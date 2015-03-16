package com.scaleunlimited.yalder;

import java.util.HashMap;
import java.util.Map;

public class CharUtils {

    /**
     * Return hash value for <key> that has low three bits cleared.
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
        
        hash += (hash << 3);
        hash ^= (hash >>> 11);
        hash += (hash << 15);
        
        return hash & ~0x07;
    }

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
        
        return hash & ~0x07;
    }

    public static Map<CharSequence, NGramStats> calcNGramStats(CharSequence text, int minNGramLength, int maxNGramLength) {
        Map<CharSequence, NGramStats> result = new HashMap<CharSequence, NGramStats>();
        
        NGramTokenizer tokenizer = new NGramTokenizer(text, minNGramLength, maxNGramLength);
        while (tokenizer.hasNext()) {
            CharSequence token = tokenizer.next();
            NGramStats curStats = result.get(token);
            if (curStats == null) {
                curStats = new NGramStats();
                result.put(token, curStats);
            }
            
            curStats.incNGramCount();
        }
        
        return result;
    }
    

}
