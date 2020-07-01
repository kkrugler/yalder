package org.krugler.yalder;

import java.io.IOException;
import java.util.function.IntConsumer;

import org.apache.flink.api.common.functions.GroupReduceFunction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.FileSystem.WriteMode;
import org.apache.flink.core.fs.Path;
import org.apache.flink.util.Collector;
import org.krugler.yalder.AnalyzeTextWorkflow.FilterLines;

/**
 * Decide which Unicode blocks are (almost) always for the same language, so that
 * we can optimize the tokenizer to collapse these into a single code point.
 *
 */
public class AnalyzeLeipzigWorkflow {

    public static void createWorkflow(ExecutionEnvironment env, AnalyzeLeipzigOptions options) throws IOException {

        Configuration parameters = new Configuration();
        parameters.setBoolean("recursive.file.enumeration", true);

        LeipzigCorpusInputFormat inputFormat = new LeipzigCorpusInputFormat(new Path(options.getLeipzigDir()));
        DataSet<Tuple2<String, String>> inputData = env.createInput(inputFormat)
                        // descend recursively into subdirectories
                        .withParameters(parameters)
                        .name("leipzig corpus data");

        // Generate the lang|block id|1 pairs, then sum.
        DataSet<Tuple3<String, String, Integer>> blockNameData = inputData
                        .filter(new FilterLines(options.getSampleRate()))
                        .flatMap(new MakeBlockNames())
                        .groupBy(0, 1)
                        .sum(2)
                        .name("language|block name|count data");

        // For a given block, see if it's predominantly a single language.
        blockNameData.groupBy(1)
            // TODO add min count, so we don't trigger a block assignment for a few random
            // chars.
            .reduceGroup(new SingleLanguageBlock(options.getSingleLangFraction(), options.getMinBlockCount()))
            .name("block name|count|language")
            .writeAsCsv(options.getWorkingFile(WorkingDir.SINGLE_LANGUAGE_BLOCKS_FILENAME), WriteMode.OVERWRITE)
            .setParallelism(1);
    }
    
    @SuppressWarnings("serial")
	protected static class MakeBlockNames extends RichFlatMapFunction<Tuple2<String, String>, Tuple3<String, String, Integer>> {

		@Override
		public void flatMap(Tuple2<String, String> in, Collector<Tuple3<String, String, Integer>> out)
				throws Exception {
		    String lang = in.f0;
			String text = in.f1;
			
			text.chars().forEach(new IntConsumer() {
                
                @Override
                public void accept(int value) {
                    Character.UnicodeBlock block = Character.UnicodeBlock.of(value);
                    if (block != null) {
                        out.collect(new Tuple3<>(lang, block.toString(), 1));
                    }
                }
            });
		}
    }
    
    @SuppressWarnings("serial")
    protected static class SingleLanguageBlock implements GroupReduceFunction<Tuple3<String, String, Integer>, Tuple3<String, Long, String>> {

        private double _singleLanguageFraction;
        private long _minBlockCount;
        
        public SingleLanguageBlock(float singleLanguageFraction, int minBlockCount) {
            _singleLanguageFraction = singleLanguageFraction;
            _minBlockCount = minBlockCount;
        }
        
        @Override
        public void reduce(Iterable<Tuple3<String, String, Integer>> iter, Collector<Tuple3<String, Long, String>> out) throws Exception {
            long totalBlockCount = 0;
            long maxLangBlockCount = 0;
            String maxLang = "";
            String blockName = null;
            
            for (Tuple3<String, String, Integer> in : iter) {
                if (blockName == null) {
                    blockName = in.f1;
                }
                
                long count = in.f2;
                totalBlockCount += count;
                
                if (count > maxLangBlockCount) {
                    maxLangBlockCount = count;
                    maxLang = in.f0;
                }
            }
            
            // See if our max language has >= target percentage.
            double singleLangPercent = (double)maxLangBlockCount/(double)totalBlockCount;
            if ((singleLangPercent >= _singleLanguageFraction) && (totalBlockCount >= _minBlockCount)) {
                out.collect(new Tuple3<>(blockName, totalBlockCount, maxLang));
            }
        }
    }
}
