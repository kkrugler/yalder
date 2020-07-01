package org.krugler.yalder;

import java.io.IOException;
import java.util.Random;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.io.CsvReader;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.Path;
import org.apache.flink.util.Collector;
import org.krugler.yalder.text.TextTokenizer;

public class AnalyzeTextWorkflow {

    public static void createWorkflow(ExecutionEnvironment env, AnalyzeTextOptions options) throws IOException {

        Configuration parameters = new Configuration();
        parameters.setBoolean("recursive.file.enumeration", true);

        DataSet<Tuple2<String, String>> inputData = null;

        if (options.getWikipediaDir() != null) {
            // We have a bunch of small files, named xxx_<ISO country code>.txt.
            // For example, Biltzheim_bug.txt
            TextAndLangInputFormat inputFormat =  new TextAndLangInputFormat(new Path(options.getWikipediaDir()));
            inputData = env.createInput(inputFormat)
                // descend recursively into subdirectories
                .withParameters(parameters)
                .name("wikipedia data");
        }

        if (options.getTaggedFile() != null) {
            CsvReader reader = env.readCsvFile(options.getTaggedFile())
                            .fieldDelimiter("\t");
            reader.setCharset("UTF-8");
            DataSet<Tuple2<String, String>> taggedData = reader
                            .types(String.class, String.class)
                            .map(new ConvertLangCode())
                            .name("tagged data");

            if (inputData == null) {
                inputData = taggedData;
            } else {
                inputData = inputData.union(taggedData);
            }
        }

        if (options.getLeipzigDir() != null) {
            LeipzigCorpusInputFormat inputFormat = new LeipzigCorpusInputFormat(new Path(options.getLeipzigDir()));
            DataSet<Tuple2<String, String>> leipzigData = env.createInput(inputFormat)
                            // descend recursively into subdirectories
                            .withParameters(parameters)
                            .name("leipzig corpus data");

            if (inputData == null) {
                inputData = leipzigData;
            } else {
                inputData = inputData.union(leipzigData);
            }
        }
        
        // First we generate the lang|ngram|0 pairs, then sum.
        // We write these out, as well as the lang|all|count result
        DataSet<Tuple3<String, String, Integer>> langNgramData = inputData
                        .filter(new FilterLines(options.getSampleRate()))
                        // TODO support word edge only option, so text has to start or end with ' ', and
                        // can't contain a space in the middle. Assumes min/max length is 4. But we'd only
                        // want to do this if the 2nd and/or 3rd char was in a Unicode block that seems 
                        // like it should have a space (e.g. Han chars are OK w/o spaces).
                        .flatMap(new MakeNGrams(options.getMinNGramLength(), options.getMaxNGramLength()))
                        .groupBy(0, 1)
                        .sum(2)
                        .filter(t -> t.f2 >= options.getMinNGramCount())
                        .name("language|ngram|count data");

        langNgramData.writeAsCsv(options.getWorkingSubdir(WorkingDir.LANG_NGRAM_COUNT_DIRNAME));

        langNgramData
        .groupBy(0)
        .sum(2)
        .project(0, 2)
        .name("languagge|count data")
        .writeAsCsv(options.getWorkingSubdir(WorkingDir.LANG_COUNT_DIRNAME));
    }
    
    @SuppressWarnings("serial")
    protected static class FilterLines extends RichFilterFunction<Tuple2<String, String>> {

        private float _sampleRate;
        
        private transient Random _rand;
        
        public FilterLines(float sampleRate) {
            _sampleRate = sampleRate;
        }
        
        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            
            _rand = new Random();
        }
        
        @Override
        public boolean filter(Tuple2<String, String> in) throws Exception {
            return _rand.nextFloat() <= _sampleRate;
        }
    }
    
    @SuppressWarnings("serial")
	protected static class MakeNGrams extends RichFlatMapFunction<Tuple2<String, String>, Tuple3<String, String, Integer>> {

        private int _minNGramLength;
    	private int _maxNGramLength;
    	
    	private transient TextTokenizer _tokenizer;
    	
    	public MakeNGrams(int minNGramLength, int maxNGramLength) {
    	    _minNGramLength = minNGramLength;
    		_maxNGramLength = maxNGramLength;
    	}
    	
    	@Override
    	public void open(Configuration parameters) throws Exception {
    		super.open(parameters);
    		
    		_tokenizer = new TextTokenizer("", _maxNGramLength);
    	}
    	
		@Override
		public void flatMap(Tuple2<String, String> in, Collector<Tuple3<String, String, Integer>> out)
				throws Exception {
			String lang = in.f0;
			
			_tokenizer.reset();
			_tokenizer.addText(in.f1);
			
			while (_tokenizer.hasNext()) {
			    String ngram = _tokenizer.next();
			    if (ngram.length() >= _minNGramLength) {
			        out.collect(new Tuple3<>(lang, ngram, 1));
			    }
			}
		}
    }
    
    @SuppressWarnings("serial")
    protected static class ConvertLangCode implements MapFunction<Tuple2<String, String>, Tuple2<String, String>> {

        @Override
        public Tuple2<String, String> map(Tuple2<String, String> in) throws Exception {
            return new Tuple2<>(LanguageLocale.convertISO2LetterNameTo3Letters(in.f0), in.f1);
        }
    }
}
