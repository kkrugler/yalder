package com.scaleunlimited.yald;

public class DetectionResult implements Comparable<DetectionResult> {

    private String _language;
    private double _score;
    private double _confidence;
    
    public DetectionResult(String language, double score) {
        _language = language;
        _score = score;
        _confidence = 0.0;
    }

    public String getLanguage() {
        return _language;
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


    
}
