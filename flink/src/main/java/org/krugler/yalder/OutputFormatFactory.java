package org.krugler.yalder;

import java.io.Serializable;

import org.apache.flink.api.common.io.OutputFormat;

public interface OutputFormatFactory<T> extends Serializable {

    public OutputFormat<T> makeOutputFormat(String bucket);
}
