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

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

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
    
    // For each ngram, that only occurs in a single language
    // model, store that model's index.
    private Int2IntMap _ngramToOneLanguage;
    
    // For each ngram, store the probability for each language.
    private double[][] _ngramProbabilities;
    
    // Dynamic state when processing text for a document. There is one
    // double value for every supported language (based on loaded models)
    double[] _langProbabilities;
    double[] _singleLangProbabilities;
    
    // For normalization, confidence, and early termination.
    int _numKnownNGrams;
    int _numUnknownNGrams;
    int _curBestLanguageIndex;

    boolean _hasEnoughText;
    
    // TODO support mixed language mode.
    // TODO support short text mode (renormalize more often)
    
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
        _ngramToOneLanguage = new Int2IntOpenHashMap();
        for (BaseLanguageModel baseModel : _models) {
            int langIndex = langToIndex(baseModel.getLanguage());
            HashLanguageModel model = (HashLanguageModel)baseModel;
            IntIntMap langCounts = model.getNGramCounts();
            for (int ngramHash : langCounts.keySet()) {
                _ngramToIndex.add(ngramHash);
                if (_ngramToOneLanguage.containsKey(ngramHash)) {
                    _ngramToOneLanguage.put(ngramHash, -1);
                } else {
                    _ngramToOneLanguage.put(ngramHash, langIndex);
                }
            }
        }
        
        // For singletons, remove from _ngramToIndex, and add to _ngramToOneLang, with
        // the language index.
        for (int key : _ngramToOneLanguage.keySet()) {
            if (_ngramToOneLanguage.get(key) == -1) {
                _ngramToOneLanguage.remove(key);
            } else {
                _ngramToIndex.remove(key);
            }
        }
        
        // Now we can set a unique index for each ngram.
        _ngramToIndex.setIndexes();
        
        int uniqueNGrams = _ngramToIndex.size();
        int [][] ngramCounts = new int[uniqueNGrams][];
        
        _ngramProbabilities = new double[uniqueNGrams][];
        
        for (BaseLanguageModel baseModel : _models) {
            HashLanguageModel model = (HashLanguageModel)baseModel;
            LanguageLocale language = model.getLanguage();
            int langIndex = langToIndex(language);
            
            IntIntMap langCounts = model.getNGramCounts();
            for (int ngramHash : langCounts.keySet()) {
                // If it's not one of the ngrams for a single language,
                // we need to calculate probabilities across languages.
                if (!_ngramToOneLanguage.containsKey(ngramHash)) {
                    int index = _ngramToIndex.getIndex(ngramHash);
                    int[] counts = ngramCounts[index];
                    if (counts == null) {
                        counts = new int[numLanguages];
                        ngramCounts[index] = counts;
                    }

                    counts[langIndex] = langCounts.getValue(ngramHash);
                }
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
    public void reset() {
        final int numLanguages = _langToIndex.size();
        _langProbabilities = new double[numLanguages];
        double startingProb = 1.0 / numLanguages;
        for (int i = 0; i < numLanguages; i++) {
            _langProbabilities[i] = startingProb;
        }
        
        _singleLangProbabilities = new double[numLanguages];
        
        _numKnownNGrams = 0;
        _numUnknownNGrams = 0;
        _curBestLanguageIndex = -1;
        _hasEnoughText = false;
    }
    
    @Override
    public void addText(char[] text, int offset, int length) {
        final int numLanguages = _langToIndex.size();
        
        HashTokenizer tokenizer = new HashTokenizer(text, offset, length, _maxNGramLength);
        while (tokenizer.hasNext()) {
            int hash = tokenizer.next();
            int index = _ngramToIndex.getIndex(hash);
            
            double[] ngramProbabilities = null;
            int singleLangIndex = -1;
            if (index == -1) {
                // Might be an ngram that's only in the model for single language
                singleLangIndex = _ngramToOneLanguage.get(hash);
                if (singleLangIndex == -1) {
                    // FUTURE track how many unknown ngrams we get, and use that
                    // to adjust probabilities.
                    _numUnknownNGrams += 1;
                    continue;
               }
                    
                ngramProbabilities = _singleLangProbabilities;
                ngramProbabilities[singleLangIndex] = 1.0;
            } else {
                ngramProbabilities = _ngramProbabilities[index];
            }
            
            _numKnownNGrams += 1;
            
            for (int langIndex = 0; langIndex < numLanguages; langIndex++) {
                double prob = ngramProbabilities[langIndex];
                
                // Unknown ngrams for the language get a default probability of "alpha".
                if (prob == 0.0) {
                    prob = _alpha;
                }
                
                // apply dampening, which increases the probability by a percentage
                // of the delta from 1.0, and thus reduces the rapid swings caused by
                // getting a few ngrams in a row with very low probability for an
                // interesting language.
                prob += (1.0 - prob) * _dampening;
                
                _langProbabilities[langIndex] *= prob;
            }
            
            if (singleLangIndex != -1) {
                _singleLangProbabilities[singleLangIndex] = 0.0;
            }
            
            // So we don't let probabilities become 0.0, we have to adjust
            if ((_numKnownNGrams % RENORMALIZE_INTERVAL) == 0) {
                normalizeLangProbabilities();
            }
            
            // See if we haven't had a change in a very probable language in N ngrams
            // We rely on probabilities being normalized, so our interval is always a
            // multiple of the renormalization interval.
            
            // TODO need to factor in confidence, which includes number of unknown ngrams.
            // TODO support "mixed text" mode, and skip this if it's true.
            if ((_numKnownNGrams % EARLY_TERMINATION_INTERVAL) == 0) {
                int newBestLanguageIndex = calcBestLanguageIndex();
                if ((newBestLanguageIndex != -1) && (newBestLanguageIndex == _curBestLanguageIndex)) {
                    _hasEnoughText = true;
                    break;
                }
                
                _curBestLanguageIndex = newBestLanguageIndex;
            }
        }
    }
    
    @Override
    public boolean hasEnoughText() {
        return _hasEnoughText;
    }
    
    @Override
    public Collection<DetectionResult> detect() {
        normalizeLangProbabilities();

        List<DetectionResult> result = new ArrayList<DetectionResult>();
        for (LanguageLocale language : _langToIndex.keySet()) {
            double curProb = _langProbabilities[_langToIndex.get(language)];
            
            if (curProb >= MIN_LANG_PROBABILITY) {
                DetectionResult dr = new DetectionResult(language, curProb);
                result.add(dr);
            }
        }

        Collections.sort(result);
        return result;
    }

    private int calcBestLanguageIndex() {
        for (int i = 0; i < _langProbabilities.length; i++) {
            if (_langProbabilities[i] > MIN_GOOD_LANG_PROBABILITY) {
                return i;
            }
        }

        return -1;
    }

    private void normalizeLangProbabilities() {
        double totalProb = 0.0;
        for (double prob : _langProbabilities) {
            totalProb += prob;
        }
        
        double scalar = 1.0/totalProb;
        
        for (int i = 0; i < _langProbabilities.length; i++) {
            _langProbabilities[i] *= scalar;
        }
    }

}
