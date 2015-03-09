package com.scaleunlimited.yalder;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.RuntimeErrorException;

import org.apache.log4j.Logger;
import org.apache.mahout.math.RandomAccessSparseVector;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.stats.LogLikelihood;

public class ModelBuilder {
    private static final Logger LOGGER = Logger.getLogger(ModelBuilder.class);

    public static final int MIN_NGRAM_LENGTH = 1;
    public static final int MAX_NGRAM_LENGTH = 4;
    
    // Percentage of documents that must contain the ngram
    // private static final double MIN_DF = 0.01;
    private static final double MIN_DF = 0.05;

    // Minimum LLR values for various ngram lengths
    private static final double MIN_LLR[] = {
        0.0,    // no ngrams of length 0
        40.0,   // length 1
        20.0,   // length 2
        10.0,    // length 3
        5.0    // length 4
    };
    
    private static final double NGRAM_SCALARS[] = {
        0.0,    // no ngrams of length 0
        1.0,
        2.0,
        3.0,
        4.0
    };
    
    // TODO everything here needs to be based on HashTokenizer????
    // Otherwise we need to keep NGramTokenizer in sync (which includes normalization)
    private Map<String, Map<CharSequence, NGramStats>> _langStats;
    private Map<String, Integer> _docsPerLanguage;
    
    public ModelBuilder() {
        _langStats = new HashMap<String, Map<CharSequence, NGramStats>>();
        _docsPerLanguage = new HashMap<String, Integer>();

    }
    
    public void addTrainingDoc(String language, CharSequence text) {
        Map<CharSequence, NGramStats> docStats = CharUtils.calcNGramStats(text, MIN_NGRAM_LENGTH, MAX_NGRAM_LENGTH);
        mergeStats(language, docStats);

        Integer curDocsForLanguage = _docsPerLanguage.get(language);
        if (curDocsForLanguage == null) {
            _docsPerLanguage.put(language, 1);
        } else {
            _docsPerLanguage.put(language, curDocsForLanguage + 1);
        }
    }
    
    public Collection<LanguageModel> makeModels() {
        try {
            return makeModels(null);
        } catch (IOException e) {
            throw new RuntimeException("Impossible exception", e);
        }
    }
    
    public Collection<LanguageModel> makeModels(OutputStreamWriter osw) throws IOException {
        
        // First build the language vectors for all languages we've got.
        Map<String, NGramVector> langVectors = makeVectors(_langStats.keySet(), osw);
        
        List<LanguageModel> models = new ArrayList<LanguageModel>(langVectors.size());
        for (String language : langVectors.keySet()) {
            models.add(new LanguageModel(language, null, langVectors.get(language)));
        }
        
        // For languages that wind up with vectors which are "close" to each other, create special
        // training data that can be used to disambiguate. So then if our top 2 languages are very close
        // (low confidence) and we have specific models for those two against each other, do a "tie breaker"
        // E.g. top two results are es and pt, but they're close so we have low confidence. If we have
        // an es/pt model and a pt/es model (should always be a pair) then re-calc scores using those two
        // models and see if the order (and confidence) changes.
        Set<String> pairWiseLanguages = new HashSet<String>();
        
        for (LanguageModel model : models) {
            String language = model.getLanguage();
            List<DetectionResult> results = new ArrayList<DetectionResult>();
            for (LanguageModel otherModel: models) {
                double score = model.compare(otherModel);
                results.add(new DetectionResult(otherModel.getLanguage(), score));
            }
            
            Collections.sort(results);
            
            for (DetectionResult result : results) {
                double score = result.getScore();
                String otherLanguage = result.getLanguage();
                if (!language.equals(otherLanguage) && (score >= 0.30)) {
                    // Order these so that the "smaller" language (sort order) is first in our key.
                    String pairWiseKey = null;
                    if (language.compareTo(otherLanguage) < 0) {
                        pairWiseKey = language + "\t" + otherLanguage;
                    } else {
                        pairWiseKey = otherLanguage + "\t" + language;
                    }
                    pairWiseLanguages.add(pairWiseKey);
                }
            }
        }

        // Now we have a set of pair-wise languages to process
        for (String pairWiseKey : pairWiseLanguages) {
            String[] languages = pairWiseKey.split("\t");
            Set<String> languageSet = new HashSet<String>();
            languageSet.add(languages[0]);
            languageSet.add(languages[1]);
            Map<String, NGramVector> pairwiseVectors = makeVectors(languageSet, 0.3, osw);
            models.add(new LanguageModel(languages[0], languages[1], pairwiseVectors.get(languages[0])));
            models.add(new LanguageModel(languages[1], languages[0], pairwiseVectors.get(languages[1])));
        }

        return models;
    }
    
