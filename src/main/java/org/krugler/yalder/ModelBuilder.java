package org.krugler.yalder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.krugler.yalder.hash.HashLanguageModel;
import org.krugler.yalder.hash.HashTokenizer;
import org.krugler.yalder.hash.IntIntMap;
import org.krugler.yalder.text.TextLanguageModel;
import org.krugler.yalder.text.TextTokenizer;

public class ModelBuilder {
    private static final Logger LOGGER = Logger.getLogger(ModelBuilder.class);

    public static final int DEFAULT_MAX_NGRAM_LENGTH = 4;
    
    // Minimum frequency for ngram
    public static final double DEFAULT_MIN_NGRAM_FREQUENCY = 0.00002;
    
    // Minimum number of ngrams we want to see for a language.
    // Since we get roughly <max length> * chars number of ngrams, we're really saying we
    // want at least 4000 characters.
    public static final int DEFAULT_MIN_NGRAMS_FOR_LANGUAGE = 4000 * DEFAULT_MAX_NGRAM_LENGTH;
    
    private Map<LanguageLocale, IntIntMap> _perLangHashCounts;
    private Map<LanguageLocale, Map<String, Integer>> _perLangTextCounts;
    
    private int _maxNGramLength = DEFAULT_MAX_NGRAM_LENGTH;
    private double _minNGramFrequency = DEFAULT_MIN_NGRAM_FREQUENCY;
    private int _minNGramsForLanguage = DEFAULT_MIN_NGRAMS_FOR_LANGUAGE;
    private boolean _binaryMode = true;
    
    public ModelBuilder() {
        _perLangTextCounts = new HashMap<LanguageLocale, Map<String, Integer>>();
        _perLangHashCounts = new HashMap<LanguageLocale, IntIntMap>();
    }
    
    public ModelBuilder setMaxNGramLength(int maxNGramLength) {
        _maxNGramLength = maxNGramLength;
        return this;
    }
    
    public int getMaxNGramLength() {
        return _maxNGramLength;
    }
    
    public ModelBuilder setMinNGramFrequency(double minNGramFrequency) {
        _minNGramFrequency = minNGramFrequency;
        return this;
    }
    
    public double getMinNGramFrequency() {
        return _minNGramFrequency;
    }
    
    public ModelBuilder setBinaryMode(boolean binaryMode) {
        _binaryMode = binaryMode;
        return this;
    }
    
    public void addTrainingDoc(String language, String text) {
        addTrainingDoc(LanguageLocale.fromString(language), text);
    }
    
    // TODO make sure we haven't already made the languages.
    public void addTrainingDoc(LanguageLocale language, String text) {
        if (_binaryMode) {
            addTrainingDocNGramsAsHashes(language, text);
        } else {
            addTrainingDocNGramsAsText(language, text);
        }
    }
    
    public void addTrainingDocNGramsAsText(LanguageLocale language, String text) {
        Map<String, Integer> ngramCounts = _perLangTextCounts.get(language);
        if (ngramCounts == null) {
            ngramCounts = new HashMap<String, Integer>();
            _perLangTextCounts.put(language, ngramCounts);
        }
        
        TextTokenizer tokenizer = new TextTokenizer(text, _maxNGramLength);
        while (tokenizer.hasNext()) {
            String token = tokenizer.next();
            Integer curCount = ngramCounts.get(token);
            if (curCount == null) {
                ngramCounts.put(token, 1);
            } else {
                ngramCounts.put(token, curCount + 1);
            }
        }
    }
    
    public void addTrainingDocNGramsAsHashes(LanguageLocale language, String text) {
        IntIntMap ngramCounts = _perLangHashCounts.get(language);
        if (ngramCounts == null) {
            ngramCounts = new IntIntMap();
            _perLangHashCounts.put(language, ngramCounts);
        }
        
        HashTokenizer tokenizer = new HashTokenizer(text, _maxNGramLength);
        while (tokenizer.hasNext()) {
            int token = tokenizer.next();
            ngramCounts.add(token, 1);
        }
    }
    
    // TODO make sure at least one document has been added.
    
    public Collection<BaseLanguageModel> makeModels() {
        if (_binaryMode) {
            return makeBinaryModels();
        } else {
            return makeTextModels();
        }
    }
    
