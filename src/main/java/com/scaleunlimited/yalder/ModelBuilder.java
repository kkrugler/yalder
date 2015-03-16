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

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.log4j.Logger;
import org.apache.mahout.math.RandomAccessSparseVector;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.stats.LogLikelihood;

public class ModelBuilder {
    private static final Logger LOGGER = Logger.getLogger(ModelBuilder.class);

    public static final int MIN_NGRAM_LENGTH = 4;
    public static final int MAX_NGRAM_LENGTH = 4;

    private static final int TARGET_NGRAMS = 2000;          // 1000
    private static final double TARGET_SCORE = 50.0;        // 40.0
    private static final double MAX_OTHERDF = 0.01;
    
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
        
        Map<String, NGramVector> langVectors = new HashMap<String, NGramVector>();

        int totalVectorTerms = 0;
        
        for (String language : languages) {
            double docsForThisLanguage = _docsPerLanguage.get(language);
            Map<CharSequence, NGramStats> languageStats = _langStats.get(language);
            
            List<NGramScore> bestNGrams = new ArrayList<ModelBuilder.NGramScore>(languageStats.size());
            for (CharSequence ngram : languageStats.keySet()) {
                NGramStats stats = languageStats.get(ngram);
                double df = stats.getDocCount() / docsForThisLanguage;
                
                double totalDocsForOtherLanguages = 0.0;
                double docsForOtherLanguages = 0.0;
                for (String otherLanguage : languages) {
                    if (otherLanguage.equals(language)) {
                        continue;
                    }

                    totalDocsForOtherLanguages += _docsPerLanguage.get(otherLanguage);

                    NGramStats otherStats = _langStats.get(otherLanguage).get(ngram);
                    if (otherStats != null) {
                        docsForOtherLanguages += otherStats.getDocCount();
                    }
                }
                
                // The otherDF is the document frequency for this ngram for all of the other languages.
                // We'd love it if this value was 0, as that means it never occurs in any other language.
                double otherDF = docsForOtherLanguages / totalDocsForOtherLanguages;
                
                // Our score is essentially the probability that this ngram will be in a document for our
                // target language, and not in a document for any other language.
                double ngramScore = df * (1.0 - otherDF);
                
                // TODO remove me
                // Try out limiting otherDF to max value.
                if (otherDF > MAX_OTHERDF) {
                    ngramScore = 0.0;
                }
                
                bestNGrams.add(new NGramScore(ngram, ngramScore, otherDF));
            }
            
            // Pick the best ngrams
            Collections.sort(bestNGrams);
            int maxIndex = Math.min(TARGET_NGRAMS, bestNGrams.size()) - 1;
            
            NGramVector vector = new NGramVector(TARGET_NGRAMS);
            double totalLanguageScore = 0.0;
            for (int i = 0; (i <= maxIndex) && (totalLanguageScore < TARGET_SCORE); i++) {
                double score = bestNGrams.get(i).getScore();
                double otherDF = bestNGrams.get(i).getOtherDF();
                // System.out.println(String.format("NGram '%s' has score %f and other DF %f", bestNGrams.get(i).getNGram(), score, otherDF));
                
                // The weight is the probability that this term isn't for another language, which
                // means 1.0 - otherDF. But we need to quantize this to 1...7, whereas it's going
                // to be in the range of 1.0 - [0.0 ... MAX_OTHERDF]. We want 1.0 to be 7, and 
                // 1.0 - MAX_OTHERDF to be 1.
                double percent = 1.0 - (otherDF / MAX_OTHERDF);
                
                int maxWeight = 7;
                int minWeight = 1;
                double range = maxWeight - minWeight;
                
                // int weight = (int)Math.round(minWeight + (range * percent));
                int weight = 1;
                
                vector.set(bestNGrams.get(i).getNGram(), weight);
                totalLanguageScore += bestNGrams.get(i).getScore();
            }
            
            totalVectorTerms += vector.size();
            System.out.println(String.format("Language '%s' size = %d", language, vector.size()));
            langVectors.put(language, vector);
        }
        
        System.out.println(String.format("Total vector terms = %d", totalVectorTerms));
        
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
        private double _otherDF;
        
        public NGramScore(CharSequence ngram, double score, double otherDF) {
            _ngram = ngram;
            _score = score;
            _otherDF = otherDF;
        }

        public CharSequence getNGram() {
            return _ngram;
        }

        public double getScore() {
            return _score;
        }
        
        public double getOtherDF() {
            return _otherDF;
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
