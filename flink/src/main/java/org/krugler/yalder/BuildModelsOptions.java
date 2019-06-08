package org.krugler.yalder;

import org.kohsuke.args4j.Option;

@SuppressWarnings("serial")
public class BuildModelsOptions extends BaseFlinkOptions {

    private float _targetNGramPercentage = 0.95f;
    
    @Option(name = "-ngrampercent", usage = "Percentage of all ngrams in language that need to be model (e.g. 0.95)", required = false)
    public void setTargetNGramPercentage(float targetNGramPercentage) {
        _targetNGramPercentage = targetNGramPercentage;
    }
    
    public float getTargetNGramPercentage() {
        return _targetNGramPercentage;
    }
}
