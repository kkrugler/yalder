package com.scaleunlimited.yalder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

public class ModelBuilder {
    private static final Logger LOGGER = Logger.getLogger(ModelBuilder.class);

    public static final int DEFAULT_MAX_NGRAM_LENGTH = 4;
    
    public static final double DEFAULT_MIN_NGRAM_PROBABILITY = 0.38;

    public static final double DEFAULT_PROBABILITY_SCORE_POWER = 1.5;

    public static final int DEFAULT_NGRAMS_PER_LANGUAGE = 1000;

    public static final int DEFAULT_MIN_NORMALIZED_COUNT = (int)Math.round(0.0001 * LanguageModel.NORMALIZED_COUNT);
    
    private Map<String, Map<CharSequence, NGramStats>> _perLangNGramCounts;
    private int _ngramsPerLanguage = DEFAULT_NGRAMS_PER_LANGUAGE;
    private int _maxNGramLength = DEFAULT_MAX_NGRAM_LENGTH;
    private double _minNGramProbability = DEFAULT_MIN_NGRAM_PROBABILITY;
    private double _probabilityScorePower = DEFAULT_PROBABILITY_SCORE_POWER;
    private int _minNormalizedCount = DEFAULT_MIN_NORMALIZED_COUNT;
    
    public ModelBuilder() {
        _perLangNGramCounts = new HashMap<String, Map<CharSequence, NGramStats>>();
    }
    
    public ModelBuilder setMaxNGramLength(int maxNGramLength) {
        _maxNGramLength = maxNGramLength;
        return this;
    }
    
    public int getMaxNGramLength() {
        return _maxNGramLength;
    }
    
    public ModelBuilder setMinNGramProbability(double minNGramProbability) {
        _minNGramProbability = minNGramProbability;
        return this;
    }
    
    public ModelBuilder setProbabilityScorePower(double probabilityScorePower) {
        _probabilityScorePower = probabilityScorePower;
        return this;
    }
    
    public ModelBuilder setNGramsPerLanguage(int ngramsPerLanguage) {
        _ngramsPerLanguage = ngramsPerLanguage;
        return this;
    }
    
