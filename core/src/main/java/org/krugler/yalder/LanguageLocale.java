package org.krugler.yalder;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

public class LanguageLocale {

    private static Map<String, LanguageLocale> LOCALES = new HashMap<String, LanguageLocale>();
    
    // Java Locale support uses ISO 639-2 bibliographic codes, versus the terminological codes
    // (which is what we use). So we have a table to normalize from bib to term, and we need
    // a second table to get the names for terminological codes.
    
    // From http://www.loc.gov/standards/iso639-2/php/English_list.php
    private static Map<String, String> BIB_TO_TERM_MAPPING = new HashMap<String, String>();
    
    static {
        BIB_TO_TERM_MAPPING.put("alb", "sqi");
        BIB_TO_TERM_MAPPING.put("arm", "hye");
        BIB_TO_TERM_MAPPING.put("baq", "eus");
        BIB_TO_TERM_MAPPING.put("bur", "mya");
        BIB_TO_TERM_MAPPING.put("chi", "zho");
        BIB_TO_TERM_MAPPING.put("cze", "ces");
        BIB_TO_TERM_MAPPING.put("dut", "nld");
        BIB_TO_TERM_MAPPING.put("geo", "kat");
        BIB_TO_TERM_MAPPING.put("ger", "deu");
        BIB_TO_TERM_MAPPING.put("gre", "ell");
        BIB_TO_TERM_MAPPING.put("ice", "isl");
        BIB_TO_TERM_MAPPING.put("mac", "mkd");
        BIB_TO_TERM_MAPPING.put("may", "msa");
        BIB_TO_TERM_MAPPING.put("mao", "mri");
        BIB_TO_TERM_MAPPING.put("rum", "ron");
        BIB_TO_TERM_MAPPING.put("per", "fas");
        BIB_TO_TERM_MAPPING.put("slo", "slk");
        BIB_TO_TERM_MAPPING.put("tib", "bod");
        BIB_TO_TERM_MAPPING.put("wel", "cym");
        BIB_TO_TERM_MAPPING.put("fre", "fra");
    }

    // Java Locale support uses bibliographic codes, but we use terminological, so provide
    // a mapping from the 639-2 (T) code to the (US locale) language name.
    private static Map<String, String> TERM_CODE_TO_NAME = new HashMap<String, String>();
    static {
        TERM_CODE_TO_NAME.put("sqi", "Albanian");
        TERM_CODE_TO_NAME.put("hye", "Armenian");
        TERM_CODE_TO_NAME.put("eus", "Basque");
        TERM_CODE_TO_NAME.put("zho", "Chinese");
        TERM_CODE_TO_NAME.put("ces", "Czech");
        TERM_CODE_TO_NAME.put("nld", "Dutch");
        TERM_CODE_TO_NAME.put("kat", "Georgian");
        TERM_CODE_TO_NAME.put("deu", "German");
        TERM_CODE_TO_NAME.put("ell", "Greek");
        TERM_CODE_TO_NAME.put("isl", "Icelandic");
        TERM_CODE_TO_NAME.put("mkd", "Macedonian");
        TERM_CODE_TO_NAME.put("msa", "Malay");
        TERM_CODE_TO_NAME.put("mri", "Maori");
        TERM_CODE_TO_NAME.put("ron", "Romanian");
        TERM_CODE_TO_NAME.put("fas", "Persian");
        TERM_CODE_TO_NAME.put("slk", "Slovak");
        TERM_CODE_TO_NAME.put("bod", "Tibetan");
        TERM_CODE_TO_NAME.put("cym", "Welsh");
        TERM_CODE_TO_NAME.put("fra", "French");
    }
    
    private static Map<String, String> IMPLICIT_SCRIPT = new HashMap<String, String>();
    static {
        // TODO add full set of these.
        IMPLICIT_SCRIPT.put("aze", "Latn");
        IMPLICIT_SCRIPT.put("bos", "Latn");
        IMPLICIT_SCRIPT.put("uzb", "Latn");
        IMPLICIT_SCRIPT.put("tuk", "Latn");
        IMPLICIT_SCRIPT.put("msa", "Latn");
        
        IMPLICIT_SCRIPT.put("mon", "Cyrl");
        IMPLICIT_SCRIPT.put("srp", "Cyrl");
        
        IMPLICIT_SCRIPT.put("pan", "Guru");
    }
    
    private static Map<String, String> IMPLICIT_COUNTRY_SCRIPT = new HashMap<>();
    static {
        // TODO add full set of these.
        IMPLICIT_COUNTRY_SCRIPT.put("zhoTW", "Hant");
        IMPLICIT_COUNTRY_SCRIPT.put("zhoCN", "Hans");
    }
    
