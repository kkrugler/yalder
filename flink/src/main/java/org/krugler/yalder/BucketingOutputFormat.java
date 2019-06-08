package org.krugler.yalder;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.flink.api.common.io.OutputFormat;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.configuration.Configuration;

@SuppressWarnings("serial")
public class BucketingOutputFormat<IT> implements OutputFormat<IT>, Serializable {

    private OutputFormatFactory<IT> _factory;
    private KeySelector<IT, String> _pathGenerator;
    private boolean _forceFiles = true;
    
    private transient Configuration _conf;
    private transient int _taskNumber;
    private transient int _numTasks;
    
    private transient Map<String, OutputFormat<IT>> _outputs;
    
    public BucketingOutputFormat(OutputFormatFactory<IT> formatFactory, KeySelector<IT, String> pathGenerator) {
        _factory = formatFactory;
        _pathGenerator = pathGenerator;
    }
    
    public void setForceFiles(boolean forceFiles) {
        _forceFiles = forceFiles;
    }
    
    public boolean isForceFiles() {
        return _forceFiles;
    }
    
    @Override
    public void configure(Configuration conf) {
        _conf = conf;
    }
    
    @Override
    public void open(int taskNumber, int numTasks) throws IOException {
        _taskNumber = taskNumber;
        _numTasks = numTasks;
        
        _outputs = new HashMap<>();
    }
    
    @Override
    public void writeRecord(IT record) throws IOException {
        String bucket;
        
        try {
            bucket = _pathGenerator.getKey(record);
        } catch (Exception e) {
            throw new IOException("Error getting bucket from record", e);
        }
        
        OutputFormat<IT> output = _outputs.get(bucket);
        if (output == null) {
            output = _factory.makeOutputFormat(bucket);
            output.configure(_conf);
            
            if (_forceFiles) {
                //Force to one task, so we output a single file for
                // each bucket, versus (if parallelism > 1) directories.
                output.open(0, 1);
            } else {
                output.open(_taskNumber, _numTasks);
            }
            
            _outputs.put(bucket, output);
        }
        
        output.writeRecord(record);
    }
    
    @Override
    public void close() throws IOException {
        IOException firstException = null;
        for (OutputFormat<IT> output : _outputs.values()) {
            try {
                output.close();
            } catch (Exception e) {
                if (firstException == null) {
                    firstException = new IOException("Exception closing bucket", e);
                }
            }
        }
        
        if (firstException != null) {
            throw firstException;
        }
    }
}
