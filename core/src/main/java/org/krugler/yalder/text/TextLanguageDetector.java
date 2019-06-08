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
        
        // TODO verify that each model is a text model
        
        // Build a master map from ngram to per-language probabilities
        // Each model should contain a normalized count (not probability) of the ngram
        // so we can compute probabilities for languages being mixed-in.
        
        Map<String, Map<LanguageLocale, Integer>> ngramCounts = new HashMap<>();
        Map<LanguageLocale, Integer> missingNgramCounts = new HashMap<>();
        _langProbabilities = new HashMap<>();
        _averageLangProbabilities = new HashMap<>();
        
        double totalMissingNGramCounts = 0.0;
        for (BaseLanguageModel baseModel : _models) {
            TextLanguageModel model = (TextLanguageModel)baseModel;
            LanguageLocale language = model.getLanguage();
            _langProbabilities.put(language, 0.0);
            _averageLangProbabilities.put(language, 0.0);
            
            missingNgramCounts.put(language, model.getAlpha());
            totalMissingNGramCounts += model.getAlpha();
            
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
//            for (LanguageLocale language : counts.keySet()) {
//                totalCount += counts.get(language);
//            }
            for (LanguageLocale language : _langProbabilities.keySet()) {
                if (counts.containsKey(language)) {
                    totalCount += counts.get(language);
                } else {
                    // totalCount += missingNgramCounts.get(language);
                }
            }

            Map<LanguageLocale, Double> probabilities = new HashMap<LanguageLocale, Double>();
            for (LanguageLocale language : counts.keySet()) {
                probabilities.put(language, counts.get(language)/totalCount);
            }
            
            _ngramProbabilities.put(ngram, probabilities);
        }
        
        // And calculate per-language probabilities for any ngram that doesn't exist at all, and save that
        // for the empty ngram.
        Map<LanguageLocale, Double> missingNGramProbabilities = new HashMap<>();
        for (LanguageLocale language : _langProbabilities.keySet()) {
            missingNGramProbabilities.put(language, missingNgramCounts.get(language)/totalMissingNGramCounts);
        }
        
        _ngramProbabilities.put("", missingNGramProbabilities);
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
            
            Map<LanguageLocale, Double> probs = _ngramProbabilities.get(ngram);
            if (probs == null) {
                // FUTURE track how many unknown ngrams we get, and use that
                // to adjust probabilities.
                _numUnknownNGrams += 1;
                
                // TODO figure out whether to skip unknown ngrams, or use missing ngram probabilities.
                // probs = alphaProbs;
                // probs = _ngramProbabilities.get("");
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
                
                prob += (1.0 - prob) * _dampening;
                double newProb = _langProbabilities.get(language) * prob;
                _langProbabilities.put(language, newProb);
            }
            
            if ((details != null) && (detailLanguages != null)) {
                details.append('\n');
            }

            if (details != null) {
                details.append("lang probabilities: ");
                details.append(getSortedProbabilities(_langProbabilities, detailLanguages));
                details.append('\n');
            }
            
            // So we don't let probabilities become 0.0, we have to adjust
            if ((_numKnownNGrams % _renormalizeInterval) == 0) {
                normalizeLangProbabilities();
                
                if (isSegmented()) {
                    // Find the best language. If the best language probability
                    // is > our min probability, record it.
                    double bestProb = 0.0;
                    LanguageLocale bestLang = null;
                    double startingProb = 1.0 / _langProbabilities.size();

                    for (LanguageLocale ll : _langProbabilities.keySet()) {
                        double curProb = _langProbabilities.put(ll, startingProb);

                        if (curProb > bestProb) {
                            bestProb = curProb;
                            bestLang = ll;
                        }

                        _averageLangProbabilities.put(ll, _averageLangProbabilities.get(ll) + curProb);
                    }

                    if ((details != null) && (bestProb >= MIN_LANG_PROBABILITY)) {
                        details.append("current result: ");
                        details.append(bestLang.toString());
                        details.append('\n');
                    }
                }
            }
        }
        
        if (isSegmented() && (details != null)) {
            details.append("Average lang probabilities: ");
            details.append(getSortedProbabilities(_averageLangProbabilities, detailLanguages));
            details.append('\n');
        }
    }

    @Override
    public Collection<DetectionResult> detect() {
        
        normalizeLangProbabilities();

        if (isSegmented()) {
            int pendingTokens = (_numKnownNGrams + _numUnknownNGrams) % _renormalizeInterval;
            double totalProb = 0.0;
            for (LanguageLocale ll : _langProbabilities.keySet()) {
                // Weight probability by how many tokens we completed out of current segment.
                double curProb = (_langProbabilities.get(ll) * pendingTokens) / _renormalizeInterval;
                double newProb = _averageLangProbabilities.get(ll) + curProb;
                _averageLangProbabilities.put(ll, newProb);
                totalProb += newProb;
            }
            
            // Now normalize to sum to 1.0f
            for (LanguageLocale ll : _langProbabilities.keySet()) {
                double normalized = _averageLangProbabilities.get(ll) / totalProb;
                _averageLangProbabilities.put(ll,  normalized);
            }
        }
        
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
