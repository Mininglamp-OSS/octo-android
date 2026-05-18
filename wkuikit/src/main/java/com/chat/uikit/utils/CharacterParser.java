/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