    private Collection<BaseLanguageModel> makeTextModels() {
        Set<LanguageLocale> languages = new HashSet<LanguageLocale>(_perLangTextCounts.keySet());
        
        // For each language, record the total number of ngrams
        Map<LanguageLocale, Integer> perLanguageTotalNGramCount = new HashMap<LanguageLocale, Integer>();
        
        for (LanguageLocale language : languages) {
            int languageNGrams = 0;
            
            Map<String, Integer> ngramCounts = _perLangTextCounts.get(language);
            for (String ngram : ngramCounts.keySet()) {
                int count = ngramCounts.get(ngram);
                languageNGrams += count;
            }
            
            perLanguageTotalNGramCount.put(language,  languageNGrams);
        }

        int minNormalizedCount = (int)Math.round(_minNGramFrequency) * BaseLanguageModel.NORMALIZED_COUNT;
        Set<LanguageLocale> languagesToSkip = new HashSet<LanguageLocale>();
        for (LanguageLocale language : languages) {
            int ngramsForLanguage = perLanguageTotalNGramCount.get(language);
            
            // if ngrams for language is less than a limit, skip the language
            if (ngramsForLanguage < _minNGramsForLanguage) {
                System.out.println(String.format("Skipping '%s' because it only has %d ngrams", language, ngramsForLanguage));
                languagesToSkip.add(language);
                continue;
            }
            
            double datasizeNormalization = (double)BaseLanguageModel.NORMALIZED_COUNT / (double)ngramsForLanguage;
            Map<String, Integer> ngramCounts = _perLangTextCounts.get(language);
            Map<String, Integer> normalizedCounts = new HashMap<>(ngramCounts.size());
            for (String ngram : ngramCounts.keySet()) {
                int count = ngramCounts.get(ngram);
                int adjustedCount = (int)Math.round(count * datasizeNormalization);
                
                // Set to ignore if we don't have enough of these to matter.
                // TODO actually key this off alpha somehow, as if we don't have an ngram then
                // we give it a probability of x?
                if (adjustedCount >= minNormalizedCount) {
                    normalizedCounts.put(ngram, adjustedCount);
                }
            }
            
            _perLangTextCounts.put(language, normalizedCounts);
        }

        // Remove the languages we want to skip
        languages.removeAll(languagesToSkip);
        
        List<BaseLanguageModel> models = new ArrayList<>();
        for (LanguageLocale language : languages) {
            Map<String, Integer> ngramCounts = _perLangTextCounts.get(language);
            TextLanguageModel model = new TextLanguageModel(language, _maxNGramLength, ngramCounts);
            models.add(model);
        }

        return models;
    }
    
    private Collection<BaseLanguageModel> makeBinaryModels() {
        Set<LanguageLocale> languages = new HashSet<LanguageLocale>(_perLangHashCounts.keySet());
        
        // For each language, record the total number of ngrams
        Map<LanguageLocale, Integer> perLanguageTotalNGramCount = new HashMap<LanguageLocale, Integer>();
        
        for (LanguageLocale language : languages) {
            IntIntMap ngramCounts = _perLangHashCounts.get(language);
            perLanguageTotalNGramCount.put(language, ngramCounts.sum());
        }

        int minNormalizedCount = (int)Math.round(_minNGramFrequency) * BaseLanguageModel.NORMALIZED_COUNT;
        Set<LanguageLocale> languagesToSkip = new HashSet<LanguageLocale>();
        for (LanguageLocale language : languages) {
            int ngramsForLanguage = perLanguageTotalNGramCount.get(language);
            
            // if ngrams for language is less than a limit, skip the language
            if (ngramsForLanguage < _minNGramsForLanguage) {
                System.out.println(String.format("Skipping '%s' because it only has %d ngrams", language, ngramsForLanguage));
                languagesToSkip.add(language);
                continue;
            } else {
                System.out.println(String.format("%s has %d total ngrams",  language, ngramsForLanguage));
            }
            
            double datasizeNormalization = (double)BaseLanguageModel.NORMALIZED_COUNT / (double)ngramsForLanguage;
            IntIntMap ngramCounts = _perLangHashCounts.get(language);
            IntIntMap normalizedCounts = new IntIntMap(ngramCounts.size());
            
            for (int ngramHash : ngramCounts.keySet()) {
                int count = ngramCounts.getValue(ngramHash);
                int adjustedCount = (int)Math.round(count * datasizeNormalization);
                
                // Set to ignore if we don't have enough of these to matter.
                // FUTURE change limit based on length of the ngram (which we no longer have)?
                if (adjustedCount >= minNormalizedCount) {
                    normalizedCounts.add(ngramHash, adjustedCount);
                }
            }
            
            _perLangHashCounts.put(language, normalizedCounts);
        }

        // Remove the languages we want to skip
        languages.removeAll(languagesToSkip);
        
        List<BaseLanguageModel> models = new ArrayList<>();
        for (LanguageLocale language : languages) {
            BaseLanguageModel model = new HashLanguageModel(language, _maxNGramLength, _perLangHashCounts.get(language));
            models.add(model);
        }

        return models;
    }

}
