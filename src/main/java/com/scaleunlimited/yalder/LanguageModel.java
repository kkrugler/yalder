package com.scaleunlimited.yalder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;


/**
 * Encapsulation of model about a given language. This consists of
 * a list of ngrams (stored as 4-byte ints) and probabilities (floats).
 * The probability for an ngram is for it being that language, versus
 * all of the other known languages - so this has to be adjusted such
 * that the probabilities sum to 1.0 for the set of loaded languages.
 * 
 * Each ngram int is a right-flush (towards LSB) value, representing
 * packed character codes. So we can have a single character (e.g. 'a'
 * is stored as 0x00000061), or four characters (e.g. 'abcd' is stored
 * as '0x61626364'), or a single character that requires two bytes
 * (e.g. Hiragana 'a' is stored as 0x00003040), or any mix of one and
 * two byte values that fits into four bytes.
 * 
 * @author Ken Krugler
 *
 */

public class LanguageModel {

    // Version of model, for de-serialization
    public static final int MODEL_VERSION = 1;
    
    // The normalized counts are relative to this count, so that we
    // can combine languages built with different amounts of data.
    public static final int NORMALIZED_COUNT = 1000000;

    private static final String VERSION_PREFIX = "version:";
    private static final String LANGUAGE_PREFIX = "language:";
    private static final String NGRAM_SIZE_PREFIX = "max ngram length:";
    private static final String NGRAM_DATA_PREFIX = "ngrams:";

    private LanguageLocale _modelLanguage;
    
    private int _maxNGramLength;
    
    // Map from ngram to count
    private Map<String, Integer> _normalizedCounts;
    
    public LanguageModel() {
        // No-arg construct for deserialization
    }
    
    public LanguageModel(LanguageLocale modelLanguage, int maxNGramLength, Map<String, Integer> normalizedCounts) {
        _modelLanguage = modelLanguage;
        _maxNGramLength = maxNGramLength;
        _normalizedCounts = normalizedCounts;
    }
    
    public LanguageLocale getLanguage() {
        return _modelLanguage;
    }
    
    public int getMaxNGramLength() {
        return _maxNGramLength;
    }
    
    public int getNGramCount(String ngram) {
        Integer result = _normalizedCounts.get(ngram);
        return result == null ? 0 : result;
    }
    
    public Map<String, Integer> getNGramCounts() {
        return _normalizedCounts;
    }
    
    public int prune(int minNormalizedCount) {
        Set<String> ngramsToPrune = new HashSet<>();
        for (String ngram : _normalizedCounts.keySet()) {
            if (_normalizedCounts.get(ngram) < minNormalizedCount) {
                ngramsToPrune.add(ngram);
            }
        }
        
        for (String ngram : ngramsToPrune) {
            _normalizedCounts.remove(ngram);
        }
        
        return ngramsToPrune.size();
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + _maxNGramLength;
        result = prime * result + ((_modelLanguage == null) ? 0 : _modelLanguage.hashCode());
        result = prime * result + ((_normalizedCounts == null) ? 0 : _normalizedCounts.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        LanguageModel other = (LanguageModel) obj;
        if (_maxNGramLength != other._maxNGramLength)
            return false;
        if (_modelLanguage == null) {
            if (other._modelLanguage != null)
                return false;
        } else if (!_modelLanguage.equals(other._modelLanguage))
            return false;
        if (_normalizedCounts == null) {
            if (other._normalizedCounts != null)
                return false;
        } else if (!_normalizedCounts.equals(other._normalizedCounts))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return String.format("'%s': %d ngrams", _modelLanguage, _normalizedCounts.size());
    }

    public void writeModel(OutputStreamWriter osw) throws IOException {
        osw.write(String.format("%s %d\n", VERSION_PREFIX, MODEL_VERSION));
        osw.write(String.format("%s %s\n", LANGUAGE_PREFIX, _modelLanguage.getName()));
        osw.write(String.format("%s %d\n", NGRAM_SIZE_PREFIX, _maxNGramLength));
        osw.write(String.format("%s\n", NGRAM_DATA_PREFIX));
        
        for (String ngram : _normalizedCounts.keySet()) {
            osw.write(String.format("\t%s: %d\n", ngram, _normalizedCounts.get(ngram)));
        }
    }
    
    public void readModel(InputStreamReader isw) throws IOException {
        List<String> lines = IOUtils.readLines(isw);
        if (lines.size() < 5) {
            throw new IllegalArgumentException("Model doesn't contain enough lines of text");
        }
        
        // First line must be the version
        String versionLine = lines.get(0);
        if (!versionLine.startsWith(VERSION_PREFIX)) {
            throw new IllegalArgumentException("First line of model must be the version number");
        }
        
        versionLine = versionLine.substring(VERSION_PREFIX.length()).trim();
        int version = Integer.parseInt(versionLine);
        if (version != MODEL_VERSION) {
            throw new IllegalArgumentException("Version doesn't match supported values, got " + version);
        }
        
        // Next line must be language info.
        String languageLine = lines.get(1);
        if (!languageLine.startsWith(LANGUAGE_PREFIX)) {
            throw new IllegalArgumentException("Second line of model must be the language info");
        }
        
        languageLine = languageLine.substring(LANGUAGE_PREFIX.length()).trim();
        _modelLanguage = LanguageLocale.fromString(languageLine);
        
        // Next line is the ngram max length
        String ngramSizeLine = lines.get(2);
        if (!ngramSizeLine.startsWith(NGRAM_SIZE_PREFIX)) {
            throw new IllegalArgumentException("Third line of model must be the max ngram length");
        }
        
        ngramSizeLine = ngramSizeLine.substring(NGRAM_SIZE_PREFIX.length()).trim();
        _maxNGramLength = Integer.parseInt(ngramSizeLine);

        // Next line is the ngram header
        String ngramsLine = lines.get(3);
        if (!ngramsLine.equals(NGRAM_DATA_PREFIX)) {
            throw new IllegalArgumentException("Fourth line of model must be the ngram data header");
        }
        
        Pattern p = Pattern.compile("\t(.+?): (.+)");
        Matcher m = p.matcher("");
        
        _normalizedCounts = new HashMap<String, Integer>(lines.size() - 4);
        for (int i = 4; i < lines.size(); i++) {
            String ngramLine = lines.get(i);
            m.reset(ngramLine);
            if (!m.matches()) {
                throw new IllegalArgumentException(String.format("%d ngram in model has invalid format", i - 3));
            }
            
            String ngram = m.group(1);
            int normalizedCount = Integer.parseInt(m.group(2));
            _normalizedCounts.put(ngram, normalizedCount);
        }
    }
}
