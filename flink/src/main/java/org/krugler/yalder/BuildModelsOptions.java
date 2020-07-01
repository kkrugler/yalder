package org.krugler.yalder;

import org.kohsuke.args4j.Option;

@SuppressWarnings("serial")
public class BuildModelsOptions extends BaseFlinkOptions {

    private float _targetNGramPercentage = 0.95f;
    private float _minNGramRate = 5.0f/BaseLanguageModel.NORMALIZED_COUNT;
    
    @Option(name = "-ngrampercent", usage = "Percentage of all ngrams in language that need to be model (e.g. 0.95)", required = false)
    public void setTargetNGramPercentage(float targetNGramPercentage) {
        _targetNGramPercentage = targetNGramPercentage;
    }
    
    public float getTargetNGramPercentage() {
        return _targetNGramPercentage;
    }

    public float getMinNGramRate() {
        return _minNGramRate;
    }

    @Option(name = "-ngramrate", usage = "Min percentage that ngram has to reach to be included (e.g. 0.000005)", required = false)
    public void setMinNGramRate(float minNGramRate) {
        _minNGramRate = minNGramRate;
    }
    
}
