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

@SuppressWarnings("serial")
public class TextAndLangInputFormat extends DelimitedInputFormat<Tuple2<String, String>> {

	// Filenames look like Biltzheim_bug.txt
	private static final Pattern FILENAME_PATTERN = Pattern.compile(".+_(...)\\.txt");
	
	private transient String _langCode;
	
	public TextAndLangInputFormat(Path filePath) {
		super(filePath, null);
	}

	@Override
	public void open(FileInputSplit split) throws IOException {
		super.open(split);
		
		String filename = split.getPath().getName();
		Matcher m = FILENAME_PATTERN.matcher(filename);
		if (!m.matches()) {
			throw new IllegalArgumentException("Filename format not supported: " + filename);
		}
		
		_langCode = m.group(1);
	}
	
	@Override
	public Tuple2<String, String> readRecord(Tuple2<String, String> reusable, byte[] bytes, int offset, int numBytes)
			throws IOException {
		return new Tuple2<>(_langCode, new String(bytes, offset, numBytes, StandardCharsets.UTF_8));
	}

	@Override
	public String toString() {
		return "TextAndLangInputFormat (" + Arrays.toString(getFilePaths()) + ")";
	}

	@Override
	public boolean supportsMultiPaths() {
		return true;
	}
}
