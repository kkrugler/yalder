package org.krugler.yalder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.flink.api.common.functions.GroupReduceFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.operators.Order;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.io.CsvReader;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.core.fs.FileSystem.WriteMode;
import org.apache.flink.core.fs.Path;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuildModelsWorkflow {
	private static final Logger LOGGER = LoggerFactory.getLogger(BuildModelsWorkflow.class);
	
	private static final int NORMALIZED_COUNT = BaseLanguageModel.NORMALIZED_COUNT;
	
    public static List<Tuple2<String, Integer>> getCounts(ExecutionEnvironment env, BuildModelsOptions options) throws Exception {
        CsvReader reader = env.readCsvFile(options.getWorkingSubdir(WorkingDir.LANG_COUNT_DIRNAME));
        reader.setCharset("UTF-8");
    	return reader.types(String.class, Integer.class).collect();
    }
    
    public static void createWorkflow(ExecutionEnvironment env, BuildModelsOptions options, List<Tuple2<String, Integer>> langCountData) throws Exception {
    	// LLR calculation
    	// k(AB) is count of ngram A in language B
    	// k(A~B) is count of ngram A in languages other than B
    	// k(~AB) is count of ngrams other than A in language B
    	// k(~A~B) is count of ngrams other than A, and ngrams in languages other than B

    	// So we need the following...
    	// Total count of ngrams
    	// Per-language count of ngram A
    	// Total count of ngram A
    	// Per-language count of all ngrams
    	
    	// language, ngram, count Tuple3
    	// "eng", "the", 12403 => k(A|B)
    	// "", "the", 34503 => k(A|all) = total count of "the" in all languages
    	// "eng", "", 32897 => k(all|B) = total count of all ngrams in "eng"
    	// "", "", 57695 => k(all|all) = total count of all ngrams
    	
    	// k(eng|the) = 12403
    	// k(~eng|the) = k(all|the) - k(eng|the)
    	// k(eng|~the) = k(eng|all) - k(eng|the)
    	// k(~eng|~the) = k(all|all) - k(eng|the)
    	
    	// We read in the lang|all results, build a map, and also calc the all|all (total ngram) count,
    	// which we pass to the constructor of the operator that calculates the LLR score for each ngram|lang pair.
    	// So this gives us total count of all ngrams and per-language count of all ngrams.
    	// The per-language count for a specific ngram is just the specific <lang, ngram, count> tuple.
    	// So what's left is the per-ngram count (across all languages), which we calc in a workflow,
    	// emit as <"", ngram, count>, and then union with the explicit <lang, ngram, count> tuples.
    	
    	// Then for each lang, we sort ngrams by LLR (high to low), and output
    	// lang|ngram|normalized count pairs until we get to NGRAM_COVERAGE_PERCENT of all ngrams.
    	
    	int totalCount = 0;
    	Map<String, Integer> langCounts = new HashMap<>(langCountData.size());
    	for (Tuple2<String, Integer> lc : langCountData) {
    		langCounts.put(lc.f0, lc.f1);
    		totalCount += lc.f1;
    	}
    	
    	CsvReader reader = env.readCsvFile(options.getWorkingSubdir(WorkingDir.LANG_NGRAM_COUNT_DIRNAME));
    	reader.setCharset("UTF-8");
    	DataSet<Tuple3<String, String, Integer>> langNgramCounts = reader
    		.types(String.class, String.class, Integer.class);
    	
    	// We need to calculate the count (across all languages) for each ngram.
    	DataSet<Tuple3<String, String, Integer>> allNgramCounts = langNgramCounts
    			.groupBy(1)
    			.sum(2)
    			.map(new MakeAllCountriesTuple());
    			
    	// Now merge in the all|ngram|count data, group by ngram, sort by language (so that the
    	// special all count (with language set to "") comes first, then calc the LLR score.
    	langNgramCounts.union(allNgramCounts)
    		.groupBy(1)
    		.sortGroup(0, Order.ASCENDING)
    		.reduceGroup(new CalcLLRScores(langCounts, totalCount))
    		
    		.groupBy(0)
    		.sortGroup(3, Order.DESCENDING)
    		.reduceGroup(new PickBestNgrams(langCounts, options.getTargetNGramPercentage()))
    		
    		// group by language, sort by ngram (so special "" ngram for all comes first), then
    		// write to separate files.
    		
    		.map(t -> new TextModelRecord(t.f0, t.f1, t.f2))
    		.groupBy(r -> r.getLang())
            .sortGroup(r -> r.getNgram(), Order.ASCENDING)
            .first(Integer.MAX_VALUE)
            
            .output(new BucketingOutputFormat<>(
                     new TextModelOutputFormatFactory(new Path(options.getWorkingSubdir(WorkingDir.MODELS_DIRNAME))),
                     r -> r.getLang()))
            .name("language models")
            .setParallelism(1);
    }
    
    @SuppressWarnings("serial")
	protected static class MakeAllCountriesTuple implements MapFunction<Tuple3<String, String, Integer>, Tuple3<String, String, Integer>> {

		@Override
		public Tuple3<String, String, Integer> map(Tuple3<String, String, Integer> in) throws Exception {
			return new Tuple3<>("", in.f1, in.f2);
		}
    }
    
    @SuppressWarnings("serial")
	protected static class CalcLLRScores implements GroupReduceFunction<Tuple3<String, String, Integer>, Tuple4<String, String, Integer, Float>> {

    	private Map<String, Integer> _langCounts;
    	private int _totalCount;
    	
    	public CalcLLRScores(Map<String, Integer> langCounts, int totalCount) {
    		_langCounts = langCounts;
    		_totalCount = totalCount;
    	}
    	
		/**
		 * We'll get called with all languages for a given (grouped by) ngram. The first entry
		 * will be the special all|ngram|count tuple.
		 * 
		 * @param iter
		 * @param out
		 * @throws Exception
		 */
		@Override
		public void reduce(Iterable<Tuple3<String, String, Integer>> iter,
				Collector<Tuple4<String, String, Integer, Float>> out) throws Exception {
			
			int allLangCountForNgram = -1;
			for (Tuple3<String, String, Integer> in : iter) {
			    if (allLangCountForNgram == -1) {
			        allLangCountForNgram = in.f2;
			        if (!in.f0.isEmpty()) {
			            throw new RuntimeException("Error - first entry should be \"all|ngram|count\", got: " + in);
			        }

			        continue;
			    }

			    String lang = in.f0;
			    if (lang.isEmpty()) {
			        throw new RuntimeException("Error - non-initial entry should be \"lang|ngram|count\", got: " + in);
			    }

			    // k11 = k(AB) = count of ngram A in language B
			    // = value in tuple that we've calculated in workflow.
			    long k11 = in.f2;

			    // k12 = k(~AB) = count of ngrams other than A in language B
			    // = count of all ngrams in language B - count of ngram A in language B
			    long k12 = _langCounts.get(lang) - in.f2;

			    // k21 = k(A~B) = count of ngram A in languages other than B
			    // = count of ngram A in all languages - count of ngram A in language B
			    long k21 = allLangCountForNgram - in.f2;

			    // k22 = k(~A~B) = count of ngrams other than ngram A, for languages other than B
			    // = total ngrams - language B ngrams - (ngram A in languages other than B, aka k21)
			    long k22 = _totalCount - _langCounts.get(lang) - k21;

			    float llr = (float)LogLikelihood.logLikelihoodRatio(k11, k12, k21, k22);
			    out.collect(new Tuple4<>(lang, in.f1, in.f2, llr));
			}
		}
    }
    
    @SuppressWarnings("serial")
	protected static class PickBestNgrams implements GroupReduceFunction<Tuple4<String, String, Integer, Float>, Tuple3<String, String, Integer>> {

    	private Map<String, Integer> _langCounts;
    	private float _ngramCoveragePercentage;
    	
    	public PickBestNgrams(Map<String, Integer> langCounts, float ngramCoveragePercentage) {
    		_langCounts = langCounts;
    		
    		_ngramCoveragePercentage = ngramCoveragePercentage;
    	}
    	
		/**
		 * We'll get called with all ngrams for a given (grouped by) language, sorted by LLR score. Collect
		 * tuples until we've got 95% of all ngrams covered.
		 * 
		 * @param iter
		 * @param out
		 * @throws Exception
		 */
		@Override
		public void reduce(Iterable<Tuple4<String, String, Integer, Float>> iter,
				Collector<Tuple3<String, String, Integer>> out) throws Exception {
			
		    final int targetNGramCount = Math.round(NORMALIZED_COUNT * _ngramCoveragePercentage);
		    
			int langTotalCount = -1;
			int curCount = 0;
			int numNgrams = 0;
			String lang = null;
			float sumOfSquares = 0.0f;
			float sumOfCounts = 0.0f;
			
			for (Tuple4<String, String, Integer, Float> in : iter) {
				// If it's the first tuple, get the language and total ngram count
				// for that language.
				if (langTotalCount == -1) {
					lang = in.f0;
					langTotalCount = _langCounts.get(lang);
                    LOGGER.info("{}: \"{}\" had the max LLR score of {}", lang, in.f1, in.f3);
				}
				
				if (curCount < targetNGramCount) {
					// Calculate the normalized count, which pretends like we have
					// 10,000 total ngrams. But it's always at least 1.
					int normalizedCount = Math.max(1, Math.round(((float)NORMALIZED_COUNT * in.f2)/langTotalCount));
					out.collect(new Tuple3<>(lang, in.f1, normalizedCount));
					numNgrams++;

					// We use the actual normalized count to determine how many ngram counts
					// to add, as we try to get to our target count.
					curCount += normalizedCount;
					if (curCount >= targetNGramCount) {
						LOGGER.info("{}: {} ngrams", lang, numNgrams);
					}
				} else {
					// Keep track of sum(count^2/total), sum(count), to
					// come up with a good alpha value.
					sumOfSquares += (in.f2 * in.f2) / (float)langTotalCount;
					sumOfCounts += in.f2;
				}
			}
			
			// Now generate one more result, which is our best "alpha" value for any unknown ngrams.
			float alpha = langTotalCount * sumOfSquares / sumOfCounts;
			int normalizedAlphaCount = Math.max(1,  Math.round((float)NORMALIZED_COUNT * alpha)/langTotalCount);
			out.collect(new Tuple3<>(lang, "", normalizedAlphaCount));
		}
    }
    
}
