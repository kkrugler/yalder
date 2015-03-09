package com.scaleunlimited.yalder;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.scaleunlimited.yalder.DetectionResult;
import com.scaleunlimited.yalder.LanguageModel;

public class LanguageModelTest {

    /**
     * Print out languages that are "close", where I've arbitrarily defined this
     * to be a score >= 0.30. We get 5 such combinations with our current approach,
     * specifically de/nl, sk/cs, da/sv/, it/es, and pt/es.
     */
    @Test
    public void testClosestModels() throws Exception {
        List<String> lines = EuroParlUtils.readLines();
        Collection<LanguageModel> models = EuroParlUtils.buildModels(lines);
        
        for (LanguageModel model : models) {
            if (model.isPairwise()) {
                continue;
            }
            
            String language = model.getLanguage();
            List<DetectionResult> results = new ArrayList<DetectionResult>();
            for (LanguageModel otherModel: models) {
                if (otherModel.isPairwise()) {
                    continue;
                }

                double score = model.compare(otherModel);
                results.add(new DetectionResult(otherModel.getLanguage(), score));
            }
            
            Collections.sort(results);
            
            System.out.println(String.format("Results for '%s':", language));
            for (DetectionResult result : results) {
                double score = result.getScore();
                if (!language.equals(result.getLanguage()) && (score >= 0.30)) {
                    System.out.println(String.format("\t'%s': %f", result.getLanguage(), result.getScore()));
                }
            }
            
            System.out.println();
        }
    }

}