    private static Map<String, String> SPECIFIC_TO_MACRO = new HashMap<>();
    static {
        // TODO add full set of these.
        SPECIFIC_TO_MACRO.put("nno", "nor");
        SPECIFIC_TO_MACRO.put("nob", "nor");
    }

    private static Map<String, String> ISO_639_2_TO_1 = new HashMap<>();
    static {
        ISO_639_2_TO_1.put("aar", "aa");
        ISO_639_2_TO_1.put("abk", "ab");
        ISO_639_2_TO_1.put("afr", "af");
        ISO_639_2_TO_1.put("aka", "ak");
        ISO_639_2_TO_1.put("sqi", "sq");
        ISO_639_2_TO_1.put("amh", "am");
        ISO_639_2_TO_1.put("ara", "ar");
        ISO_639_2_TO_1.put("arg", "an");
        ISO_639_2_TO_1.put("hye", "hy");
        ISO_639_2_TO_1.put("asm", "as");
        ISO_639_2_TO_1.put("ava", "av");
        ISO_639_2_TO_1.put("ave", "ae");
        ISO_639_2_TO_1.put("aym", "ay");
        ISO_639_2_TO_1.put("aze", "az");
        ISO_639_2_TO_1.put("bak", "ba");
        ISO_639_2_TO_1.put("bam", "bm");
        ISO_639_2_TO_1.put("eus", "eu");
        ISO_639_2_TO_1.put("bel", "be");
        ISO_639_2_TO_1.put("ben", "bn");
        ISO_639_2_TO_1.put("bih", "bh");
        ISO_639_2_TO_1.put("bis", "bi");
        ISO_639_2_TO_1.put("bod", "bo");
        ISO_639_2_TO_1.put("bos", "bs");
        ISO_639_2_TO_1.put("bre", "br");
        ISO_639_2_TO_1.put("bul", "bg");
        ISO_639_2_TO_1.put("mya", "my");
        ISO_639_2_TO_1.put("cat", "ca");
        ISO_639_2_TO_1.put("ces", "cs");
        ISO_639_2_TO_1.put("cha", "ch");
        ISO_639_2_TO_1.put("che", "ce");
        ISO_639_2_TO_1.put("zho", "zh");
        ISO_639_2_TO_1.put("chu", "cu");
        ISO_639_2_TO_1.put("chv", "cv");
        ISO_639_2_TO_1.put("cor", "kw");
        ISO_639_2_TO_1.put("cos", "co");
        ISO_639_2_TO_1.put("cre", "cr");
        ISO_639_2_TO_1.put("cym", "cy");
        ISO_639_2_TO_1.put("ces", "cs");
        ISO_639_2_TO_1.put("dan", "da");
        ISO_639_2_TO_1.put("deu", "de");
        ISO_639_2_TO_1.put("div", "dv");
        ISO_639_2_TO_1.put("nld", "nl");
        ISO_639_2_TO_1.put("dzo", "dz");
        ISO_639_2_TO_1.put("ell", "el");
        ISO_639_2_TO_1.put("eng", "en");
        ISO_639_2_TO_1.put("epo", "eo");
        ISO_639_2_TO_1.put("est", "et");
        ISO_639_2_TO_1.put("eus", "eu");
        ISO_639_2_TO_1.put("ewe", "ee");
        ISO_639_2_TO_1.put("fao", "fo");
        ISO_639_2_TO_1.put("fas", "fa");
        ISO_639_2_TO_1.put("fij", "fj");
        ISO_639_2_TO_1.put("fin", "fi");
        ISO_639_2_TO_1.put("fra", "fr");
        ISO_639_2_TO_1.put("fra", "fr");
        ISO_639_2_TO_1.put("fry", "fy");
        ISO_639_2_TO_1.put("ful", "ff");
        ISO_639_2_TO_1.put("kat", "ka");
        ISO_639_2_TO_1.put("deu", "de");
        ISO_639_2_TO_1.put("gla", "gd");
        ISO_639_2_TO_1.put("gle", "ga");
        ISO_639_2_TO_1.put("glg", "gl");
        ISO_639_2_TO_1.put("glv", "gv");
        ISO_639_2_TO_1.put("ell", "el");
        ISO_639_2_TO_1.put("grn", "gn");
        ISO_639_2_TO_1.put("guj", "gu");
        ISO_639_2_TO_1.put("hat", "ht");
        ISO_639_2_TO_1.put("hau", "ha");
        ISO_639_2_TO_1.put("heb", "he");
        ISO_639_2_TO_1.put("her", "hz");
        ISO_639_2_TO_1.put("hin", "hi");
        ISO_639_2_TO_1.put("hmo", "ho");
        ISO_639_2_TO_1.put("hrv", "hr");
        ISO_639_2_TO_1.put("hun", "hu");
        ISO_639_2_TO_1.put("hye", "hy");
        ISO_639_2_TO_1.put("ibo", "ig");
        ISO_639_2_TO_1.put("isl", "is");
        ISO_639_2_TO_1.put("ido", "io");
        ISO_639_2_TO_1.put("iii", "ii");
        ISO_639_2_TO_1.put("iku", "iu");
        ISO_639_2_TO_1.put("ile", "ie");
        ISO_639_2_TO_1.put("ina", "ia");
        ISO_639_2_TO_1.put("ind", "id");
        ISO_639_2_TO_1.put("ipk", "ik");
        ISO_639_2_TO_1.put("isl", "is");
        ISO_639_2_TO_1.put("ita", "it");
        ISO_639_2_TO_1.put("jav", "jv");
        ISO_639_2_TO_1.put("jpn", "ja");
        ISO_639_2_TO_1.put("kal", "kl");
        ISO_639_2_TO_1.put("kan", "kn");
        ISO_639_2_TO_1.put("kas", "ks");
        ISO_639_2_TO_1.put("kat", "ka");
        ISO_639_2_TO_1.put("kau", "kr");
        ISO_639_2_TO_1.put("kaz", "kk");
        ISO_639_2_TO_1.put("khm", "km");
        ISO_639_2_TO_1.put("kik", "ki");
        ISO_639_2_TO_1.put("kin", "rw");
        ISO_639_2_TO_1.put("kir", "ky");
        ISO_639_2_TO_1.put("kom", "kv");
        ISO_639_2_TO_1.put("kon", "kg");
        ISO_639_2_TO_1.put("kor", "ko");
        ISO_639_2_TO_1.put("kua", "kj");
        ISO_639_2_TO_1.put("kur", "ku");
        ISO_639_2_TO_1.put("lao", "lo");
        ISO_639_2_TO_1.put("lat", "la");
        ISO_639_2_TO_1.put("lav", "lv");
        ISO_639_2_TO_1.put("lim", "li");
        ISO_639_2_TO_1.put("lin", "ln");
        ISO_639_2_TO_1.put("lit", "lt");
        ISO_639_2_TO_1.put("ltz", "lb");
        ISO_639_2_TO_1.put("lub", "lu");
        ISO_639_2_TO_1.put("lug", "lg");
        ISO_639_2_TO_1.put("mkd", "mk");
        ISO_639_2_TO_1.put("mah", "mh");
        ISO_639_2_TO_1.put("mal", "ml");
        ISO_639_2_TO_1.put("mri", "mi");
        ISO_639_2_TO_1.put("mar", "mr");
        ISO_639_2_TO_1.put("msa", "ms");
        ISO_639_2_TO_1.put("mkd", "mk");
        ISO_639_2_TO_1.put("mlg", "mg");
        ISO_639_2_TO_1.put("mlt", "mt");
        ISO_639_2_TO_1.put("mon", "mn");
        ISO_639_2_TO_1.put("mri", "mi");
        ISO_639_2_TO_1.put("msa", "ms");
        ISO_639_2_TO_1.put("mya", "my");
        ISO_639_2_TO_1.put("nau", "na");
        ISO_639_2_TO_1.put("nav", "nv");
        ISO_639_2_TO_1.put("nbl", "nr");
        ISO_639_2_TO_1.put("nde", "nd");
        ISO_639_2_TO_1.put("ndo", "ng");
        ISO_639_2_TO_1.put("nep", "ne");
        ISO_639_2_TO_1.put("nld", "nl");
        ISO_639_2_TO_1.put("nno", "nn");
        ISO_639_2_TO_1.put("nob", "nb");
        ISO_639_2_TO_1.put("nor", "no");
        ISO_639_2_TO_1.put("nya", "ny");
        ISO_639_2_TO_1.put("oci", "oc");
        ISO_639_2_TO_1.put("oji", "oj");
        ISO_639_2_TO_1.put("ori", "or");
        ISO_639_2_TO_1.put("orm", "om");
        ISO_639_2_TO_1.put("oss", "os");
        ISO_639_2_TO_1.put("pan", "pa");
        ISO_639_2_TO_1.put("fas", "fa");
        ISO_639_2_TO_1.put("pli", "pi");
        ISO_639_2_TO_1.put("pol", "pl");
        ISO_639_2_TO_1.put("por", "pt");
        ISO_639_2_TO_1.put("pus", "ps");
        ISO_639_2_TO_1.put("que", "qu");
        ISO_639_2_TO_1.put("roh", "rm");
        ISO_639_2_TO_1.put("ron", "ro");
        ISO_639_2_TO_1.put("ron", "ro");
        ISO_639_2_TO_1.put("run", "rn");
        ISO_639_2_TO_1.put("rus", "ru");
        ISO_639_2_TO_1.put("sag", "sg");
        ISO_639_2_TO_1.put("san", "sa");
        ISO_639_2_TO_1.put("sin", "si");
        ISO_639_2_TO_1.put("slk", "sk");
        ISO_639_2_TO_1.put("slk", "sk");
        ISO_639_2_TO_1.put("slv", "sl");
        ISO_639_2_TO_1.put("sme", "se");
        ISO_639_2_TO_1.put("smo", "sm");
        ISO_639_2_TO_1.put("sna", "sn");
        ISO_639_2_TO_1.put("snd", "sd");
        ISO_639_2_TO_1.put("som", "so");
        ISO_639_2_TO_1.put("sot", "st");
        ISO_639_2_TO_1.put("spa", "es");
        ISO_639_2_TO_1.put("sqi", "sq");
        ISO_639_2_TO_1.put("srd", "sc");
        ISO_639_2_TO_1.put("srp", "sr");
        ISO_639_2_TO_1.put("ssw", "ss");
        ISO_639_2_TO_1.put("sun", "su");
        ISO_639_2_TO_1.put("swa", "sw");
        ISO_639_2_TO_1.put("swe", "sv");
        ISO_639_2_TO_1.put("tah", "ty");
        ISO_639_2_TO_1.put("tam", "ta");
        ISO_639_2_TO_1.put("tat", "tt");
        ISO_639_2_TO_1.put("tel", "te");
        ISO_639_2_TO_1.put("tgk", "tg");
        ISO_639_2_TO_1.put("tgl", "tl");
        ISO_639_2_TO_1.put("tha", "th");
        ISO_639_2_TO_1.put("bod", "bo");
        ISO_639_2_TO_1.put("tir", "ti");
        ISO_639_2_TO_1.put("ton", "to");
        ISO_639_2_TO_1.put("tsn", "tn");
        ISO_639_2_TO_1.put("tso", "ts");
        ISO_639_2_TO_1.put("tuk", "tk");
        ISO_639_2_TO_1.put("tur", "tr");
        ISO_639_2_TO_1.put("twi", "tw");
        ISO_639_2_TO_1.put("uig", "ug");
        ISO_639_2_TO_1.put("ukr", "uk");
        ISO_639_2_TO_1.put("urd", "ur");
        ISO_639_2_TO_1.put("uzb", "uz");
        ISO_639_2_TO_1.put("ven", "ve");
        ISO_639_2_TO_1.put("vie", "vi");
        ISO_639_2_TO_1.put("vol", "vo");
        ISO_639_2_TO_1.put("cym", "cy");
        ISO_639_2_TO_1.put("wln", "wa");
        ISO_639_2_TO_1.put("wol", "wo");
        ISO_639_2_TO_1.put("xho", "xh");
        ISO_639_2_TO_1.put("yid", "yi");
        ISO_639_2_TO_1.put("yor", "yo");
        ISO_639_2_TO_1.put("zha", "za");
        ISO_639_2_TO_1.put("zho", "zh");
        ISO_639_2_TO_1.put("zul", "zu");
    }
    
