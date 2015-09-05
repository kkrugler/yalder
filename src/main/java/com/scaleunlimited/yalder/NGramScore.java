package com.scaleunlimited.yalder;

public class NGramScore implements Comparable<NGramScore> {
    private CharSequence _ngram;
    
    private int _count;
    private double _score;
    private double _probability;
    
    public NGramScore(CharSequence ngram, int count, double score, double probability) {
        _ngram = ngram;
        _count = count;
        _score = score;
        _probability = probability;
    }

    public CharSequence getNGram() {
        return _ngram;
    }

    public double getScore() {
        return _score;
    }
    
    public double getProbability() {
        return _probability;
    }
    
    public int getCount() {
        return _count;
    }
    
    public void setCount(int count) {
        _count = count;
    }
    
    public void setScore(double score) {
        _score = score;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((_ngram == null) ? 0 : hashCode(_ngram));
        return result;
    }

    private int hashCode(CharSequence ngram) {
        int h = 0;
        
        for (int i = 0; i < ngram.length(); i++) {
            h = 31 * h + ngram.charAt(i);
        }

        return h;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        NGramScore other = (NGramScore) obj;
        if (_ngram == null) {
            if (other._ngram != null)
                return false;
        }
        
        return equals(_ngram, other._ngram);
    }

    
    private boolean equals(CharSequence ngram1, CharSequence ngram2) {
        int len1 = ngram1.length();
        int len2 = ngram2.length();
        if (len1 != len2) {
            return false;
        }
        
        for (int i = 0; i < len1; i++) {
            if (ngram1.charAt(i) != ngram2.charAt(i)) {
                return false;
            }
        }
        
        return true;
    }

    @Override
    public int compareTo(NGramScore o) {
        if (_score > o._score) {
            return -1;
        } else if (_score < o._score) {
            return 1;
        } else {
            return 0;
        }
    }
    
    
}

