package com.scaleunlimited.yalder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LanguageDetector {

    public static final double DEFAULT_ALPHA = 0.000002;
    public static final double DEFAULT_DAMPENING = 0.001;

    private static final double MIN_LANG_PROBABILITY = 0.1;
    
    private Collection<LanguageModel> _models;
    
    // Map from language of model to index used for accessing arrays.
    private Map<LanguageLocale, Integer> _langToIndex;
    
    // Map from ngram (hash) to index.
    private IntToIndex _ngramToIndex;
    
    // For each ngram, store the probability for each language
    private double[][] _ngramProbabilities;
    
    private Map<LanguageLocale, Double> _langProbabilities;
    private int _maxNGramLength;
    
    // The probability we use for an ngram when we have no data for it for a given language.
    private double _alpha;
    
    // Used to increase an ngram's probability, to reduce rapid swings in short text
    // snippets. The probability is increased by (1 - prob) * dampening.
    private double _dampening = DEFAULT_DAMPENING;
    
    public LanguageDetector(Collection<LanguageModel> models) {
        this(models, getMaxNGramLengthFromModels(models));
    }

    private static int getMaxNGramLengthFromModels(Collection<LanguageModel> models) {
        int maxNGramLength = 0;
        Iterator<LanguageModel> iter = models.iterator();
        while (iter.hasNext()) {
            maxNGramLength = Math.max(maxNGramLength, iter.next().getMaxNGramLength());
        }
        
        return maxNGramLength;
    }

    public LanguageDetector(Collection<LanguageModel> models, int maxNGramLength) {
        int numLanguages = setModels(models);
        _maxNGramLength = maxNGramLength;
        _alpha = DEFAULT_ALPHA;
        
        // Build a master map from ngram to per-language probabilities
        // Each model should contain a normalized count (not probability) of the ngram
        // so we can compute probabilities for languages being mixed-in.
        
        // So the first step is to build a map from every ngram (hash) to an index.
        _ngramToIndex = new IntToIndex();
        for (LanguageModel model : _models) {
            Map<String, Integer> langCounts = model.getNGramCounts();
            for (String ngram : langCounts.keySet()) {
                _ngramToIndex.add(Integer.parseInt(ngram));
            }
        }
        
        int uniqueNGrams = _ngramToIndex.size();
        int [][] ngramCounts = new int[uniqueNGrams][];
        
        _langProbabilities = new HashMap<LanguageLocale, Double>();
        _ngramProbabilities = new double[uniqueNGrams][];
        
        for (LanguageModel model : _models) {
            LanguageLocale language = model.getLanguage();
            int langIndex = langToIndex(language);
            
            _langProbabilities.put(language, 0.0);
            
            Map<String, Integer> langCounts = model.getNGramCounts();
            for (String ngram : langCounts.keySet()) {
                int hash = Integer.parseInt(ngram);
                int index = _ngramToIndex.getIndex(hash);
                int[] counts = ngramCounts[index];
                if (counts == null) {
                    counts = new int[numLanguages];
                    ngramCounts[index] = counts;
                }
                
                counts[langIndex] = langCounts.get(ngram);
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
    
    private int setModels(Collection<LanguageModel> models) {
        _models = models;
        
        // Build a master map from language to index (0...n-1), which we'll use to index into
        // arrays associated with each ngram.
        
        _langToIndex = new HashMap<>(_models.size());
        int curIndex = 0;
        for (LanguageModel model : _models) {
            if (_langToIndex.put(model.getLanguage(), curIndex) != null) {
                throw new IllegalArgumentException("Got two models with the same language: " + model.getLanguage());
            }
            
            curIndex += 1;
        }
        
        return curIndex;
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
            int hash = Integer.parseInt(ngram);
            int index = _ngramToIndex.getIndex(hash);
                            
            if (index == -1) {
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
                int langIndex = langToIndex(language);
                double prob = _ngramProbabilities[index][langIndex];
                
                if ((details != null) && (prob != 0.0) && ((detailLanguages == null) || (detailLanguages.contains(language)))) {
                    details.append(String.format("\t'%s'=%f", language, prob));
                    details.append(detailLanguages == null ? '\n' : ' ');
                }
                
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

    public LanguageModel getModel(LanguageLocale language) {
        for (LanguageModel model : _models) {
            
            if (model.getLanguage().equals(language)) {
                return model;
            }
            
            // TODO if weakly equal, and no other set to weak, save it
        }
        
        throw new IllegalArgumentException("Unknown language: " + language);
    }

}
