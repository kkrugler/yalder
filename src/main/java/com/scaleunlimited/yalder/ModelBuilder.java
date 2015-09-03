package com.scaleunlimited.yalder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

public class ModelBuilder {
    private static final Logger LOGGER = Logger.getLogger(ModelBuilder.class);

    public static final int MAX_NGRAM_LENGTH = 4;
    
    private static final double MIN_PROBABILITY = 0.2;

    private static final double PROBABILITY_SCORE_POWER = 1.0;

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
        
        NGramTokenizer tokenizer = new NGramTokenizer(text, 1, MAX_NGRAM_LENGTH);
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
    
    public Collection<LanguageModel> makeModels(int ngramsForAnyLanguage) {
        Map<String, Integer> ngramsPerLanguage = new HashMap<String, Integer>();
        for (String language : _ngramCounts.keySet()) {
            ngramsPerLanguage.put(language, ngramsForAnyLanguage);
        }
        
        return makeModels(ngramsPerLanguage);
    }
    
    public Collection<LanguageModel> makeModels(Map<String, Integer> ngramsPerLanguage) {

        for (String language : _ngramCounts.keySet()) {
            if (ngramsPerLanguage.get(language) == null) {
                throw new IllegalArgumentException("No ngram count for language " + language);
            }
        }
        // First come up with combined counts of ngrams across all languages.
        // We also want to normalize counts, so that the amount of training data
        // per language isn't a factor.
        Map<CharSequence, NGramStats> globalNGramCounts = new HashMap<CharSequence, NGramStats>();

        Map<String, Integer> perLanguageNGramCounts = new HashMap<String, Integer>();
        
        int maxLanguageNGrams = 0;
        for (String language : _ngramCounts.keySet()) {
            int languageNGrams = 0;
            
            Map<CharSequence, NGramStats> ngramCounts = _ngramCounts.get(language);
            for (CharSequence ngram : ngramCounts.keySet()) {
                int count = ngramCounts.get(ngram).getNGramCount();
                languageNGrams += count;
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

        // For each ngram, record a score for every language where it exists.
        Map<String, List<NGramScore>> langNGramByScore = new HashMap<String, List<NGramScore>>();
        // TODO make the map elements be a Map<CharSequence, Integer> that tells us where in
        // the langNGramsByScore list this ngram is located...which means we can't remove it from
        // the other language list, but we can flag it somehow has being used already (e.g. score
        // set to 0, so then don't use).
        Map<String, Set<CharSequence>> langNGramByNGram = new HashMap<String, Set<CharSequence>>();
        
        // Our end results.
        Map<String, List<NGramScore>> languageResults = new HashMap<String, List<NGramScore>>();
        
        for (String language : _ngramCounts.keySet()) {
            langNGramByScore.put(language, new ArrayList<NGramScore>());
            langNGramByNGram.put(language, new HashSet<CharSequence>());
            languageResults.put(language, new ArrayList<NGramScore>());
        }
        
        for (CharSequence ngram : globalNGramCounts.keySet()) {
            double totalCount = (double)globalNGramCounts.get(ngram).getNGramCount();

            for (String language : _ngramCounts.keySet()) {
                NGramStats langStatsForThisNGram = _ngramCounts.get(language).get(ngram);
            
                if (langStatsForThisNGram != null) {
                    int count = langStatsForThisNGram.getNGramCount();
                    
                    double probability = count / totalCount;
                    
                    // if the probability is too low, ignore it as we don't ever want
                    // to use it.
                    if (probability < MIN_PROBABILITY) {
                        continue;
                    }
                    
                    // When calculating the score, the probability is much more important
                    // than the count...so raise it to a power, so common letters (like ' ')
                    // with lower probabilities get a lower score.
                    double score = Math.pow(probability, PROBABILITY_SCORE_POWER) * count;
                    
                    NGramScore lng = new NGramScore(ngram, score, probability);
                    
                    langNGramByScore.get(language).add(lng);
                    langNGramByNGram.get(language).add(ngram);
                }
            }
        }
        
        // For each language, sort the ngrams by score.
        for (String language : langNGramByScore.keySet()) {
            Collections.sort(langNGramByScore.get(language));
        }
        
        boolean done = false;
        
        while (!done) {
            // Round-robin style, find the best ngram (by score) for each language, and add it to
            // that language's list. Also add that ngram from any other language to those language's
            // lists, and remove that ngram from both lists. Keep going until every language has
            // at least our target number of ngrams.
            
            done = true;
            for (String language : langNGramByScore.keySet()) {
                List<NGramScore> ngrams = langNGramByScore.get(language);
                if (ngrams.isEmpty()) {
                    continue;
                }
                
                NGramScore bestNGram = ngrams.get(0);
                languageResults.get(language).add(bestNGram);
                done = done && languageResults.get(language).size() >= ngramsPerLanguage.get(language);
                
                ngrams.remove(0);
                
                CharSequence ngram = bestNGram.getNGram();
                langNGramByNGram.get(language).remove(ngram);

                // Find and remove from other lists & sets.
                for (String otherLanguage : langNGramByScore.keySet()) {
                    if (otherLanguage.equals(language)) {
                        continue;
                    }
                    
                    if (!langNGramByNGram.get(otherLanguage).contains(ngram)) {
                        continue;
                    }
                    
                    langNGramByNGram.get(otherLanguage).remove(ngram);

                    // Now we have to scan the other language's list until we find
                    // the ngram, and then remove it.
                    List<NGramScore> otherLangNGrams = langNGramByScore.get(otherLanguage);
                    int index = otherLangNGrams.indexOf(bestNGram);
                    languageResults.get(otherLanguage).add(otherLangNGrams.remove(index));
                }
            }
        }
        
        List<LanguageModel> models = new ArrayList<LanguageModel>();
        for (String language : languageResults.keySet()) {
            List<NGramScore> langResults = languageResults.get(language);
            Collections.sort(langResults);
            
            Map<CharSequence, Integer> normalizedCounts = new HashMap<CharSequence, Integer>();
            System.out.println(String.format("Language = '%s'", language));
            for (int i = 0; i < Math.min(ngramsPerLanguage.get(language), langResults.size()); i++) {
                NGramScore result = langResults.get(i);
                System.out.println(String.format("\t'%s': %f (prob = %f)", result.getNGram(), result.getScore(), result.getProbability()));
                
                normalizedCounts.put(result.getNGram(), (int)Math.round(result.getProbability() * LanguageModel.NORMALIZED_COUNT));
            }
            
            LanguageModel model = new LanguageModel(language, normalizedCounts);
            models.add(model);
        }

        return models;
    }
    
    
    private static class NGramScore implements Comparable<NGramScore> {
        private CharSequence _ngram;
        private double _score;
        private double _probability;
        
        public NGramScore(CharSequence ngram, double score, double probability) {
            _ngram = ngram;
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
            
            return compareCharSequences(_ngram, other._ngram);
        }

        private boolean compareCharSequences(CharSequence ngram1, CharSequence ngram2) {
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
    private static class LangNGramScore implements Comparable<LangNGramScore> {
        private String _language;
        private CharSequence _ngram;
        private double _score;
        
        public LangNGramScore(String language, CharSequence ngram, double score) {
            _language = language;
            _ngram = ngram;
            _score = score;
        }

        public CharSequence getNGram() {
            return _ngram;
        }

        public double getScore() {
            return _score;
        }
        
        public String getLanguage() {
            return _language;
        }
        
        @Override
        public int compareTo(LangNGramScore o) {
            int result = _language.compareTo(o._language);
            if (result != 0) {
                return result;
            }
                            
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
