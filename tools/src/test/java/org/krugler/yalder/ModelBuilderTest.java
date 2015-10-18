package org.krugler.yalder;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.krugler.yalder.BaseLanguageModel;
import org.krugler.yalder.hash.HashLanguageModel;
import org.krugler.yalder.tools.EuroParlUtils;

public class ModelBuilderTest {

    @Test
    public void test() throws Exception {
        List<String> lines = EuroParlUtils.readLines();
        Collection<BaseLanguageModel> models = EuroParlUtils.buildModels(lines, true);
    }

}
