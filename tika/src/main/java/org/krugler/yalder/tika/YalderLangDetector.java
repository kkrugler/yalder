package org.krugler.yalder.tika;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.language.detect.LanguageConfidence;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.krugler.yalder.BaseLanguageModel;
import org.krugler.yalder.DetectionResult;
import org.krugler.yalder.LanguageLocale;
import org.krugler.yalder.ModelLoader;
import org.krugler.yalder.hash.HashLanguageDetector;

/**
 * Implementation of the LanguageDetector API that uses
 * https://github.com/kkrugler/yalder
 */
public class YalderLangDetector extends LanguageDetector {

    private HashLanguageDetector detector;
    Collection<BaseLanguageModel> models;
    private Map<LanguageLocale, Float> languageProbabilities;
    private boolean hasText = false;
    
    public YalderLangDetector() {
        super();
    }

    @Override
    public LanguageDetector loadModels() throws IOException {
        models = ModelLoader.loadModelsFromResources();
        detector = new HashLanguageDetector(models);

        return this;
    }

    @Override
    public LanguageDetector loadModels(Set<String> languages) throws IOException {
        Set<LanguageLocale> ll = new HashSet<>();
        for (String language : languages) {
            ll.add(LanguageLocale.fromString(language));
        }
        
        models = ModelLoader.loadModelsFromResources(ll);
        detector = new HashLanguageDetector(models);

        return this;
    }

    @Override
    public void addText(char[] cbuf, int off, int len) {
        if (models == null) {
            throw new IllegalStateException("Models must be loaded before calling addText()");
        }
        
        if (len > 0) {
            hasText = true;
            detector.addText(cbuf, off, len);
        }
    }

    @Override
    public List<LanguageResult> detectAll() {
        if (!hasText) {
            throw new IllegalStateException("Text must be added before calling detectAll()");
        }
        
        List<LanguageResult> result = new ArrayList<>();
        for (DetectionResult dr : detector.detect()) {
            LanguageConfidence confidence = mapConfidenceScoreToEnum(dr.getConfidence());
            result.add(new LanguageResult(dr.getLanguage().getISO2LetterName(), confidence, (float)dr.getScore()));
        }
        
        return result;
    }

    private LanguageConfidence mapConfidenceScoreToEnum(double confidence) {
        if (confidence > 0.9) {
            return LanguageConfidence.HIGH;
        } else if (confidence > 0.7) {
            return LanguageConfidence.MEDIUM;
        } else if (confidence > 0.0) {
            return LanguageConfidence.LOW;
        } else {
            return LanguageConfidence.NONE;
        }
    }

    @Override
    public boolean hasModel(String language) {
        LanguageLocale lang = LanguageLocale.fromString(language);
        String resource = ModelLoader.resourceFromLanguage(lang);
        // TODO - any better way to detect presence of the resource?
        try (InputStream is = YalderLangDetector.class.getResourceAsStream(resource)) {
            return is != null;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void reset() {
        detector.reset();
    }

    @Override
    public LanguageDetector setPriors(Map<String, Float> languageProbabilities) throws IOException {
        // TODO copy these into map from Yalder language to double.
        // Then use it to initialize language probabilities.
        return this;
    }
    
    @Override
    public boolean hasEnoughText() {
        // TODO call detector to find out
        return super.hasEnoughText();
    }
}
