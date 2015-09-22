package com.scaleunlimited.yalder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

public class ModelBuilder {
    private static final Logger LOGGER = Logger.getLogger(ModelBuilder.class);

    public static final int DEFAULT_MAX_NGRAM_LENGTH = 4;
    
    public static final int DEFAULT_MIN_NORMALIZED_COUNT = (int)Math.round(0.00001 * LanguageModel.NORMALIZED_COUNT);
    
    // Minimum number of ngrams we want to see for a language.
    // Since we get roughly <max length> * chars number of ngrams, we're really saying we
    // want at least 5000 characters.
    public static final int DEFAULT_MIN_NGRAMS_FOR_LANGUAGE = 5000 * DEFAULT_MAX_NGRAM_LENGTH;
    
    private Map<LanguageLocale, Map<String, NGramStats>> _perLangNGramCounts;
    private int _maxNGramLength = DEFAULT_MAX_NGRAM_LENGTH;
    private int _minNormalizedCount = DEFAULT_MIN_NORMALIZED_COUNT;
    private int _minNGramsForLanguage = DEFAULT_MIN_NGRAMS_FOR_LANGUAGE;
    
    public ModelBuilder() {
        _perLangNGramCounts = new HashMap<LanguageLocale, Map<String, NGramStats>>();
    }
    
    public ModelBuilder setMaxNGramLength(int maxNGramLength) {
        _maxNGramLength = maxNGramLength;
        return this;
    }
    
    public int getMaxNGramLength() {
        return _maxNGramLength;
    }
    
    public ModelBuilder setMinNormalizedCount(int minNormalizedCount) {
        _minNormalizedCount = minNormalizedCount;
        return this;
    }
    
    public int getMinNormalizedCount() {
        return _minNormalizedCount;
    }
    
    public void addTrainingDoc(String language, String text) {
        addTrainingDoc(LanguageLocale.fromString(language), text);
    }
    
    // TODO make sure we haven't already made the languages.
    public void addTrainingDoc(LanguageLocale language, String text) {
        Map<String, NGramStats> ngramCounts = _perLangNGramCounts.get(language);
        if (ngramCounts == null) {
            ngramCounts = new HashMap<String, NGramStats>();
            _perLangNGramCounts.put(language, ngramCounts);
        }
        
        NGramTokenizer tokenizer = new NGramTokenizer(text, 1, _maxNGramLength);
        while (tokenizer.hasNext()) {
            String token = tokenizer.next();
            NGramStats curStats = ngramCounts.get(token);
            if (curStats == null) {
                curStats = new NGramStats();
                ngramCounts.put(token, curStats);
            }
            
            curStats.incNGramCount();
        }
    }
    
    // TODO make sure at least one document has been added.
    
    public Collection<LanguageModel> makeModels() {
        Set<LanguageLocale> languages = new HashSet<LanguageLocale>(_perLangNGramCounts.keySet());
        
        // For each ngram, record its total count across all languages.
        Map<String, NGramStats> allLangNGramCounts = new HashMap<String, NGramStats>();

        // For each language, record the total number of ngrams
        Map<LanguageLocale, Integer> perLanguageTotalNGramCount = new HashMap<LanguageLocale, Integer>();
        
        for (LanguageLocale language : languages) {
            int languageNGrams = 0;
            
            Map<String, NGramStats> ngramCounts = _perLangNGramCounts.get(language);
            for (String ngram : ngramCounts.keySet()) {
                int count = ngramCounts.get(ngram).getNGramCount();
                languageNGrams += count;
            }
            
            perLanguageTotalNGramCount.put(language,  languageNGrams);
        }

        Set<LanguageLocale> languagesToSkip = new HashSet<LanguageLocale>();
        for (LanguageLocale language : languages) {
            int ngramsForLanguage = perLanguageTotalNGramCount.get(language);
            
            // if ngrams for language is less than a limit, skip the language
            if (ngramsForLanguage < _minNGramsForLanguage) {
                System.out.println(String.format("Skipping '%s' because it only has %d ngrams", language, ngramsForLanguage));
                languagesToSkip.add(language);
                continue;
            }
            
            double datasizeNormalization = (double)LanguageModel.NORMALIZED_COUNT / (double)ngramsForLanguage;
            Map<String, NGramStats> ngramCounts = _perLangNGramCounts.get(language);
            for (String ngram : ngramCounts.keySet()) {
                NGramStats ngramStats = ngramCounts.get(ngram);
                int count = ngramStats.getNGramCount();
                int adjustedCount = (int)Math.round(count * datasizeNormalization);
                
                // Set to ignore if we don't have enough of these to matter.
                // TODO actually key this off alpha somehow, as if we don't have an ngram then
                // we give it a probability of x?
                if (adjustedCount < _minNormalizedCount) {
                    ngramStats.setNGramCount(0);
                    continue;
                }
                
                ngramStats.setNGramCount(adjustedCount);
                
                NGramStats globalStats = allLangNGramCounts.get(ngram);
                if (globalStats == null) {
                    globalStats = new NGramStats();
                    allLangNGramCounts.put(ngram, globalStats);
                }
                
                globalStats.incNGramCount(adjustedCount);
            }
        }

        // Remove the languages we want to skip
        languages.removeAll(languagesToSkip);
        
        // For each language, keep track of all qualifying ngrams.
        Map<LanguageLocale, List<NGramScore>> langNGramByScore = new HashMap<LanguageLocale, List<NGramScore>>();
        
        for (LanguageLocale language : languages) {
            langNGramByScore.put(language, new LinkedList<NGramScore>());
        }
        
        for (String ngram : allLangNGramCounts.keySet()) {
            double totalCount = (double)allLangNGramCounts.get(ngram).getNGramCount();

            for (LanguageLocale language : languages) {
                NGramStats langStatsForThisNGram = _perLangNGramCounts.get(language).get(ngram);
            
                if (langStatsForThisNGram != null) {
                    int count = langStatsForThisNGram.getNGramCount();
                    if (count == 0) {
                        // Ignore ngrams that we've flagged as having too low of a count
                        continue;
                    }
                    
                    double probability = count / totalCount;
                    double score = count * probability;
                    NGramScore lng = new NGramScore(ngram, count, score, probability);
                    
                    langNGramByScore.get(language).add(lng);
                }
            }
        }
        
        List<LanguageModel> models = new ArrayList<LanguageModel>();
        for (LanguageLocale language : languages) {
            List<NGramScore> langResults = langNGramByScore.get(language);
            Collections.sort(langResults);
            
            Map<String, Integer> normalizedCounts = new HashMap<String, Integer>();
            LOGGER.trace(String.format("Language = '%s'", language));
            for (int i = 0; i < langResults.size(); i++) {
                NGramScore result = langResults.get(i);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace(String.format("\t'%s': %f (prob = %f)", result.getNGram(), result.getScore(), result.getProbability()));
                }
                
                normalizedCounts.put(result.getNGram(), result.getCount());
            }
            
            // FUTURE support varying max ngram length per model.
            LanguageModel model = new LanguageModel(language, _maxNGramLength, normalizedCounts);
            models.add(model);
        }

        return models;
    }
    
}
