package org.krugler.yalder.hash;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.krugler.yalder.BaseLanguageDetector;
import org.krugler.yalder.BaseLanguageModel;
import org.krugler.yalder.DetectionResult;
import org.krugler.yalder.LanguageLocale;

/**
 * Language detector that works with ngram hashes (versus text), for
 * efficiency.
 *
 */
public class HashLanguageDetector extends BaseLanguageDetector {

    private static final int RENORMALIZE_INTERVAL  = 10;
    private static final int EARLY_TERMINATION_INTERVAL = RENORMALIZE_INTERVAL * 11;

    // Map from language of model to index used for accessing arrays.
    private Map<LanguageLocale, Integer> _langToIndex;
    
    // Map from ngram (hash) to index.
    private IntToIndex _ngramToIndex;
    
    // For each ngram, store the probability for each language.
    private double[][] _ngramProbabilities;
    
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
            IntIntMap langCounts = model.getNGramCounts();
            for (int ngramHash : langCounts.keySet()) {
                _ngramToIndex.add(ngramHash);
            }
        }
        
        int uniqueNGrams = _ngramToIndex.size();
        int [][] ngramCounts = new int[uniqueNGrams][];
        
        _ngramProbabilities = new double[uniqueNGrams][];
        
        for (BaseLanguageModel baseModel : _models) {
            HashLanguageModel model = (HashLanguageModel)baseModel;
            LanguageLocale language = model.getLanguage();
            int langIndex = langToIndex(language);
            
            IntIntMap langCounts = model.getNGramCounts();
            for (int ngramHash : langCounts.keySet()) {
                int index = _ngramToIndex.getIndex(ngramHash);
                int[] counts = ngramCounts[index];
                if (counts == null) {
                    counts = new int[numLanguages];
                    ngramCounts[index] = counts;
                }
                
                counts[langIndex] = langCounts.getValue(ngramHash);
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
        final int numLanguages = _langToIndex.size();
        double[] langProbabilities = new double[numLanguages];
                        ;
        double startingProb = 1.0 / numLanguages;
        for (int i = 0; i < numLanguages; i++) {
            langProbabilities[i] = startingProb;
        }
        
        int numKnownNGrams = 0;
        int numUnknownNGrams = 0;
        int curBestLanguageIndex = -1;
        
        HashTokenizer tokenizer = new HashTokenizer(text, _maxNGramLength);
        while (tokenizer.hasNext()) {
            int hash = tokenizer.next();
            int index = _ngramToIndex.getIndex(hash);
            
            if (index == -1) {
                // FUTURE track how many unknown ngrams we get, and use that
                // to adjust probabilities.
                numUnknownNGrams += 1;
                continue;
            }
            
            numKnownNGrams += 1;
            
            for (int langIndex = 0; langIndex < numLanguages; langIndex++) {
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
                
                langProbabilities[langIndex] *= prob;
            }
            
            // So we don't let probabilities become 0.0, we have to adjust
            if ((numKnownNGrams % RENORMALIZE_INTERVAL) == 0) {
                normalizeLangProbabilities(langProbabilities);
            }
            
            // See if we haven't had a change in a very probable language in N ngrams
            // We rely on probabilities being normalized, so our interval is always a
            // multiple of the renormalization interval.
            
            // TODO need to factor in confidence, which includes number of unknown ngrams.
            if ((numKnownNGrams % EARLY_TERMINATION_INTERVAL) == 0) {
                int newBestLanguageIndex = calcBestLanguageIndex(langProbabilities);
                if ((newBestLanguageIndex != -1) && (newBestLanguageIndex == curBestLanguageIndex)) {
                    break;
                }
                
                curBestLanguageIndex = newBestLanguageIndex;
            }
        }
        
        normalizeLangProbabilities(langProbabilities);

        List<DetectionResult> result = new ArrayList<DetectionResult>();
        for (LanguageLocale language : _langToIndex.keySet()) {
            double curProb = langProbabilities[_langToIndex.get(language)];
            
            if (curProb >= MIN_LANG_PROBABILITY) {
                DetectionResult dr = new DetectionResult(language, curProb);
                result.add(dr);
            }
        }

        Collections.sort(result);
        return result;
    }

    private int calcBestLanguageIndex(double[] langProbabilities) {
        for (int i = 0; i < langProbabilities.length; i++) {
            if (langProbabilities[i] > MIN_GOOD_LANG_PROBABILITY) {
                return i;
            }
        }

        return -1;
    }

    private void normalizeLangProbabilities(double[] langProbabilities) {
        double totalProb = 0.0;
        for (double prob : langProbabilities) {
            totalProb += prob;
        }
        
        double scalar = 1.0/totalProb;
        
        for (int i = 0; i < langProbabilities.length; i++) {
            langProbabilities[i] *= scalar;
        }
    }

}
