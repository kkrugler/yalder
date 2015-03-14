package com.scaleunlimited.yalder;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class VectorStatsTool {

    public static void main(String[] args) {
        try {
            List<String> lines = EuroParlUtils.readLines();
            Collection<LanguageModel> models = EuroParlUtils.buildModels(lines);

            PrintWriter pw = new PrintWriter(new File(args[0]), "UTF-8");
            
            for (LanguageModel model : models) {
                String language = model.getLanguage();
                String pairwiseLanguage = model.getPairwiseLanguage();
                if (pairwiseLanguage == null) {
                    pairwiseLanguage = "";
                }

                BaseNGramVector vector = model.getVector();
                Iterator<Integer> iter = vector.getIterator();
                while (iter.hasNext()) {
                    pw.println(String.format("%s/%s\t%d", language, pairwiseLanguage, iter.next()));
                }
                
                pw.flush();
            }
            
            pw.close();
        } catch (Exception e) {
            System.err.println("Error running tool: " + e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
