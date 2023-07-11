package com.chat.uikit.utils;

import java.util.ArrayList;

/**
 * 2019-11-30 17:07
 */
public class CharacterParser {
    private CharacterParser() {
    }

    private static class CharacterParserBinder {
        private static final CharacterParser characterParser = new CharacterParser();
    }

    public static CharacterParser getInstance() {
        return CharacterParserBinder.characterParser;
    }

    public ArrayList<String> getList() {
        ArrayList<String> customLetters = new ArrayList<>();
        customLetters.add("A");
        customLetters.add("B");
        customLetters.add("C");
        customLetters.add("D");
        customLetters.add("E");
        customLetters.add("F");
        customLetters.add("G");
        customLetters.add("H");
        customLetters.add("I");
        customLetters.add("J");
        customLetters.add("K");
        customLetters.add("L");
        customLetters.add("M");
        customLetters.add("N");
        customLetters.add("O");
        customLetters.add("P");
        customLetters.add("Q");
        customLetters.add("R");
        customLetters.add("S");
        customLetters.add("T");
        customLetters.add("U");
        customLetters.add("V");
        customLetters.add("W");
        customLetters.add("X");
        customLetters.add("Y");
        customLetters.add("Z");
        customLetters.add("#");
        return customLetters;
    }
}
