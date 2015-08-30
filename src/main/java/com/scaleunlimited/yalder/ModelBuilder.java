package com.scaleunlimited.yalder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

public class ModelBuilder {
    private static final Logger LOGGER = Logger.getLogger(ModelBuilder.class);

    public static final int MIN_NGRAM_LENGTH = 4;
    public static final int MAX_NGRAM_LENGTH = 4;

    private SubModelBuilder _mainBuilder;
    private SubModelBuilder _csSkBuilder;
    private SubModelBuilder _daDeSvBuilder;
    
    private boolean _doCsSk = true;
    private boolean _doDaDeSv = true;
    
    public ModelBuilder() {
        _mainBuilder = new SubModelBuilder();
        _csSkBuilder = new SubModelBuilder();
        _daDeSvBuilder = new SubModelBuilder();
    }
    
    public ModelBuilder setCsSk(boolean doCsSk) {
        _doCsSk = doCsSk;
        return this;
    }
    
    public ModelBuilder setDaDeSv(boolean doDaDeSv) {
        _doDaDeSv = doDaDeSv;
        return this;
    }
    
    
    public void addTrainingDoc(String language, CharSequence text) {
        if (_doCsSk && (language.equals("cs") || language.equals("sk"))) {
            _csSkBuilder.addTrainingDoc(language, text);
            language = "cs+sk";
        } else if (_doDaDeSv && (language.equals("da") || language.equals("de") || language.equals("sv"))) {
            _daDeSvBuilder.addTrainingDoc(language, text);
            language = "da+de+sv";
        }

        _mainBuilder.addTrainingDoc(language, text);
    }
    
    public Collection<LanguageModel> makeModels() {
        
        // First build the language vectors for all languages we've got.
        Map<String, NGramVector> mainVectors = _mainBuilder.makeVectors();
        
        // Now build the pair-wise language vectors for specific cases.

        List<LanguageModel> models = new ArrayList<LanguageModel>();
        for (String language : mainVectors.keySet()) {
            models.add(new LanguageModel(language, null, mainVectors.get(language)));
        }
        
        if (_doCsSk) {
            Map<String, NGramVector> csSkVectors = _csSkBuilder.makeVectors();
            for (String language : csSkVectors.keySet()) {
                models.add(new LanguageModel(language, "cs+sk", csSkVectors.get(language)));
            }
        }
        
        if (_doDaDeSv) {
            Map<String, NGramVector> daDeSvVectors = _daDeSvBuilder.makeVectors();
            for (String language : daDeSvVectors.keySet()) {
                models.add(new LanguageModel(language, "da+de+sv", daDeSvVectors.get(language)));
            }
        }
        
        return models;
    }
    
    private static class SubModelBuilder {
        private static final int TARGET_NGRAMS = 3000;          // 1000
        private static final double TARGET_SCORE = 50.0;        // 40.0

        private static final double MIN_NGRAMFREQUENCY = 0.20;  // 0.20
        
        // Map from language to ngram to stats for that ngram
        private Map<String, Map<CharSequence, NGramStats>> _langStats;
        
        // Map from language to number of documents
        private IntCounter _docsPerLanguage;
        
        public SubModelBuilder() {
            _langStats = new HashMap<String, Map<CharSequence, NGramStats>>();
            _docsPerLanguage = new IntCounter();
        }
        
        public void addTrainingDoc(String language, CharSequence text) {
            Map<CharSequence, NGramStats> docStats = CharUtils.calcNGramStats(text, MIN_NGRAM_LENGTH, MAX_NGRAM_LENGTH);
            mergeStats(language, docStats);

            _docsPerLanguage.increment(language);
        }
        
        public Map<String, NGramVector> makeVectors() {
            Set<String> languages = _langStats.keySet();
            Map<String, NGramVector> langVectors = new HashMap<String, NGramVector>();

            NGramVector mergedVector = new NGramVector();

            for (String language : languages) {
                NGramVector vector = makeVector(language, languages);
                mergedVector.merge(vector);
                langVectors.put(language, vector);
            }

            System.out.println(String.format("Total vector terms = %d", mergedVector.size()));

            return langVectors;
        }

        private NGramVector makeVector(String targetLanguage, Set<String> otherLanguages) {
            double docsForThisLanguage = _docsPerLanguage.get(targetLanguage);
            Map<CharSequence, NGramStats> languageStats = _langStats.get(targetLanguage);

            // For each language, we want to pick the N best ngrams. The best ngram is the one with the highest
            // score, where the score is composed of the ngram's doc frequency for this language (higher is better)
            // the the frequency of the ngram in this language compared to the ngram in all languages (higher is
            // also better).

            List<NGramScore> bestNGrams = new ArrayList<ModelBuilder.NGramScore>(languageStats.size());
            for (CharSequence ngram : languageStats.keySet()) {
                NGramStats stats = languageStats.get(ngram);
                double df = stats.getDocCount() / docsForThisLanguage;

                int ngramsInOtherLanguages = 0;
                for (String otherLanguage : otherLanguages) {
                    if (otherLanguage.equals(targetLanguage)) {
                        continue;
                    }

                    NGramStats otherStats = _langStats.get(otherLanguage).get(ngram);
                    if (otherStats != null) {
                        ngramsInOtherLanguages += otherStats.getNGramCount();
                    }
                }

                int ngramCountInThisLanguage = stats.getNGramCount();
                double ngramFrequency = (double)ngramCountInThisLanguage / (double)(ngramCountInThisLanguage + ngramsInOtherLanguages);

                // Our score is essentially the probability that this ngram will be in a document for our
                // target language, and that if exists it will be for our target language.
                double ngramScore = df * ngramFrequency;

                // TODO remove me
                // Try out limiting otherDF to max value.
                if (ngramFrequency >= MIN_NGRAMFREQUENCY) {
                    bestNGrams.add(new NGramScore(ngram, ngramScore, ngramFrequency));
                }
            }

            // Pick the best ngrams
            Collections.sort(bestNGrams);
            int maxIndex = Math.min(TARGET_NGRAMS, bestNGrams.size()) - 1;

            NGramVector vector = new NGramVector(TARGET_NGRAMS);
            double totalLanguageScore = 0.0;
            for (int i = 0; (i <= maxIndex) && (totalLanguageScore < TARGET_SCORE); i++) {
                NGramScore ngramScore = bestNGrams.get(i);

                // The weight is the frequency of this ngram in this language, versus all languages
                // So it's the probability that it's this language, if we see this ngram.
                double frequency = ngramScore.getFrequency();
                // System.out.println(String.format("NGram '%s' has score %f and frequency %f", ngramScore.getNGram(), score, frequency));

                vector.set(ngramScore.getNGram(), frequency);
                totalLanguageScore += ngramScore.getScore();
            }

            System.out.println(String.format("Language '%s' size = %d, total score = %f", targetLanguage, vector.size(), totalLanguageScore));
            return vector;
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
