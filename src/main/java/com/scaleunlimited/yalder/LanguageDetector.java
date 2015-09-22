package com.scaleunlimited.yalder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LanguageDetector {

    public static final double DEFAULT_ALPHA = 0.000002;
    public static final double DEFAULT_DAMPENING = 0.001;

    private static final double MIN_LANG_PROBABILITY = 0.1;
    
    private Collection<LanguageModel> _models;
    
    // For each ngram, store the probability for each language
    private Map<String, Map<LanguageLocale, Double>> _ngramProbabilities;
    private Map<LanguageLocale, Double> _langProbabilities;
    private int _maxNGramLength;
    
    // The probability we use for an ngram when we have no data for it for a given language.
    private double _alpha;
    
    // Used to increase an ngram's probability, to reduce rapid swings in short text
    // snippets. The probability is increased by (1 - prob) * dampening.
    private double _dampening = DEFAULT_DAMPENING;
    
    public LanguageDetector(Collection<LanguageModel> models) {
        this(models, models.iterator().next().getMaxNGramLength());
    }

    // FUTURE support varying max ngram length per model.
    public LanguageDetector(Collection<LanguageModel> models, int maxNGramLength) {
        _models = models;
        _maxNGramLength = maxNGramLength;
        _alpha = DEFAULT_ALPHA;
        
        // TODO here's the approach
        // Build a master map from ngram to per-language probabilities
        // Each model should contain a normalized count (not probability) of the ngram
        // so we can compute probabilities for languages being mixed-in.
        
        Map<String, Map<LanguageLocale, Integer>> ngramCounts = new HashMap<String, Map<LanguageLocale, Integer>>();
        _langProbabilities = new HashMap<LanguageLocale, Double>();
        
        for (LanguageModel model : _models) {
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
    
    public LanguageDetector setAlpha(double alpha) {
        _alpha = alpha;
        return this;
    }
    
    public double getAlpha() {
        return _alpha;
    }
    
    public LanguageDetector setDampening(double dampening) {
        _dampening = dampening;
        return this;
    }
    
    public double getDampening() {
        return _dampening;
    }
    
    /**
     * Return true if at least one of the loaded models is for a language that
     * is weakly equal to <target>. This means that (in theory) we could get a
     * detection result that would also be weakly equal to <target>.
     * 
     * @param target Language of interest
     * @return true if it's supported by the loaded set of models.
     */
    
    public boolean supportsLanguage(LanguageLocale target) {
        for (LanguageModel model : _models) {
            if (model.getLanguage().weaklyEqual(target)) {
                return true;
            }
        }
        
        return false;
    }
    
    public Collection<DetectionResult> detect(String text) {
        return detect(text, null);
    }
    
    public Collection<DetectionResult> detect(String text, StringBuilder details) {
        return detect(text, details, null);
    }
    
    public Collection<DetectionResult> detect(String text, StringBuilder details, Set<String> detailLanguages) {
        double startingProb = 1.0 / _langProbabilities.size();
        for (LanguageLocale language : _langProbabilities.keySet()) {
            _langProbabilities.put(language,  startingProb);
        }
        
        int numKnownNGrams = 0;
        int numUnknownNGrams = 0;
        NGramTokenizer tokenizer = new NGramTokenizer(text, 1, _maxNGramLength);
        while (tokenizer.hasNext()) {
            String ngram = tokenizer.next();
            
            Map<LanguageLocale, Double> probs = _ngramProbabilities.get(ngram);
            if (probs == null) {
                // FUTURE track how many unknown ngrams we get, and use that
                // to adjust probabilities.
                numUnknownNGrams += 1;
//                
//                if (provideDetails) {
//                    details.append(String.format("'%s': not found\n", ngram));
//                }
//                
                continue;
            }
            
            numKnownNGrams += 1;
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

            if ((details != null) || (numKnownNGrams % 10) == 0) {
                normalizeLangProbabilities();
            }
            
            if (details != null) {
                details.append("lang probabilities: ");
                details.append(getSortedProbabilities(_langProbabilities, detailLanguages));
                details.append('\n');
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

    private LanguageModel getModel(String language) {
        for (LanguageModel model : _models) {
            
            if (model.getLanguage().equals(language)) {
                return model;
            }
        }
        
        throw new IllegalArgumentException("Unknown language: " + language);
    }

}
