package com.scaleunlimited.yalder;

public class DetectionResult implements Comparable<DetectionResult> {

    private LanguageLocale _language;
    private double _score;
    private double _confidence;
    private String _details;
    
    public DetectionResult(LanguageLocale language, double score) {
        _language = language;
        _score = score;
        _confidence = 0.0;
        _details = "";
    }

    public LanguageLocale getLanguage() {
        return _language;
    }

    public void setLanguage(LanguageLocale language) {
        _language = language;
    }
    
    public double getScore() {
        return _score;
    }

    public void setScore(double score) {
        _score = score;
    }

    public double getConfidence() {
        return _confidence;
    }

    public void setConfidence(double confidence) {
        _confidence = confidence;
    }

    public void setDetails(String details) {
        _details = details;
    }
    
    public String getDetails() {
        return _details;
    }
    
    /* Do reverse sorting.
     * (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(DetectionResult o) {
        if (_score > o._score) {
            return -1;
        } else if (_score < o._score) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public String toString() {
        return String.format("Language '%s' with score %f and confidence %f", _language, _score, _confidence);
    }
    
}
