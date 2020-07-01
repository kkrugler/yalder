package org.krugler.yalder;

import org.kohsuke.args4j.Option;

@SuppressWarnings("serial")
public class AnalyzeLeipzigOptions extends BaseFlinkOptions {
    
    private String _leipzigDir;
    
    private float _singleLangFraction = 0.95f;
    private float _sampleRate = 1.0f;
    private int _minBlockCount = 1;

    public String getLeipzigDir() {
        return _leipzigDir;
    }

    @Option(name = "-leipzigdir", usage = "Location of directory containing Leipzig corpus training data", required = true)
    public void setLeipzigDir(String leipzigDir) {
        _leipzigDir = leipzigDir;
    }

    public float getSingleLangFraction() {
        return _singleLangFraction;
    }

    @Option(name = "-singlelangfraction", usage = "Limit for single language (>0.0, <= 1.0)", required = false)
    public void setSingleLangFraction(float singleLangFraction) {
        _singleLangFraction = singleLangFraction;
    }
    
    public float getSampleRate() {
        return _sampleRate;
    }

    @Option(name = "-sample", usage = "Sampling rate (>0.0, <= 1.0)", required = false)
    public void setSampleRate(float sampleRate) {
        _sampleRate = sampleRate;
    }

    public int getMinBlockCount() {
        return _minBlockCount;
    }

    @Option(name = "-mincount", usage = "Minimum count of chars in block to trigger collapsing", required = false)
    public void setMinBlockCount(int minBlockCount) {
        _minBlockCount = minBlockCount;
    }


}
