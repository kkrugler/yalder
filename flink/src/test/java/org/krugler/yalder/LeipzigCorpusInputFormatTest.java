package org.krugler.yalder;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.core.fs.FileInputSplit;
import org.apache.flink.core.fs.Path;
import org.junit.Test;

public class LeipzigCorpusInputFormatTest {

    @Test
    public void test() throws IOException {
        File dirFile = new File("src/test/resources/leipzig/");
        LeipzigCorpusInputFormat inputFormat = new LeipzigCorpusInputFormat(new Path(dirFile.getAbsolutePath()));
        
        readOneFile(inputFormat, "eng_news_2015_100-sentences.txt", "eng", 100);
        readOneFile(inputFormat, "afr-za_web_2015_100-sentences.txt", "afr", 100);
    }

    private void readOneFile(LeipzigCorpusInputFormat inputFormat, String filename, String language, int expectedLines) throws IOException {
        File f = new File("src/test/resources/leipzig/" + filename);
        FileInputSplit split = new FileInputSplit(0, new Path(f.getAbsolutePath()), 0, f.length(), new String[0]);
        inputFormat.open(split);
        
        Tuple2<String, String> record = new Tuple2<>();
        
        int numLines = 0;
        while (inputFormat.nextRecord(record) != null) {
            numLines++;
            assertEquals(language, record.f0);
            if (numLines == 1) {
                // Sometimes first line in file is empty, so do looser check
                assertNotNull(record.f1);
            } else {
                assertFalse(record.f1.isEmpty());
            }
        }
        
        inputFormat.close();
        assertEquals(expectedLines, numLines);
    }

}
