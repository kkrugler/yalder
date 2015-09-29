package com.scaleunlimited.yalder.hash;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.scaleunlimited.yalder.BaseLanguageDetector;
import com.scaleunlimited.yalder.BaseLanguageModel;
import com.scaleunlimited.yalder.DetectionResult;
import com.scaleunlimited.yalder.LanguageLocale;
import com.scaleunlimited.yalder.text.TextTokenizer;

/**
 * Language detector that works with ngram hashes (versus text), for
 * efficiency.
 *
 */
public class HashLanguageDetector extends BaseLanguageDetector {

    // Map from language of model to index used for accessing arrays.
    private Map<LanguageLocale, Integer> _langToIndex;
    
    // Map from ngram (hash) to index.
    private IntToIndex _ngramToIndex;
    
    // For each ngram, store the probability for each language.
    private double[][] _ngramProbabilities;
    
    // TODO switch to double[] vs. map
    private Map<LanguageLocale, Double> _langProbabilities;
    
    public HashLanguageDetector(Collection<BaseLanguageModel> models) {
        this(models, getMaxNGramLengthFromModels(models));
    }

    public HashLanguageDetector(Collection<BaseLanguageModel> models, int maxNGramLength) {
        super(models, maxNGramLength);

        // TODO verify that each model is a binary model

        int numLanguages = makeLangToIndex(models);
        
        // Build a master map from ngram to per-language probabilities
        // Each model should contain a normalized count (not probability) of the ngram
        // so we can compute probabilities for languages being mixed-in.
        
        // So the first step is to build a map from every ngram (hash) to an index.
        _ngramToIndex = new IntToIndex();
        for (BaseLanguageModel baseModel : _models) {
            HashLanguageModel model = (HashLanguageModel)baseModel;
            Map<Integer, Integer> langCounts = model.getNGramCounts();
            for (Integer ngramHash : langCounts.keySet()) {
                _ngramToIndex.add(ngramHash);
            }
        }
        
        int uniqueNGrams = _ngramToIndex.size();
        int [][] ngramCounts = new int[uniqueNGrams][];
        
        _langProbabilities = new HashMap<LanguageLocale, Double>();
        _ngramProbabilities = new double[uniqueNGrams][];
        
        for (BaseLanguageModel baseModel : _models) {
            HashLanguageModel model = (HashLanguageModel)baseModel;
            LanguageLocale language = model.getLanguage();
            int langIndex = langToIndex(language);
            
            _langProbabilities.put(language, 0.0);
            
            Map<Integer, Integer> langCounts = model.getNGramCounts();
            for (Integer ngramHash : langCounts.keySet()) {
                int index = _ngramToIndex.getIndex(ngramHash);
                int[] counts = ngramCounts[index];
                if (counts == null) {
                    counts = new int[numLanguages];
                    ngramCounts[index] = counts;
                }
                
                counts[langIndex] = langCounts.get(ngramHash);
            }
        }
        
        // Now we can calculate the probabilities
        for (int i = 0; i < uniqueNGrams; i++) {
           double totalCount = 0;
           int[] counts = ngramCounts[i];
            for (int j = 0; j < counts.length; j++) {
                totalCount += counts[j];
            }
            
            
            double[] probs = new double[numLanguages];
            for (int j = 0; j < counts.length; j++) {
                probs[j] = counts[j] / totalCount;
            }
            
            _ngramProbabilities[i] = probs;
        }
    }
    
    private int langToIndex(LanguageLocale language) {
        return _langToIndex.get(language);
    }
    
    private int makeLangToIndex(Collection<BaseLanguageModel> models) {
        // Build a master map from language to index (0...n-1), which we'll use to index into
        // arrays associated with each ngram.
        
        _langToIndex = new HashMap<>(_models.size());
        int curIndex = 0;
        for (BaseLanguageModel model : _models) {
            if (_langToIndex.put(model.getLanguage(), curIndex) != null) {
                throw new IllegalArgumentException("Got two models with the same language: " + model.getLanguage());
            }
            
            curIndex += 1;
        }
        
        return curIndex;
    }

