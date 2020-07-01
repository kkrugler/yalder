package org.krugler.yalder;

public class WorkingDir {

    // Sub-directory containing files with language|ngram|count results.
    public static final String LANG_NGRAM_COUNT_DIRNAME = "lnc";
    
    // File containing block id|language pairs that can be collapsed by tokenizer
    public static final String SINGLE_LANGUAGE_BLOCKS_FILENAME = "blocks.txt";
    
    // Sub-directory containing files with language|count results.
    public static final String LANG_COUNT_DIRNAME = "lc";
    
    // Sub-directory containing files with language models.
    public static final String MODELS_DIRNAME = "models";
    
    public static final String TEXT_MODEL_FILENAME_PATTERN = "yalder_model_%s.txt";

}
