package org.krugler.yalder.text;

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
import org.krugler.yalder.BaseLanguageModel;
import org.krugler.yalder.LanguageLocale;


/**
 * Encapsulation of model about a given language. This consists of
 * a list of ngrams, with a (normalized) count for each ngram.
 * 
 * @author Ken Krugler
 *
 */

public class TextLanguageModel extends BaseLanguageModel {

    // Constants used when writing/reading the model.
    private static final String VERSION_PREFIX = "version:";
    private static final String LANGUAGE_PREFIX = "language:";
    private static final String NGRAM_SIZE_PREFIX = "max ngram length:";
    private static final String NGRAM_DATA_PREFIX = "ngrams:";

    // Map from ngram to count
    private Map<String, Integer> _normalizedCounts;
    
    /**
     * No-arg construct for deserialization
     */
    public TextLanguageModel() {
        super();
    }
    
    public TextLanguageModel(LanguageLocale modelLanguage, int maxNGramLength, Map<String, Integer> normalizedCounts) {
        super(modelLanguage, maxNGramLength);
        
        _normalizedCounts = normalizedCounts;
    }
    
    public int size() {
        return _normalizedCounts.size();
    }
    
    public int getNGramCount(String ngram) {
        Integer result = _normalizedCounts.get(ngram);
        return result == null ? 0 : result;
    }
    
    public Map<String, Integer> getNGramCounts() {
        return _normalizedCounts;
    }
    
    @Override
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
        TextLanguageModel other = (TextLanguageModel) obj;
        if (_normalizedCounts == null) {
            if (other._normalizedCounts != null)
                return false;
        } else if (!_normalizedCounts.equals(other._normalizedCounts))
            return false;
        return true;
    }

    public void writeAsText(OutputStreamWriter osw) throws IOException {
        osw.write(String.format("%s %d\n", VERSION_PREFIX, MODEL_VERSION));
        osw.write(String.format("%s %s\n", LANGUAGE_PREFIX, _modelLanguage.getName()));
        osw.write(String.format("%s %d\n", NGRAM_SIZE_PREFIX, _maxNGramLength));
        osw.write(String.format("%s\n", NGRAM_DATA_PREFIX));
        
        for (String ngram : _normalizedCounts.keySet()) {
            osw.write(String.format("\t%s: %d\n", ngram, _normalizedCounts.get(ngram)));
        }
    }
    
    public void readAsText(InputStreamReader isw) throws IOException {
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
