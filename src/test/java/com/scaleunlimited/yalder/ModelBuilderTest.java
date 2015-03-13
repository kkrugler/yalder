package com.scaleunlimited.yalder;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.List;

import org.junit.Test;

import com.scaleunlimited.yalder.cur.LanguageModel;

public class ModelBuilderTest {

    @Test
    public void testFasterVsRegularNGramVector() throws Exception {
        List<String> lines = EuroParlUtils.readLines();
        Collection<LanguageModel> models = EuroParlUtils.buildModels(lines);

        for (LanguageModel model : models) {
            if (!model.isPairwise() && model.getLanguage().equals("en")) {
                BaseNGramVector vector = model.getVector();
                System.out.println(vector.toString());
            }
        }
    }

}
