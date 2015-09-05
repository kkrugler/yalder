package com.scaleunlimited.yalder;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NGramScores {

    // For each language, we have a sorted list of scored ngrams. These scores have been
    // calculated relative to the _langOtherLanguage setting.
    private Map<String, List<NGramScore>> _langNGramByScore;
    
    // For each langug
    // TODO make the map elements be a Map<CharSequence, Integer> that tells us where in
    // the langNGramsByScore list this ngram is located...which means we can't remove it from
    // the other language list, but we can flag it somehow has being used already (e.g. score
    // set to 0, so then don't use).
    Map<String, Set<CharSequence>> langNGramByNGram = new HashMap<String, Set<CharSequence>>();

    public NGramScores() {
        _langNGramByScore = new HashMap<String, List<NGramScore>>();
    }
    
    private static class SortedNGramScores {
        
        private String _otherLanguage;
        private List<NGramScore> _ngrams;
        
        public int size() {
            return _ngrams.size();
        }

        public boolean isEmpty() {
            return _ngrams.isEmpty();
        }

        public Iterator<NGramScore> iterator() {
            return _ngrams.iterator();
        }

        public boolean add(NGramScore e) {
            return _ngrams.add(e);
        }

        public void clear() {
            _ngrams.clear();
        }

        public NGramScore get(int index) {
            return _ngrams.get(index);
        }
        
        
    }
}
