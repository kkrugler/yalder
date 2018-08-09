package org.krugler.yalder;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.krugler.yalder.hash.HashLanguageModel;
import org.krugler.yalder.text.TextLanguageModel;

public class ModelLoader {

    public static String resourceFromLanguage(LanguageLocale lang) {
        return String.format("/org/krugler/yalder/models/core/yalder_model_%s.bin", lang.getISO3LetterName());
    }
    
    public static Collection<BaseLanguageModel> loadModelsFromResources() throws IOException {
        Set<BaseLanguageModel> result = new HashSet<>();
        for (LanguageLocale lang : CoreModels.CORE_LANGUAGES) {
            String resource = resourceFromLanguage(lang);
            try (DataInputStream dis = new DataInputStream(ModelLoader.class.getResourceAsStream(resource))) {
                result.add(loadBinaryModel(lang, dis));
            } 
        }
        
        // TODO use reflection to see if extras jar is on classpath, add those languages.
        
        return result;
    }
    
    public static Collection<BaseLanguageModel> loadModelsFromDirectory(File dirFile, boolean isBinary) throws IOException {
        Set<BaseLanguageModel> result = new HashSet<>();
        
        String modelSuffix = isBinary ? "bin" : "txt";
        for (File file : FileUtils.listFiles(dirFile, new String[]{modelSuffix}, true)) {
            String filename = file.getName();
            Pattern p = Pattern.compile(String.format("yalder_model_(.+).%s", modelSuffix));
            Matcher m = p.matcher(filename);
            if (!m.matches()) {
                continue;
            }

            LanguageLocale ll = LanguageLocale.fromString(m.group(1));
            
            // Verify that language is valid
            
            
            if (isBinary) {
                try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
                    result.add(loadBinaryModel(ll, dis));
                } 
            } else {
                try (InputStreamReader isr = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
                    result.add(loadTextModel(ll, isr));
                }
            }
        }

        return result;
    }
    
    public static BaseLanguageModel loadBinaryModel(LanguageLocale locale, DataInputStream dis) throws IOException {
        HashLanguageModel binaryModel = new HashLanguageModel();
        binaryModel.readAsBinary(dis);

        if (!locale.equals(binaryModel.getLanguage())) {
            throw new IllegalArgumentException("Loaded model language doesn't match target of " + locale);
        }

        return binaryModel;
    }
    
    public static BaseLanguageModel loadTextModel(LanguageLocale locale, InputStreamReader isr) throws IOException {
        TextLanguageModel textModel = new TextLanguageModel();
        textModel.readAsText(isr);

        if (!locale.equals(textModel.getLanguage())) {
            throw new IllegalArgumentException("Loaded model language doesn't match target of " + locale);
        }

        return textModel;
    }

}
