package com.scaleunlimited.yalder;


public class CharUtils {

    /**
     * Return hash value for <key>
     * 
     * @param key
     * @return
     */
    public static int joaat_hash(CharSequence key) {
        int hash = 0;
        
        for (int i = 0; i < key.length(); i++) {
            int c = (int)key.charAt(i);
            hash += c;
            hash += (hash << 10);
            hash ^= (hash >>> 6);
        }
        
        return conditionHash(hash);
    }

    public static int joaat_hash(char[] text, int offset, int length) {
        int hash = 0;
        
        for (int i = 0; i < length; i++) {
            int c = (int)text[offset + i];
            hash += c;
            hash += (hash << 10);
            hash ^= (hash >>> 6);
        }
        
        return conditionHash(hash);
    }

    private static int conditionHash(int hash) {
        hash += (hash << 3);
        hash ^= (hash >>> 11);
        hash += (hash << 15);
        
        return hash;
    }

}
