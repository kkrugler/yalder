package org.krugler.yalder;


public class CharUtils {

    /**
     * Return hash value for <text>, for <length> chars at <offset>
     * 
     * @param text
     * @param offset
     * @param length
     * @return hash of characters.
     */
    public static int joaat_hash(char[] text, int offset, int length) {
        int hash = 0;
        
        for (int i = 0; i < length; i++) {
            int c = (int)text[offset + i];
            hash += c;
            hash += (hash << 10);
            hash ^= (hash >>> 6);
        }
        
        hash += (hash << 3);
        hash ^= (hash >>> 11);
        hash += (hash << 15);
        
        return hash;
    }


}
