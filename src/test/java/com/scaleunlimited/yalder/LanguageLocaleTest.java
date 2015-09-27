package com.scaleunlimited.yalder;

import static org.junit.Assert.*;

import org.junit.Test;

public class LanguageLocaleTest {

    @Test
    public void testDisplayNameInToString() {
        LanguageLocale l = LanguageLocale.fromString("pag");
        System.out.println(l);
        
        System.out.println(LanguageLocale.fromString("ind"));
        System.out.println(LanguageLocale.fromString("dzo"));
    }
    
    @Test
    public void testMacroLanguageEquivalence() {
        LanguageLocale macroLocale = LanguageLocale.fromString("nor");
        LanguageLocale specificLocale = LanguageLocale.fromString("nob");
        assertTrue(specificLocale.weaklyEqual(macroLocale));
        assertFalse(macroLocale.weaklyEqual(specificLocale));
    }
    
    @Test
    public void test3letter2letterEquivalence() {
        LanguageLocale threeLetter = LanguageLocale.fromString("cym");
        LanguageLocale twoLetter = LanguageLocale.fromString("cy");
        assertEquals(twoLetter, threeLetter);
        assertTrue(threeLetter.weaklyEqual(twoLetter));
        
        threeLetter = LanguageLocale.fromString("swe");
        twoLetter = LanguageLocale.fromString("sv");
        assertEquals(twoLetter, threeLetter);
    }

    @Test
    public void testBiblioCodes() {
        LanguageLocale threeLetterBiblio = LanguageLocale.fromString("ger");
        LanguageLocale threeLetterTech = LanguageLocale.fromString("deu");
        LanguageLocale twoLetter = LanguageLocale.fromString("de");
        assertEquals(twoLetter, threeLetterBiblio);
        assertEquals(threeLetterTech, threeLetterBiblio);
        assertTrue(threeLetterBiblio.weaklyEqual(twoLetter));
        assertTrue(threeLetterBiblio.weaklyEqual(threeLetterTech));
    }
    
    @Test
    public void testScriptWeaklyEqual() {
        LanguageLocale target = LanguageLocale.fromString("zho");
        LanguageLocale detectTrad = LanguageLocale.fromString("zho-Hant");
        LanguageLocale detectSimp = LanguageLocale.fromString("zho-Hans");

        assertTrue(detectTrad.weaklyEqual(target));
        assertTrue(detectSimp.weaklyEqual(target));
        
        assertFalse(target.weaklyEqual(detectTrad));
        assertFalse(target.weaklyEqual(detectSimp));
    }
    
    @Test
    public void testScriptFromRegion() {
        LanguageLocale withRegion = LanguageLocale.fromString("zh-TW");
        LanguageLocale withScript = LanguageLocale.fromString("zh-Hant");
        assertEquals(withRegion, withScript);
    }
}
