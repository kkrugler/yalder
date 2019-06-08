package org.krugler.yalder;

import org.kohsuke.args4j.Option;

@SuppressWarnings("serial")
public class AnalyzeTextOptions extends BaseFlinkOptions {
    
    private String _wikipediaDir;
    private String _taggedFile;
    private int _minNGramLength = 1;
    private int _maxNGramLength = 4;
    private float _sampleRate = 1.0f;
    
    public String getWikipediaDir() {
        return _wikipediaDir;
    }

    @Option(name = "-wikidir", usage = "Location of directory containing Wikipedia-based training data", required = false)
    public void setWikipediaDir(String wikipediaDir) {
    	_wikipediaDir = wikipediaDir;
    }

    public String getTaggedFile() {
        return _taggedFile;
    }

    @Option(name = "-taggedfile", usage = "Location of file containing text lines with language code<tab>text", required = false)
    public void setTaggedFile(String taggedFile) {
    	_taggedFile = taggedFile;
    }

    public int getMinNGramLength() {
        return _minNGramLength;
    }

    @Option(name = "-minngram", usage = "Min length for ngrams", required = false)
    public void setMinNGramLength(int minNGramLength) {
        _minNGramLength = minNGramLength;
    }

    public int getMaxNGramLength() {
        return _maxNGramLength;
    }

    @Option(name = "-maxngram", usage = "Max length for ngrams", required = false)
    public void setMaxNGramLength(int maxNGramLength) {
        _maxNGramLength = maxNGramLength;
    }

    public float getSampleRate() {
        return _sampleRate;
    }

    @Option(name = "-sample", usage = "Sampling rate (>0.0, <= 1.0)", required = false)
    public void setSampleRate(float sampleRate) {
        _sampleRate = sampleRate;
    }

    
}
