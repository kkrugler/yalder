package org.krugler.yalder.text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.krugler.yalder.BaseLanguageDetector;
import org.krugler.yalder.BaseLanguageModel;
import org.krugler.yalder.DetectionResult;
import org.krugler.yalder.LanguageLocale;

import com.clearspring.analytics.stream.frequency.CountMinSketch;

/**
 * Version of detector that works with text-based ngrams, versus hash codes.
 * 
 * Useful for debugging character normalization, issues with the detector, etc.
 *
 */
public class TextLanguageDetector extends BaseLanguageDetector {

    private CountMinSketch _ngramCounts;
    private Map<LanguageLocale, CountMinSketch> _langNgramCounts;
    private Map<LanguageLocale, Double> _langProbabilities;
    private Map<LanguageLocale, Double> _averageLangProbabilities;
    
    // Dynamic values during detection of one document.
    private int _numKnownNGrams;
    private int _numUnknownNGrams;
    
    private boolean _segmented = false;
    
    public TextLanguageDetector(Collection<BaseLanguageModel> models) {
        this(models, getMaxNGramLengthFromModels(models));
    }

    public TextLanguageDetector(Collection<BaseLanguageModel> models, int maxNGramLength) {
        super(models, maxNGramLength);
        
        // Build a master map from ngram to per-language probabilities
        // Each model should contain a normalized count (not probability) of the ngram
        // so we can compute probabilities for languages being mixed-in.
        _langNgramCounts = new HashMap<>();
        _ngramCounts = new CountMinSketch(0.000001, 0.99, 666);
        
        _langProbabilities = new HashMap<>();
        _averageLangProbabilities = new HashMap<>();
        
        for (BaseLanguageModel baseModel : _models) {
            TextLanguageModel model = (TextLanguageModel)baseModel;
            LanguageLocale language = model.getLanguage();
            
            // Create default entries, so these maps have all valid languages as keys
            _langProbabilities.put(language, 0.0);
            _averageLangProbabilities.put(language, 0.0);
            
            CountMinSketch cms = new CountMinSketch(0.00001, 0.99, 666);
            _langNgramCounts.put(language, cms);
            
            Map<String, Integer> langCounts = model.getNGramCounts();
            for (String ngram : langCounts.keySet()) {
                int ngramCount = langCounts.get(ngram);
                cms.add(ngram, ngramCount);
                _ngramCounts.add(ngram, ngramCount);
            }
            
            cms.add("", model.getAlpha());
            if (cms.estimateCount("") != model.getAlpha()) {
                System.out.format("%s: alpha count is %d, estimated as %d\n", language.getISO3LetterName(), model.getAlpha(), cms.estimateCount(""));
            }
            
            for (String ngram : langCounts.keySet()) {
                int estimate = (int)cms.estimateCount(ngram);
                if (estimate != langCounts.get(ngram)) {
                    System.out.format("%s: '%s' count is %d, estimated as %d\n", language.getISO3LetterName(), ngram, langCounts.get(ngram), estimate);
                }
            }
        }
    }
    
    public boolean isSegmented() {
        return _segmented;
    }
    
    public void setSegmented(boolean segmented) {
        _segmented = segmented;
    }
    
    @Override
    public void reset() {
        double startingProb = 1.0 / _langProbabilities.size();
        for (LanguageLocale language : _langProbabilities.keySet()) {
            _langProbabilities.put(language,  startingProb);
            _averageLangProbabilities.put(language, 0.0);
        }
        
        _numKnownNGrams = 0;
        _numUnknownNGrams = 0;
    }

    @Override
    public void addText(char[] text, int offset, int length) {
        addText(text, offset, length, null, null);
    }
    
    public void addText(char[] text, int offset, int length, StringBuilder details, Set<String> detailLanguages) {
        TextTokenizer tokenizer = new TextTokenizer(text, offset, length, _maxNGramLength);
        
        while (tokenizer.hasNext()) {
            String ngram = tokenizer.next();
            
            double totalCount = (double)_ngramCounts.estimateCount(ngram);
            if (totalCount == 0) {
                _numUnknownNGrams += 1;
                continue;
            }
            
            _numKnownNGrams += 1;
            for (LanguageLocale ll : _langNgramCounts.keySet()) {
                long langNgramCount = _langNgramCounts.get(ll).estimateCount(ngram);
                double prob = langNgramCount / totalCount;
                if (prob == 0) {
                    prob = _alpha;
                }
                
                prob += (1.0 - prob) * _dampening;
                double newProb = _langProbabilities.get(ll) * prob;
                _langProbabilities.put(ll, newProb);
            }

            // So we don't let probabilities become 0.0, we have to adjust
            if ((_numKnownNGrams % _renormalizeInterval) == 0) {
                normalizeLangProbabilities();
            }
        }
    }

    @Override
    public Collection<DetectionResult> detect() {
        
        normalizeLangProbabilities();
        
        List<DetectionResult> result = new ArrayList<DetectionResult>();
        for (LanguageLocale language : _langProbabilities.keySet()) {
            double curProb = 0.0;
            if (isSegmented()) {
                curProb = _averageLangProbabilities.get(language);
            } else {
                curProb = _langProbabilities.get(language);
            }
            
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
     * languages we actually care about, return a string with the details, where languages
     * are sorted by their probability (high to low), filtered to the set of ones of interest.
     * 
     * This is only used during debugging.
     * 
     * @param langProbabilities
     * @param detailLanguages
     * @return sorted language+probabilities
     */
    private String getSortedProbabilities(Map<LanguageLocale, Double> langProbabilities, Set<String> detailLanguages) {
        Map<LanguageLocale, Double> remainingLanguages = new HashMap<>(langProbabilities);
        
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
