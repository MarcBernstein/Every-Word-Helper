/* Copyright 2010 Marc Bernstein

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.mcbtech.everywordhelper;

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;

/**
 * @author Marc Bernstein (github@marcanderica.org)
 */
public class SaveStateHelper {
	private static final String KEY_MATCHED_WORDS = "KEY_MATCHED_WORDS";

	private static final String KEY_LETTERS = "KEY_LETTERS";

	private static final String KEY_MIN_WORD_LENGTH = "KEY_MIN_WORD_LENGTH";

	public static void save(Bundle outState, String mLetters, int mMinWordLength, List<String> mMatchedWords) {
		outState.putStringArrayList(KEY_MATCHED_WORDS, (ArrayList<String>) mMatchedWords);
		outState.putString(KEY_LETTERS, mLetters);
		outState.putInt(KEY_MIN_WORD_LENGTH, mMinWordLength);
	}

	public static String restoreLetters(Bundle savedInstanceState) {
		return savedInstanceState.getString(KEY_LETTERS);
	}

	public static int restoreMinWordLength(Bundle savedInstanceState) {
		return savedInstanceState.getInt(KEY_MIN_WORD_LENGTH);
	}

	public static ArrayList<String> restoreMatchedWords(Bundle savedInstanceState) {
		return savedInstanceState.getStringArrayList(KEY_MATCHED_WORDS);
	}
}
