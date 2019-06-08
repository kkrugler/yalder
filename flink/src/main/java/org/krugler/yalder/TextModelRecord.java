package org.krugler.yalder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

import org.krugler.yalder.text.TextLanguageModel;

@SuppressWarnings("serial")
public class TextModelRecord implements Serializable, DOSerializable {

    private String _lang;
    private String _ngram;
    private int _count;
    
    public TextModelRecord(String lang, String ngram, int count) {
        _lang = lang;
        _ngram = ngram;
        _count = count;
    }

    public String getLang() {
        return _lang;
    }

    public void setLang(String lang) {
        _lang = lang;
    }

    public String getNgram() {
        return _ngram;
    }

    public void setNgram(String ngram) {
        _ngram = ngram;
    }

    public int getCount() {
        return _count;
    }

    public void setCount(int count) {
        _count = count;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.write(TextLanguageModel.getNgramString(_ngram, _count).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void read(DataInput in) throws IOException {
        throw new RuntimeException("Can't use TextModelRecord for reading from files");
    }

}
