package org.krugler.yalder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public interface DOSerializable {

    public void write(DataOutput out) throws IOException;
    
    public void read(DataInput in) throws IOException;
}