    private String _language;       // ISO 639-1 language code (2 letters)  
    private String _iso3Language;   // ISO 639-2/T language code (3 letters)
    private String _script;     // ISO 15924 script name (4 letters) or empty
    
    public static LanguageLocale fromString(String languageName) {
        if (!LOCALES.containsKey(languageName)) {

            synchronized (LOCALES) {
                // Make sure nobody added it after we did our check.
                if (!LOCALES.containsKey(languageName)) {
                    LOCALES.put(languageName, new LanguageLocale(languageName));
                }
            }
        }
        
        return LOCALES.get(languageName);
    }
    
    /**
     * Return a language name (suitable for passing into the fromString method) from
     * a language (2 or 3 character) and script (always four character, e.g. "Latn").
     * 
     * @param language
     * @param script
     * @return standard format for language name
     */
    public static String makeLanguageName(String language, String script) {
        return String.format("%s-%s", language, script);
    }
    
    private LanguageLocale(String languageTag) {
        Locale locale = Locale.forLanguageTag(languageTag);
        
        // First convert the language code to ISO 639-2/T
        _iso3Language = locale.getISO3Language();
        if (_iso3Language.isEmpty()) {
            throw new IllegalArgumentException("A valid language code must be provided");
        }
        
        String twoLetterCode = ISO_639_2_TO_1.get(locale.getLanguage());
        _language = twoLetterCode == null ? "" : twoLetterCode;
        
        // See if we need to convert from 639-2/B to 639-2/T
        if (BIB_TO_TERM_MAPPING.containsKey(_iso3Language)) {
            _iso3Language = BIB_TO_TERM_MAPPING.get(_iso3Language);
        }
        
        // Now see if we have a script.
        _script = locale.getScript();
        // TODO - do we want to verify it's a valid script? What happens if you pass
        // in something like en-latin?
        
        // If the script is empty, and we have a country, then we might want to
        // explicitly set the script as that's how it was done previously (e.g. zh-TW
        // to set the Hant script, versus zh-CN for Hans script)
        if (_script.isEmpty()) {
            String country = locale.getCountry();
            // TODO convert UN M.39 code into two letter country 
            if (!country.isEmpty()) {
                // TODO look up language + country
                String languagePlusCountry = _iso3Language + country;
                if (IMPLICIT_COUNTRY_SCRIPT.containsKey(languagePlusCountry)) {
                    _script = IMPLICIT_COUNTRY_SCRIPT.get(languagePlusCountry);
                }
            }
        }
        
        // If the script isn't empty, and it's the default script for the language,
        // then remove it so we don't include it in comparisons.
        if (!_script.isEmpty() && _script.equals(IMPLICIT_SCRIPT.get(_iso3Language))) {
            _script = "";
        }
    }

