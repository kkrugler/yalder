package com.scaleunlimited.yalder.hash;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.scaleunlimited.yalder.BaseLanguageModel;
import com.scaleunlimited.yalder.LanguageLocale;


/**
 * Encapsulation of model about a given language. This consists of
 * a list of ngrams (stored as 4-byte hashes) and their normalized
 * counts.
 *
 */

public class HashLanguageModel extends BaseLanguageModel {

    // Map from ngram to count
    // TODO use native int int map
    private Map<Integer, Integer> _normalizedCounts;
    
    /**
     * No-arg construct for deserialization
     */
    public HashLanguageModel() {
        super();
    }
    
    public HashLanguageModel(LanguageLocale modelLanguage, int maxNGramLength, Map<Integer, Integer> normalizedCounts) {
        super(modelLanguage, maxNGramLength);
        _normalizedCounts = normalizedCounts;
    }
    
    @Override
    public int size() {
        return _normalizedCounts.size();
    }
    
    public int getNGramCount(String ngram) {
        Integer result = _normalizedCounts.get(ngram);
        return result == null ? 0 : result;
    }
    
    public Map<Integer, Integer> getNGramCounts() {
        return _normalizedCounts;
    }
    
    @Override
    public int prune(int minNormalizedCount) {
        Set<Integer> ngramsToPrune = new HashSet<>();
        for (Integer ngramHash : _normalizedCounts.keySet()) {
            if (_normalizedCounts.get(ngramHash) < minNormalizedCount) {
                ngramsToPrune.add(ngramHash);
            }
        }
        
        for (Integer ngramHash : ngramsToPrune) {
            _normalizedCounts.remove(ngramHash);
        }
        
        return ngramsToPrune.size();
    }
    
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((_normalizedCounts == null) ? 0 : _normalizedCounts.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        HashLanguageModel other = (HashLanguageModel) obj;
        if (_normalizedCounts == null) {
            if (other._normalizedCounts != null)
                return false;
        } else if (!_normalizedCounts.equals(other._normalizedCounts))
            return false;
        return true;
    }

    public void readAsBinary(DataInput in) throws IOException {
        int version = in.readInt();
        if (version != MODEL_VERSION) {
            throw new IllegalArgumentException("Version doesn't match supported values, got " + version);
        }
        
        _modelLanguage = LanguageLocale.fromString(in.readUTF());
        _maxNGramLength = in.readInt();
        
        int numNGrams = in.readInt();
        _normalizedCounts = new HashMap<>(numNGrams);
        for (int i = 0; i < numNGrams; i++) {
            int hash = in.readInt();
            int count = WritableUtils.readVInt(in);
            _normalizedCounts.put(hash, count);
        }
    }

    public void writeAsBinary(DataOutput out) throws IOException {
        out.writeInt(MODEL_VERSION);
        out.writeUTF(_modelLanguage.getName());
        out.writeInt(_maxNGramLength);
        out.writeInt(_normalizedCounts.size());;
        
        for (Integer ngramHash : _normalizedCounts.keySet()) {
            out.writeInt(ngramHash);
            WritableUtils.writeVInt(out, _normalizedCounts.get(ngramHash));
        }

    }
}