    private Map<String, NGramVector> makeVectors(Set<String> languages, OutputStreamWriter osw) throws IOException {
        return makeVectors(languages, 1.0, osw);
    }
    
    private Map<String, NGramVector> makeVectors(Set<String> languages, double thresholdScalar, OutputStreamWriter osw) throws IOException {
        // For each language, we now know the count for each ngram, and the doc count.
        // We want to calculate the LLR score. We need to combine all of the ngram counts
        // across languages so we know the global count for an ngram (and the total global
        // count for all ngrams), and we need to know how many ngrams we've got for each
        // language as well.

        int totalNGrams = 0;
        Map<String, Integer> ngramsPerLanguage = new HashMap<String, Integer>();
        Map<CharSequence, Integer> ngramsGlobally = new HashMap<CharSequence, Integer>();
        for (String language : languages) {
            int ngramsForCurLanguage = 0;
            Map<CharSequence, NGramStats> stats = _langStats.get(language);
            for (CharSequence ngram : stats.keySet()) {
                int ngramCount = stats.get(ngram).getNGramCount();
                totalNGrams += ngramCount;
                ngramsForCurLanguage += ngramCount;

                Integer globalNGramCount = ngramsGlobally.get(ngram);
                if (globalNGramCount == null) {
                    ngramsGlobally.put(ngram, ngramCount);
                } else {
                    ngramsGlobally.put(ngram, globalNGramCount + ngramCount);
                }
            }

            ngramsPerLanguage.put(language, ngramsForCurLanguage);
        }

        // Now for each language/ngram we can calculate both the doc frequency
        // and the LLR score.
        Map<String, NGramVector> langVectors = new HashMap<String, NGramVector>();
        
        Set<CharSequence> ngrams = new HashSet<CharSequence>();
        
        for (String language : languages) {
            NGramVector vector = new NGramVector();
            
            int ngramsForThisLanguage = ngramsPerLanguage.get(language);
            int docsForThisLanguage = _docsPerLanguage.get(language);

            Map<CharSequence, NGramStats> stats = _langStats.get(language);
            for (CharSequence ngram : stats.keySet()) {
                // k11 is the count of this ngram for this language
                int k11 = stats.get(ngram).getNGramCount();

                // k12 is the count of this ngram for all other languages.
                int k12 = ngramsGlobally.get(ngram) - k11;

                // k21 is the count of other ngrams for this language
                int k21 = ngramsForThisLanguage - k11;

                // k22 is the count of other ngrams for other languages
                int k22 = totalNGrams - ngramsForThisLanguage;

                double llrScore = LogLikelihood.rootLogLikelihoodRatio(k11, k12, k21, k22);

                int docsWithThisNGram = stats.get(ngram).getDocCount();
                double df = (double)docsWithThisNGram / (double)docsForThisLanguage;

                // Now only output the result if DF and LLR are "interesting.
                if ((df < MIN_DF * thresholdScalar) || (llrScore < MIN_LLR[ngram.length()] * thresholdScalar)) {
                    continue;
                }
                
                // Add this ngram to our vector.
                int hash = NGramVector.calcHash(ngram);
                if (vector.get(hash) != 0.0) {
                    LOGGER.info(String.format("Hash collision for '%s'", ngram));
                }
                
                vector.set(hash);
                
                // Update the set of ngrams of interest.
                ngrams.add(ngram);
                
                // Output language, ngram, count, doc frequency and LLR score
                if (osw != null) {
                    osw.write(String.format("%s\t'%s'\t%d\t%f\t%f\t%d\t%d\t%d\t%d\n", language, ngram, k11, df, llrScore, k11, k12, k21, k22));
                }
            }
            
            // Save off the vector we created.
            langVectors.put(language, vector);
            LOGGER.info(String.format("Vector for '%s' has %d elements", language, vector.size()));
        }
        
        return langVectors;
    }

    private void mergeStats(String language, Map<CharSequence, NGramStats> docStats) {
        Map<CharSequence, NGramStats> curLangStats = _langStats.get(language);
        if (curLangStats == null) {
            curLangStats = new HashMap<CharSequence, NGramStats>();
            _langStats.put(language, curLangStats);
        }
        
        // merge docStats into curLangStats.
        for (CharSequence ngram : docStats.keySet()) {
            NGramStats curNGramStats = curLangStats.get(ngram);
            if (curNGramStats == null) {
                curNGramStats = new NGramStats();
                curLangStats.put(ngram, curNGramStats);
            }
            
            curNGramStats.incDocCount();
            curNGramStats.incNGramCount(docStats.get(ngram).getNGramCount());
        }
    }

}
