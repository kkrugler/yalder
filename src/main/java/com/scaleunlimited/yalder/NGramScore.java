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
        
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be > 0");
        }
        
        if ((score <= 0.0) || Double.isNaN(score) || Double.isInfinite(score)) {
            throw new IllegalArgumentException("Score must be > 0 and < infinity");
        }
        
        if ((probability <= 0.0) || Double.isNaN(probability) || Double.isInfinite(probability)) {
            throw new IllegalArgumentException("Probability must be > 0 and < infinity");
        }
        
        
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
        long temp;
        temp = Double.doubleToLongBits(_score);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        return result;
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
        if (Double.doubleToLongBits(_score) != Double.doubleToLongBits(other._score))
            return false;
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

