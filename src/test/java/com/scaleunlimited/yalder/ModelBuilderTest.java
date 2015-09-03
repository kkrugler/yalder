package com.scaleunlimited.yalder;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.List;

import org.junit.Test;

public class ModelBuilderTest {

    @Test
    public void test() throws Exception {
        List<String> lines = EuroParlUtils.readLines();
        Collection<LanguageModel> models = EuroParlUtils.buildModels(lines, 1000);
    }

}
