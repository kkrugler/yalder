package com.scaleunlimited.yalder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.google.common.base.Optional;
import com.optimaize.langdetect.DetectedLanguage;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.text.CommonTextObjectFactories;
import com.optimaize.langdetect.text.TextObject;
import com.optimaize.langdetect.text.TextObjectFactory;

public class OtherDetectorsTest {

    private static final String[] TARGET_LANGUAGES = new String[] {
        // "bg",
        // "cs",
        "da",
        "de",
        "el",
        "en",
        "es",
        "et",
        "fi",
        "fr",
        "hu",
        "it",
        // "lt",
        // "lv",
        "nl",
        "pl",
        "pt",
        "ro",
        "sk",
        "sl",
        "sv"
    };
    
    private static final Set<String> SKIPPED_LANGUAGES = new HashSet<String>() {{
        add("bg");
        add("cs");
        add("lt");
        add("lv");
    }};
    
    @Test
    public void testLanguageDetectorPerformance() throws IOException {
        //load target languages:
        List<LanguageProfile> languageProfiles = new LanguageProfileReader().read(Arrays.asList(TARGET_LANGUAGES));

        //build language detector:
        com.optimaize.langdetect.LanguageDetector languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                .withProfiles(languageProfiles)
                .build();

        //create a text object factory
        TextObjectFactory textObjectFactory = CommonTextObjectFactories.forDetectingShortCleanText();
        // TextObjectFactory textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();

        List<String> lines = EuroParlUtils.readLines();

        // Do 10 runs, and take the fastest time.
        long bestDuration = Long.MAX_VALUE;
        int numHits = 0;
        int numMisses = 0;
        
        for (int i = 0; i < 10; i++) {
            long startTime = System.currentTimeMillis();
            for (String line : lines) {
                String[] pieces = line.split("\t", 2);
                String language = pieces[0];
                if (SKIPPED_LANGUAGES.contains(language)) {
                    continue;
                }
                
                TextObject textObject = textObjectFactory.forText(pieces[1]);
                List<DetectedLanguage> result = languageDetector.getProbabilities(textObject);
                if (result.size() > 0 && result.get(0).getLanguage().equals(language)) {
                    numHits += 1;
                } else {
                    numMisses += 1;
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            System.out.println(String.format("Run #%d duration = %dms", i + 1, duration));
            System.out.println(String.format("Run #%d error rate = %f%%", i + 1, 100.0 * (double)numMisses/(double)(numMisses + numHits)));
            bestDuration = Math.min(bestDuration, duration);
        }
        
        System.out.println(String.format("Best duration = %dms", bestDuration));
    }
}
