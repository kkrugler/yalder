package org.krugler.yalder.text;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.krugler.yalder.BaseLanguageModel;
import org.krugler.yalder.DetectionResult;
import org.krugler.yalder.EuroParlUtils;
import org.krugler.yalder.LanguageLocale;
import org.krugler.yalder.ModelLoader;

public class TextLanguageDetectorTest {

    @Test
    public void testAllEuroparl() throws IOException {
        File modelsDir = new File("src/test/resources/org/krugler/yalder/models/text/");
        Collection<BaseLanguageModel> models = ModelLoader.loadModelsFromDirectory(modelsDir, false);
        
        TextLanguageDetector detector = new TextLanguageDetector(models);
        detector.setSegmented(true);
        detector.setRenormalizeInterval(100);
        
        List<String> lines = EuroParlUtils.readLines();

        int numHits = 0;
        int numMisses = 0;

        for (String line : lines) {
            String[] pieces = line.split("\t", 2);
            LanguageLocale ll = LanguageLocale.fromString(pieces[0]);
            String text = pieces[1];
            
            detector.reset();
            detector.addText(text);
            Collection<DetectionResult> result = detector.detect();
            if (result.size() > 0 && result.iterator().next().getLanguage().weaklyEqual(ll)) {
                numHits += 1;
            } else {
                numMisses += 1;
                if (result.size() == 0) {
                    System.out.format("Error, no identification for \"%s\"\n", text);
                } else {
                    LanguageLocale resultingLL = result.iterator().next().getLanguage();
                    System.out.format("Error, identified as '%s', was '%s' for \"%s\"\n", resultingLL.getISO3LetterName(), ll.getISO3LetterName(), text);
                }
            }
        }
        
        System.out.println(String.format("Error rate = %f%%", 100.0 * (double)numMisses/(double)(numMisses + numHits)));
    }

    @Test
    public void testProblemStrings() throws IOException {
        File modelsDir = new File("src/test/resources/org/krugler/yalder/models/text/");
        Collection<BaseLanguageModel> models = ModelLoader.loadModelsFromDirectory(modelsDir, false);
        
        TextLanguageDetector detector = new TextLanguageDetector(models);
        detector.setSegmented(true);
        detector.setRenormalizeInterval(20);
        detector.setDampening(0.05);
        
        final char[] confusedPolAsEng = "Regiony te to belgijski region Limburg, holenderski region Limburg i region Aachen.".toCharArray();
        StringBuilder details = new StringBuilder();
        
        detector.reset();
        detector.addText(confusedPolAsEng, 0, confusedPolAsEng.length, details, null);
        System.out.println(details);
    }

}
