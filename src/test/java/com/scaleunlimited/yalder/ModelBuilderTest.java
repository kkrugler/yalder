package com.scaleunlimited.yalder;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.List;

import org.junit.Test;

import com.scaleunlimited.yalder.hash.HashLanguageModel;

public class ModelBuilderTest {

    @Test
    public void test() throws Exception {
        List<String> lines = EuroParlUtils.readLines();
        Collection<BaseLanguageModel> models = EuroParlUtils.buildModels(lines, true);
    }

}
