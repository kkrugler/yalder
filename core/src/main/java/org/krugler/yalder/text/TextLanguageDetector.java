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

/**
 * Version of detector that works with text-based ngrams, versus hash codes.
 * 
 * Useful for debugging character normalization, issues with the detector, etc.
 *
 */
public class TextLanguageDetector extends BaseLanguageDetector {

    // For each ngram, store the probability for each language
    private Map<String, Map<LanguageLocale, Double>> _ngramProbabilities;
    private Map<LanguageLocale, Double> _langProbabilities;
    
    // Dynamic values during detection of one document.
    private int _numKnownNGrams;
    private int _numUnknownNGrams;
    
    public TextLanguageDetector(Collection<BaseLanguageModel> models) {
        this(models, getMaxNGramLengthFromModels(models));
    }

    public TextLanguageDetector(Collection<BaseLanguageModel> models, int maxNGramLength) {
        super(models, maxNGramLength);
        
        // TODO verify that each model is a text model
        
        // TODO here's the approach
        // Build a master map from ngram to per-language probabilities
        // Each model should contain a normalized count (not probability) of the ngram
        // so we can compute probabilities for languages being mixed-in.
        
        Map<String, Map<LanguageLocale, Integer>> ngramCounts = new HashMap<String, Map<LanguageLocale, Integer>>();
        _langProbabilities = new HashMap<LanguageLocale, Double>();
        
        for (BaseLanguageModel baseModel : _models) {
            TextLanguageModel model = (TextLanguageModel)baseModel;
            LanguageLocale language = model.getLanguage();
            _langProbabilities.put(language, 0.0);
            
            Map<String, Integer> langCounts = model.getNGramCounts();
            for (String ngram : langCounts.keySet()) {
                Map<LanguageLocale, Integer> curCounts = ngramCounts.get(ngram);
                if (curCounts == null) {
                    curCounts = new HashMap<LanguageLocale, Integer>();
                    ngramCounts.put(ngram, curCounts);
                }
                
                int newCount = langCounts.get(ngram);
                Integer curCount = curCounts.get(language);
                if (curCount == null) {
                    curCounts.put(language, newCount);
                } else {
                    curCounts.put(language, curCount + newCount);
                }
            }
        }
        
        // Now we can calculate the probabilities
        _ngramProbabilities = new HashMap<String, Map<LanguageLocale, Double>>();
        for (String ngram : ngramCounts.keySet()) {
            Map<LanguageLocale, Integer> counts = ngramCounts.get(ngram);
            double totalCount = 0;
            for (LanguageLocale language : counts.keySet()) {
                totalCount += counts.get(language);
            }
            
            Map<LanguageLocale, Double> probabilities = new HashMap<LanguageLocale, Double>();
            for (LanguageLocale language : counts.keySet()) {
                probabilities.put(language, counts.get(language)/totalCount);
            }
            
            _ngramProbabilities.put(ngram, probabilities);
        }
    }
    
    @Override
    public void reset() {
        double startingProb = 1.0 / _langProbabilities.size();
        for (LanguageLocale language : _langProbabilities.keySet()) {
            _langProbabilities.put(language,  startingProb);
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
            
            Map<LanguageLocale, Double> probs = _ngramProbabilities.get(ngram);
            if (probs == null) {
                // FUTURE track how many unknown ngrams we get, and use that
                // to adjust probabilities.
                _numUnknownNGrams += 1;
//                
//                if (provideDetails) {
//                    details.append(String.format("'%s': not found\n", ngram));
//                }
//                
                continue;
            }
            
            _numKnownNGrams += 1;
            if (details != null) {
                details.append(String.format("ngram '%s' probs:", ngram));
                details.append(detailLanguages == null ? '\n' : ' ');
            }
            
            for (LanguageLocale language : _langProbabilities.keySet()) {
                Double probObj = probs.get(language);
                double prob = (probObj == null ? _alpha : probObj);
                if ((details != null) && (probObj != null) && ((detailLanguages == null) || (detailLanguages.contains(language)))) {
                    details.append(String.format("\t'%s'=%f", language, prob));
                    details.append(detailLanguages == null ? '\n' : ' ');
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
            
            if ((details != null) && (detailLanguages != null)) {
                details.append('\n');
            }

            if ((details != null) || (_numKnownNGrams % 10) == 0) {
                normalizeLangProbabilities();
            }
            
            if (details != null) {
                details.append("lang probabilities: ");
                details.append(getSortedProbabilities(_langProbabilities, detailLanguages));
                details.append('\n');
            }
        }
    }

    @Override
    public Collection<DetectionResult> detect() {
        
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
