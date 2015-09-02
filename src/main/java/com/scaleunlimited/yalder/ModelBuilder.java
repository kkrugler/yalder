package com.scaleunlimited.yalder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

public class ModelBuilder {
    private static final Logger LOGGER = Logger.getLogger(ModelBuilder.class);

    private Map<String, Map<CharSequence, NGramStats>> _ngramCounts;
    
    public ModelBuilder() {
        _ngramCounts = new HashMap<String, Map<CharSequence, NGramStats>>();
    }
    
    public void addTrainingDoc(String language, CharSequence text) {
        Map<CharSequence, NGramStats> ngramCounts = _ngramCounts.get(language);
        if (ngramCounts == null) {
            ngramCounts = new HashMap<CharSequence, NGramStats>();
            _ngramCounts.put(language, ngramCounts);
        }
        
        NGramTokenizer tokenizer = new NGramTokenizer(text, 1, 4);
        while (tokenizer.hasNext()) {
            CharSequence token = tokenizer.next();
            NGramStats curStats = ngramCounts.get(token);
            if (curStats == null) {
                curStats = new NGramStats();
                ngramCounts.put(token, curStats);
            }
            
            curStats.incNGramCount();
        }
    }
    
    public Collection<LanguageModel> makeModels() {

        // First come up with combined counts of ngrams across all languages.
        // We also want to normalize counts, so that the amount of training data
        // per language isn't a factor.
        Map<CharSequence, NGramStats> globalNGramCounts = new HashMap<CharSequence, NGramStats>();

        Map<String, Integer> perLanguageNGramCounts = new HashMap<String, Integer>();
        
        int totalNGrams = 0;
        int maxLanguageNGrams = 0;
        for (String language : _ngramCounts.keySet()) {
            int languageNGrams = 0;
            
            Map<CharSequence, NGramStats> ngramCounts = _ngramCounts.get(language);
            for (CharSequence ngram : ngramCounts.keySet()) {
                int count = ngramCounts.get(ngram).getNGramCount();
                languageNGrams += count;
                totalNGrams += 1;
            }
            
            perLanguageNGramCounts.put(language,  languageNGrams);
            if (languageNGrams > maxLanguageNGrams) {
                maxLanguageNGrams = languageNGrams;
            }
        }

        
        for (String language : _ngramCounts.keySet()) {
            double datasizeNormalization = (double)maxLanguageNGrams / (double)perLanguageNGramCounts.get(language);
            Map<CharSequence, NGramStats> ngramCounts = _ngramCounts.get(language);
            for (CharSequence ngram : ngramCounts.keySet()) {
                NGramStats ngramStats = ngramCounts.get(ngram);
                int count = ngramStats.getNGramCount();
                int adjustedCount = (int)Math.round(count * datasizeNormalization);
                ngramStats.setNGramCount(adjustedCount);
                
                NGramStats globalStats = globalNGramCounts.get(ngram);
                if (globalStats == null) {
                    globalStats = new NGramStats();
                    globalNGramCounts.put(ngram, globalStats);
                }

                globalStats.incNGramCount(adjustedCount);
            }
        }

        // Now we can calculate, for each ngram, the probability that it's for language X. We want to
        // calculate a score as well for each language, which reflects the difference in probability
        // between it and the next most common language, as well as how often it occurs. We'll only
        // create one entry, for the most likely language, with the ratio of probabilities between it
        // and the next most likely language * how often it occurs for this language.

        Map<String, List<NGramScore>> ngramResults = new HashMap<String, List<NGramScore>>();
        
        for (CharSequence ngram : globalNGramCounts.keySet()) {
            double totalCount = (double)globalNGramCounts.get(ngram).getNGramCount();

            double bestProb = 0.0;
            double secondBestProb = 0.0;
            double bestFrequency = 0.0;
            String bestLanguage = null;
            
            int bestCount = 0;
            
            for (String language : _ngramCounts.keySet()) {
                NGramStats langStatsForThisNGram = _ngramCounts.get(language).get(ngram);
            
                if (langStatsForThisNGram != null) {
                    int count = langStatsForThisNGram.getNGramCount();
                    
                    // TODO need to normalize count, so that a language with more training
                    // data (and thus more ngrams) doesn't always win the comparison. So
                    // calc a weighting factor, which would be something like how much
                    // to scale up each count if we wanted to have 1B ngrams for each language.
                    double probability = count / totalCount;
                    if (probability > bestProb) {
                        bestLanguage = language;
                        secondBestProb = bestProb;
                        bestProb = probability;
                        bestCount = count;
                    }
                }
            }
            
            // Calc a score for 0.0 (secondBest is same as best) to 1.0 (secondBestProb is 0.0)
            double score = (bestProb - secondBestProb) / bestProb;
                
            // Increase score by number of occurrences.
            // TODO this likely isn't what we really want, as score of 1.0 with X occurrences would
            // wind up the same as score of 0.5 with X/2 occurrences. And we'd want a frequency
            // for this ngram relative to the best language total ngram count, 
            score *= bestCount;
            
            List<NGramScore> langResults = ngramResults.get(bestLanguage);
            if (langResults == null) {
                langResults = new ArrayList(1000);
                ngramResults.put(bestLanguage, langResults);
            }
            
            NGramScore result = new NGramScore(ngram, score, bestCount/totalCount);
            langResults.add(result);
        }

        // TODO for any ngram we've got for any language, we need to add it for all other
        // languages where it has a frequence of at least x (e.g. .1, meaning 10% of all occurrences
        // are for this language).
        for (String language : ngramResults.keySet()) {
            List<NGramScore> langResults = ngramResults.get(language);
            
            // Sort the list, and pick the top N
            Collections.sort(langResults);
            System.out.println(String.format("Language = '%s'", language));
            for (int i = 0; i < Math.min(100, langResults.size()); i++) {
                NGramScore result = langResults.get(i);
                System.out.println(String.format("\t'%s': %f (frequency = %f)", result.getNGram(), result.getScore(), result.getFrequency()));
            }
        }

        List<LanguageModel> models = new ArrayList<LanguageModel>();
        return models;
    }
    
    private static class NGramScore implements Comparable<NGramScore> {
        private CharSequence _ngram;
        private double _score;
        private double _frequency;
        
        public NGramScore(CharSequence ngram, double score, double frequency) {
            _ngram = ngram;
            _score = score;
            _frequency = frequency;
        }

        public CharSequence getNGram() {
            return _ngram;
        }

        public double getScore() {
            return _score;
        }
        
        public double getFrequency() {
            return _frequency;
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
