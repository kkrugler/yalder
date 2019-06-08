package org.krugler.yalder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.flink.api.common.io.FileOutputFormat.OutputDirectoryMode;
import org.apache.flink.api.common.io.OutputFormat;
import org.apache.flink.api.java.io.TextOutputFormat;
import org.apache.flink.core.fs.FileSystem.WriteMode;
import org.apache.flink.core.fs.Path;
import org.apache.flink.core.memory.DataOutputViewStreamWrapper;
import org.krugler.yalder.text.TextLanguageModel;


@SuppressWarnings("serial")
public class TextModelOutputFormatFactory implements OutputFormatFactory<TextModelRecord> {

    private Path _basePath;
    
    public TextModelOutputFormatFactory(Path basePath) {
        _basePath = basePath;
    }
    
    @Override
    public OutputFormat<TextModelRecord> makeOutputFormat(String bucket) {
        String extension = WorkingDir.TEXT_MODEL_FILENAME_PATTERN.replaceAll("%s", bucket);
        TextModelOutputFormat result = new TextModelOutputFormat(new Path(_basePath, extension), bucket);
        result.setOutputDirectoryMode(OutputDirectoryMode.PARONLY);
        result.setWriteMode(WriteMode.OVERWRITE);
        return result;
    }
    
    private static class TextModelOutputFormat extends TextOutputFormat<TextModelRecord> {

        private String _lang;
        
        private transient boolean _firstRecord;
        private transient DataOutputViewStreamWrapper _wrapper;
        
        public TextModelOutputFormat(Path outputPath, String lang) {
            super(outputPath);
            
            _lang = lang;
        }

        @Override
        public void open(int taskNumber, int numTasks) throws IOException {
            super.open(taskNumber, numTasks);
            
            _firstRecord = true;
            _wrapper = new DataOutputViewStreamWrapper(this.stream);
        }
        
        @Override
        public void close() throws IOException {
            _wrapper.close();
        }
        
        @Override
        public void writeRecord(TextModelRecord in) throws IOException {
            if (_firstRecord) {
                _firstRecord = false;
                _wrapper.write(TextLanguageModel.getVersionString().getBytes(StandardCharsets.UTF_8));
                _wrapper.write(TextLanguageModel.getLanguageString(_lang).getBytes(StandardCharsets.UTF_8));
                
                // TODO - how to get the actual value?
                _wrapper.write(TextLanguageModel.getNgramSizeString(4).getBytes(StandardCharsets.UTF_8));
                
                // First record should be the alpha value.
                _wrapper.write(TextLanguageModel.getAlphaString(in.getCount()).getBytes(StandardCharsets.UTF_8));
                _wrapper.write(TextLanguageModel.getNgramHeaderString().getBytes(StandardCharsets.UTF_8));
            } else {
                in.write(_wrapper);
            }
        }
    }
    
}