    // We add a special "weakly equal" method that's used to
    // decide if the target locale matches us. The "target" in
    // this case is what we're looking for (what we want it to be)
    // versus what we got from detection
    // versus what we expect it to be. In this case,
    //  target      result      match?
    //  zho         zho         yes
    //  zho-Hant    zho-Hant    yes
    //  zho         zho-Hant    yes
    //  zho-Hant    zho         no (want explicitly trad chinese, got unspecified)
    //  zho-Hant    zho-Hans    no
    
    // FUTURE support country code for dialects?
    //  target  result  match?
    //  en      en-GB   yes
    //  en-GB   en-US   no
    //  en-GB   en      no
    
    public boolean weaklyEqual(LanguageLocale target) {
        if (!_iso3Language.equals(target._iso3Language)) {
            // But wait - maybe target is a macro language, and we've got
            // a more specific result...if so then they're still (maybe)
            // equal.
            if (!SPECIFIC_TO_MACRO.containsKey(_iso3Language) || !SPECIFIC_TO_MACRO.get(_iso3Language).equals(target._iso3Language)) {
                return false;
            }
        }
        
        if (target._script.isEmpty()) {
            // Target says we don't care about script, so always matches.
            return true;
        } else {
            return _script.equals(target._script);
        }
    }
    
