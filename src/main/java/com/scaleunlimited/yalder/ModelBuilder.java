package com.scaleunlimited.yalder;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.RuntimeErrorException;

import org.apache.log4j.Logger;
import org.apache.mahout.math.RandomAccessSparseVector;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.stats.LogLikelihood;

public class ModelBuilder {
    private static final Logger LOGGER = Logger.getLogger(ModelBuilder.class);

    public static final int MIN_NGRAM_LENGTH = 4;
    public static final int MAX_NGRAM_LENGTH = 4;

    private static final int TARGET_NGRAMS = 500;
    
    // Map from language to ngram to stats for that ngram
    private Map<String, Map<CharSequence, NGramStats>> _langStats;
    
    // Map from language to number of documents
    private IntCounter _docsPerLanguage;
    
    public ModelBuilder() {
        _langStats = new HashMap<String, Map<CharSequence, NGramStats>>();
        _docsPerLanguage = new IntCounter();

    }
    
    public void addTrainingDoc(String language, CharSequence text) {
        Map<CharSequence, NGramStats> docStats = CharUtils.calcNGramStats(text, MIN_NGRAM_LENGTH, MAX_NGRAM_LENGTH);
        mergeStats(language, docStats);

        _docsPerLanguage.increment(language);
    }
    
    public Collection<LanguageModel> makeModels() {
        try {
            return makeModels(null);
        } catch (IOException e) {
            throw new RuntimeException("Impossible exception", e);
        }
    }
    
    public Collection<LanguageModel> makeModels(PrintWriter pw) throws IOException {
        
        // First build the language vectors for all languages we've got.
        Map<String, NGramVector> langVectors = makeVectors(_langStats.keySet(), pw);
        
        List<LanguageModel> models = new ArrayList<LanguageModel>(langVectors.size());
        for (String language : langVectors.keySet()) {
            models.add(new LanguageModel(language, null, langVectors.get(language)));
        }
        
        return models;
    }
    
    private Map<String, NGramVector> makeVectors(Set<String> languages, PrintWriter pw) throws IOException {
        // For each language, we want to pick the N best ngrams. The best ngram is the one with the highest
        // score, where the score is composed of the ngram's doc frequency for this language (higher is better)
        // the the doc frequencies for all other languages (lower is better).
        
        // We do this one ngram at a time, because as we add ngrams that are good for disambiguating this language
        // from language X, we want to emphasize ngrams that are good for disambiguating against the other languages.
        // So we keep a running tally of per-language summed scores, and do pro-rata weighting of the remaining
        // ngrams.

        Map<String, NGramVector> langVectors = new HashMap<String, NGramVector>();

        for (String language : languages) {
            double docsForThisLanguage = _docsPerLanguage.get(language);
            Map<CharSequence, NGramStats> languageStats = _langStats.get(language);
            
            // Keep track of per-language score, and total score
            DoubleCounter scoresForOtherLanguages = new DoubleCounter();
            double totalScoresForOtherLanugages = 0.0;
            
            List<NGramScore> bestNGrams = new ArrayList<ModelBuilder.NGramScore>(languageStats.size());
            while ((bestNGrams.size() < TARGET_NGRAMS) && (languageStats.size() > 0)) {
                // Find the next best n-gram
                
                CharSequence bestNGram = null;
                double bestScore = Double.NEGATIVE_INFINITY;
                
                for (CharSequence ngram : languageStats.keySet()) {
                    NGramStats stats = languageStats.get(ngram);
                    double df = stats.getDocCount() / docsForThisLanguage;
                    
                    double totalOtherNDF = 1.0;
                    for (String otherLanguage : languages) {
                        if (otherLanguage.equals(language)) {
                            continue;
                        }
                        
                        // Weight this language based on its pro-rata share of the total
                        // scores.
                        double weight = totalScoresForOtherLanugages == 0.0 ? 1.0 : 1.0 - (scoresForOtherLanguages.get(otherLanguage) / totalScoresForOtherLanugages);
                        
                        double otherDF = 0.0;
                        double docsForOtherLanguage = _docsPerLanguage.get(otherLanguage);
                        NGramStats otherStats = _langStats.get(otherLanguage).get(ngram);
                        if (otherStats != null) {
                            otherDF = otherStats.getDocCount() / docsForOtherLanguage;
                        }
                        
                        totalOtherNDF *= (weight * (1.0 - otherDF));
                    }
                    
                    double ngramScore = df * totalOtherNDF;
                    if (ngramScore > bestScore) {
                        bestScore = ngramScore;
                        bestNGram = ngram;
                    }
                }
                
                // Move the best n-gram from our current language stats (so we don't pick it
                // again) into our list.
                bestNGrams.add(new NGramScore(bestNGram, bestScore));
                languageStats.remove(bestNGram);
                
                // Update the per-language scores for this ngram (and the total overall) so we
                // know how to properly weight the negative doc frequencies for other candidate
                // ngrams.
                for (String otherLanguage : languages) {
                    if (otherLanguage.equals(language)) {
                        continue;
                    }
                    
                    double otherDF = 0.0;
                    double docsForOtherLanguage = _docsPerLanguage.get(otherLanguage);
                    NGramStats otherStats = _langStats.get(otherLanguage).get(bestNGram);
                    if (otherStats != null) {
                        otherDF = otherStats.getDocCount() / docsForOtherLanguage;
                    }
                    
                    double otherNDF = (1.0 - otherDF);
                    scoresForOtherLanguages.increment(otherLanguage, otherNDF);
                    totalScoresForOtherLanugages += otherNDF;
                }

            }
            
            NGramVector vector = new NGramVector(TARGET_NGRAMS);
            for (NGramScore ngramScore : bestNGrams) {
                vector.set(BaseNGramVector.calcHash(ngramScore.getNGram()));
            }
            
            langVectors.put(language, vector);
        }
        
        return langVectors;
    }

    private void mergeStats(String language, Map<CharSequence, NGramStats> docStats) {
        Map<CharSequence, NGramStats> curLangStats = _langStats.get(language);
        if (curLangStats == null) {
            curLangStats = new HashMap<CharSequence, NGramStats>();
            _langStats.put(language, curLangStats);
        }
        
        // merge docStats into curLangStats.
        for (CharSequence ngram : docStats.keySet()) {
            NGramStats curNGramStats = curLangStats.get(ngram);
            if (curNGramStats == null) {
                curNGramStats = new NGramStats();
                curLangStats.put(ngram, curNGramStats);
            }
            
            curNGramStats.incDocCount();
            curNGramStats.incNGramCount(docStats.get(ngram).getNGramCount());
        }
    }
    
    private static class NGramScore implements Comparable<NGramScore> {
        private CharSequence _ngram;
        private double _score;
        
        public NGramScore(CharSequence ngram, double score) {
            _ngram = ngram;
            _score = score;
        }

        public CharSequence getNGram() {
            return _ngram;
        }

        public double getScore() {
            return _score;
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

}
