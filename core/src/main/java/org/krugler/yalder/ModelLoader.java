package org.krugler.yalder;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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

    public static Collection<BaseLanguageModel> loadModelsFromResources() {
        // TODO use language-detector code to find/load resources
        return null;
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

            // Verify that language is valid
            LanguageLocale.fromString(m.group(1));
            
            if (isBinary) {
                try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
                    HashLanguageModel binaryModel = new HashLanguageModel();
                    binaryModel.readAsBinary(dis);
                    result.add(binaryModel);
                } 
            } else {
                try (InputStreamReader isr = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
                    TextLanguageModel textModel = new TextLanguageModel();
                    textModel.readAsText(isr);
                    result.add(textModel);
                }
            }
        }

        return result;
    }
}