    public String getName() {
        if (!_script.isEmpty()) {
            return String.format("%s-%s", _iso3Language, _script);
        } else {
            return _iso3Language;
        }
    }
    
    public String getISO3LetterName() {
        return _iso3Language;
    }

    public String getISO2LetterName() {
        return _language;
    }

    /**
     * @param alpha2Code
     * @return ISO 639-2 "alpha3" code, or null if no mapping exists.
     */
    public static String convertISO2LetterNameTo3Letters(String alpha2Code) {
        try {
            LanguageLocale ll = LanguageLocale.fromString(alpha2Code);
            return ll.getISO3LetterName();
        } catch (MissingResourceException e) {
            return null;
        }
    }
    
    public final String toString() {
        String languageTag;
        if (!_script.isEmpty()) {
            languageTag = String.format("%s-%s", _iso3Language, _script);
        } else {
            languageTag = _iso3Language;
        }
        
        String displayLanguage = Locale.forLanguageTag(languageTag).getDisplayLanguage();
        if (TERM_CODE_TO_NAME.containsKey(_iso3Language)) {
            // It's a terminological code, so we have to provide the name ourselves.
            displayLanguage = TERM_CODE_TO_NAME.get(_iso3Language);
        }
        
        return String.format("%s (%s)", languageTag, displayLanguage);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((_iso3Language == null) ? 0 : _iso3Language.hashCode());
        result = prime * result + ((_script == null) ? 0 : _script.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        LanguageLocale other = (LanguageLocale) obj;
        if (_iso3Language == null) {
            if (other._iso3Language != null)
                return false;
        } else if (!_iso3Language.equals(other._iso3Language))
            return false;
        if (_script == null) {
            if (other._script != null)
                return false;
        } else if (!_script.equals(other._script))
            return false;
        return true;
    }
}