    // TODO make sure we haven't already made the languages.
    public void addTrainingDoc(String language, CharSequence text) {
        Map<CharSequence, NGramStats> ngramCounts = _perLangNGramCounts.get(language);
        if (ngramCounts == null) {
            ngramCounts = new HashMap<CharSequence, NGramStats>();
            _perLangNGramCounts.put(language, ngramCounts);
        }
        
        NGramTokenizer tokenizer = new NGramTokenizer(text, 1, _maxNGramLength);
        while (tokenizer.hasNext()) {
            CharSequence token = tokenizer.next();
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
        Set<String> languages = new HashSet<String>(_perLangNGramCounts.keySet());
        
        // First come up with combined counts of ngrams across all languages.
        // We also want to normalize counts, so that the amount of training data
        // per language isn't a factor.
        Map<CharSequence, NGramStats> allLangNGramCounts = new HashMap<CharSequence, NGramStats>();

        Map<String, Integer> perLanguageTotalNGramCount = new HashMap<String, Integer>();
        
        for (String language : languages) {
            int languageNGrams = 0;
            
            Map<CharSequence, NGramStats> ngramCounts = _perLangNGramCounts.get(language);
            for (CharSequence ngram : ngramCounts.keySet()) {
                int count = ngramCounts.get(ngram).getNGramCount();
                languageNGrams += count;
            }
            
            perLanguageTotalNGramCount.put(language,  languageNGrams);
        }

        
        for (String language : languages) {
            double datasizeNormalization = (double)LanguageModel.NORMALIZED_COUNT / (double)perLanguageTotalNGramCount.get(language);
            Map<CharSequence, NGramStats> ngramCounts = _perLangNGramCounts.get(language);
            for (CharSequence ngram : ngramCounts.keySet()) {
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

        // For each ngram, keep track of a score for every language where it exists.
        Map<String, List<NGramScore>> langNGramByScore = new HashMap<String, List<NGramScore>>();
        
        // Parallel map that lets us quickly find an NGram for a language, when we want to
        // see if it exists for another language.
        Map<String, Map<CharSequence, NGramScore>> langNGramByNGram = new HashMap<String, Map<CharSequence, NGramScore>>();
        
        // Map that tells us, for each language, which other language it's currently being
        // compared to.
        Map<String, String> langLangComparisons = new HashMap<String, String>();
        
        // Our end results.
        Map<String, List<NGramScore>> languageResults = new HashMap<String, List<NGramScore>>();
        
        for (String language : languages) {
            langNGramByScore.put(language, new LinkedList<NGramScore>());
            langNGramByNGram.put(language, new HashMap<CharSequence, NGramScore>());
            langLangComparisons.put(language, "");
            languageResults.put(language, new ArrayList<NGramScore>());
        }
        
        for (CharSequence ngram : allLangNGramCounts.keySet()) {
            double totalCount = (double)allLangNGramCounts.get(ngram).getNGramCount();

            for (String language : languages) {
                NGramStats langStatsForThisNGram = _perLangNGramCounts.get(language).get(ngram);
            
                if (langStatsForThisNGram != null) {
                    int count = langStatsForThisNGram.getNGramCount();
                    if (count == 0) {
                        // Ignore ngrams that we've flagged as having too low of a count
                        continue;
                    }
                    
                    double probability = count / totalCount;
                    double score = count * Math.pow(probability, _probabilityScorePower);
                    NGramScore lng = new NGramScore(ngram, count, score, probability);
                    
                    // Always add it to this language's set of known ngrams, as if we pick this ngram
                    // for another language, we want to add it to this language's set (versus it not
                    // existing, and thus getting a very low probability).
                    langNGramByNGram.get(language).put(ngram, lng);

                    // if the probability is too low, ignore it as we don't ever want
                    // to use it for this language.
                    if (probability < _minNGramProbability) {
                        continue;
                    }
                    
                    langNGramByScore.get(language).add(lng);
                }
            }
        }
        
        // Sort the results.
        for (String language : languages) {
            Collections.sort(langNGramByScore.get(language));
        }
        
        boolean done = false;
        int numLoops = 0;
        
        while (!done) {
            /*
            if ((numLoops % 100) == 0) {
                // Time to recalculate scores for each language's remaining ngrams, based on
                // whichever language is currently "closest" to this language.
                
                Map<String, String> closestLanguages = findClosestLanguage(languageResults);
                for (String language : languages) {
                    String closestLanguage = closestLanguages.get(language);
                    
                    rescoreNGrams(langNGramByScore, langNGramByNGram, language, closestLanguage);
                }
            }
            */
            
            numLoops += 1;
            
            // Round-robin style, find the best ngram (by score) for each language, and add it to
            // that language's list. Also add that ngram from any other language to those language's
            // lists, and remove that ngram from both lists. Keep going until every language has
            // at least our target number of ngrams.
            
            done = true;
            for (String language : languages) {
                List<NGramScore> ngrams = langNGramByScore.get(language);
                // This shoud
                if (!findBestNGram(ngrams)) {
                    continue;
                }
                
                NGramScore bestNGram = ngrams.get(0);
                languageResults.get(language).add(bestNGram);
                done = done && languageResults.get(language).size() >= _ngramsPerLanguage;
                
                // Remove the ngram from this langauge's sorted list, and from our set of ngrams.
                ngrams.remove(0);
                
                CharSequence ngram = bestNGram.getNGram();
                langNGramByNGram.get(language).remove(ngram);

                // Find and remove from other lists & sets.
                for (String otherLanguage : languages) {
                    if (otherLanguage.equals(language)) {
                        continue;
                    }
                    
                    if (!langNGramByNGram.get(otherLanguage).containsKey(ngram)) {
                        continue;
                    }
                    
                    
                    // Flag this other langauge's ngram as one that we shouldn't use.
                    // Later the findBestNGram call will remove it from the sorted list,
                    // if it's at the beginning.
                    NGramScore otherNGramScore = langNGramByNGram.get(otherLanguage).remove(ngram);
                    otherNGramScore.setScore(0.0);
                    
                    // Add this ngram to the other language's list Note that it's Ok that the score
                    // is set to 0.0, as we no longer care abou the score once it's in the list.
                    languageResults.get(otherLanguage).add(otherNGramScore);
                }
            }
        }
        
        List<LanguageModel> models = new ArrayList<LanguageModel>();
        for (String language : languages) {
            List<NGramScore> langResults = languageResults.get(language);
            Collections.sort(langResults);
            
            Map<CharSequence, Integer> normalizedCounts = new HashMap<CharSequence, Integer>();
            LOGGER.trace(String.format("Language = '%s'", language));
            for (int i = 0; i < Math.min(_ngramsPerLanguage, langResults.size()); i++) {
                NGramScore result = langResults.get(i);
                LOGGER.trace(String.format("\t'%s': %f (prob = %f)", result.getNGram(), result.getScore(), result.getProbability()));
                
                normalizedCounts.put(result.getNGram(), result.getCount());
            }
            
            // FUTURE support varying max ngram length per model.
            LanguageModel model = new LanguageModel(language, _maxNGramLength, normalizedCounts);
            models.add(model);
        }

        return models;
    }

    private boolean findBestNGram(List<NGramScore> ngrams) {
        Iterator<NGramScore> iter = ngrams.iterator();
        while (iter.hasNext()) {
          NGramScore ngramScore = iter.next();
          if (ngramScore.getScore() == 0.0) {
              iter.remove();
          } else {
              return true;
          }
        }

        return false;
    }

    /*
    private void rescoreNGrams(Map<String, List<NGramScore>> langNGramByScore, Map<String, Set<NGramScore>> langNGramByNGram, String language, String closestLanguage) {
        List<NGramScore> ngrams = langNGramByScore.get(language);
        
        for (NGramScore ngram : ngrams) {
            if (ngram.getCount() == 0) {
                // We've already used this ngram
                ngram.setScore(0.0);
            } else {
                NGramScore otherNGram = langNGramByNGram.get(closestLanguage).
            }
        }
        
        Collections.sort(ngrams);
    }
*/
    
    private Map<String, String> findClosestLanguage(Map<String, List<NGramScore>> languageResults) {
        Map<String, Map<String, Double>> probabilities = new HashMap<String, Map<String,Double>>();
        
        // We determine the closest language by multiplying probabilities for ngrams found
        // in both profiles.

        // 
        return null;
    }
    
    
}
