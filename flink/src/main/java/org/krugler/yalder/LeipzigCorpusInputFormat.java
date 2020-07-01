package org.krugler.yalder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.flink.api.common.io.DelimitedInputFormat;
	import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.core.fs.FileInputSplit;
import org.apache.flink.core.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("serial")
public class LeipzigCorpusInputFormat extends DelimitedInputFormat<Tuple2<String, String>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LeipzigCorpusInputFormat.class);
    
	// Filenames look like swa_wikipedia_2016_100K-sentences.txt
    private static final Pattern FILENAME_PATTERN = Pattern.compile("(...)_.+\\.txt");
    
    // Filenames can also look like ara-sy_newscrawl_2012_300K-sentences.txt
    private static final Pattern FILENAME_WITH_SCRIPT_PATTERN = Pattern.compile("(...)-(..)_.+\\.txt");
	
	private transient String _langCode;
	private transient boolean _validFile;
	
	public LeipzigCorpusInputFormat(Path filePath) {
		super(filePath, null);
	}

	@Override
	public void open(FileInputSplit split) throws IOException {
		super.open(split);
		
		String filename = split.getPath().getName();
		Matcher m = FILENAME_PATTERN.matcher(filename);
		if (!m.matches()) {
		    m = FILENAME_WITH_SCRIPT_PATTERN.matcher(filename);
		    if (!m.matches()) {
		        LOGGER.warn("Filename format not supported: {}", filename);
		        _validFile = false;
		        return;
		    }
		}
		
		_langCode = m.group(1);
		_validFile = true;
	}
	
	@Override
	public Tuple2<String, String> readRecord(Tuple2<String, String> reusable, byte[] bytes, int offset, int numBytes)
			throws IOException {
	    
	    if (!_validFile) {
	        return null;
	    }
	    
	    // Format of a line is <line number><tab><text>
	    // So scan for the tab, and use that to create the string.
	    while (numBytes-- > 0) {
	        if (bytes[offset++] == '\t') {
	            break;
	        }
	    }
	    
	    reusable.setFields(_langCode, new String(bytes, offset, numBytes, StandardCharsets.UTF_8));
		return reusable;
	}

	@Override
	public String toString() {
		return "LeipzigCorpusInputFormat (" + Arrays.toString(getFilePaths()) + ")";
	}

	@Override
	public boolean supportsMultiPaths() {
		return true;
	}
}
