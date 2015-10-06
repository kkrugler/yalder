package org.krugler.yalder.tools;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.krugler.yalder.BaseLanguageModel;
import org.krugler.yalder.ModelBuilder;

public class EuroParlUtils {

    public static List<String> readLines() throws IOException {
        FileInputStream fis = new FileInputStream("src/test/resources/europarl.test");
        return IOUtils.readLines(fis, "UTF-8");
    }
    
    public static Collection<BaseLanguageModel> buildModels(List<String> lines, boolean isBinary) {
        ModelBuilder builder = new ModelBuilder().setBinaryMode(isBinary);
        
        for (String line : lines) {
            
            // Format is <language code><tab>text
            String[] pieces = line.split("\t", 2);
            String language = pieces[0];
            String text = pieces[1];

            builder.addTrainingDoc(language, text);
        }

        return builder.makeModels();
    }

}