    @Override
    public Collection<DetectionResult> detect(String text) {
        double startingProb = 1.0 / _langProbabilities.size();
        for (LanguageLocale language : _langProbabilities.keySet()) {
            _langProbabilities.put(language,  startingProb);
        }
        
        int numKnownNGrams = 0;
        int numUnknownNGrams = 0;
        TextTokenizer tokenizer = new TextTokenizer(text, 1, _maxNGramLength);
        while (tokenizer.hasNext()) {
            String ngram = tokenizer.next();
            int hash = ngram.hashCode();
            int index = _ngramToIndex.getIndex(hash);
            
            if (index == -1) {
                // FUTURE track how many unknown ngrams we get, and use that
                // to adjust probabilities.
                numUnknownNGrams += 1;
                continue;
            }
            
            numKnownNGrams += 1;
            
            for (LanguageLocale language : _langProbabilities.keySet()) {
                int langIndex = langToIndex(language);
                double prob = _ngramProbabilities[index][langIndex];
                
                // Unknown ngrams for the language get a default probability of "alpha".
                if (prob == 0.0) {
                    prob = _alpha;
                }
                // apply dampening, which increases the probability by a percentage
                // of the delta from 1.0, and thus reduces the rapid swings caused by
                // getting a few ngrams in a row with very low probability for an
                // interesting language.
                prob += (1.0 - prob) * _dampening;
                
                double curProb = _langProbabilities.get(language);
                curProb *= prob;
                _langProbabilities.put(language, curProb);
            }
        }
        
        normalizeLangProbabilities();

        List<DetectionResult> result = new ArrayList<DetectionResult>();
        for (LanguageLocale language : _langProbabilities.keySet()) {
            double curProb = _langProbabilities.get(language);
            
            if (curProb >= MIN_LANG_PROBABILITY) {
                DetectionResult dr = new DetectionResult(language, curProb);
                result.add(dr);
            }
        }

        Collections.sort(result);
        return result;
    }

    /**
     * Given a set of languages and each one's current probability, and an optional set of
     * languages we actually care about, return a string with the details, where langauges
     * are sorted by their probability (high to low), filtered to the set of ones of interest.
     * 
     * This is only used during debugging.
     * 
     * @param langProbabilities
     * @param detailLanguages
     * @return sorted language+probabilities
     */
    private String getSortedProbabilities(Map<LanguageLocale, Double> langProbabilities, Set<String> detailLanguages) {
        Map<LanguageLocale, Double> remainingLanguages = new HashMap<LanguageLocale, Double>(langProbabilities);
        
        StringBuilder result = new StringBuilder();
        while (!remainingLanguages.isEmpty()) {
            double maxProbability = -1.0;
            LanguageLocale maxLanguage = null;
            for (LanguageLocale language : remainingLanguages.keySet()) {
                double langProb = remainingLanguages.get(language);
                if (langProb > maxProbability) {
                    maxProbability = langProb;
                    maxLanguage = language;
                }
            }
            
            if ((maxProbability > 0.0000005) && ((detailLanguages == null) || detailLanguages.contains(maxLanguage))) {
                result.append(String.format("'%s'=%f ", maxLanguage, maxProbability));
                remainingLanguages.remove(maxLanguage);
            } else {
                break;
            }
        }
        
        return result.toString();
    }

    private void normalizeLangProbabilities() {
        double totalProb = 0.0;
        for (LanguageLocale language : _langProbabilities.keySet()) {
            totalProb += _langProbabilities.get(language);
        }

        double scalar = 1.0/totalProb;
        
        for (LanguageLocale language : _langProbabilities.keySet()) {
            double curProb = _langProbabilities.get(language);
            curProb *= scalar;
            _langProbabilities.put(language, curProb);
        }
    }

}
