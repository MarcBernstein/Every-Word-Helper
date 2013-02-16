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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

/**
 * @author marc
 * 
 */
public class EveryWordHelper extends SherlockFragmentActivity {

	private static final String TAG = EveryWordHelper.class.getSimpleName();

	private static final int TOTAL_WORDS = 29218;

	// Starting values for word length
	private int mMinWordLength = 3;

	private int mMaxWordLength = 6;

	private ArrayAdapter<String> mAdapter;

	private List<String> mAllWords;

	private List<String> mMatchedWords;

	private String mLetters;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		if (savedInstanceState != null) {
			mLetters = SaveStateHelper.restoreLetters(savedInstanceState);
			mMinWordLength = SaveStateHelper.restoreMinWordLength(savedInstanceState);
			mMatchedWords = SaveStateHelper.restoreMatchedWords(savedInstanceState);
		}

		if (mMatchedWords == null) {
			mMatchedWords = new ArrayList<String>();
		}

		TextView minLengthTV = (TextView) findViewById(R.id.minimum_length_textview);
		minLengthTV.setText(getString(R.string.current_minimum_word_length_is_d, mMinWordLength));

		final Button findWordsBtn = (Button) findViewById(R.id.find_words_button);
		final EditText lettersET = (EditText) findViewById(R.id.letters_edittext);
		final ListView resultsLV = (ListView) findViewById(R.id.results_listview);
		mAdapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.list_item, mMatchedWords);
		resultsLV.setAdapter(mAdapter);

		lettersET.setText(mLetters == null ? "" : mLetters);
		lettersET.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					findWordsBtn.performClick();
					return true;
				}
				return false;
			}
		});

		lettersET.setOnKeyListener(new OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
					findWordsBtn.performClick();
					return true;
				}
				return false;
			}
		});

		resultsLV.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				mAdapter.remove(mMatchedWords.get(position));
				if (mMatchedWords.isEmpty()) {
					lettersET.setText("");
					mMatchedWords
							.add(getString(R.string.that_s_every_word_you_can_enter_a_new_set_of_letters_now_for_the_next_round));
					mAdapter.notifyDataSetChanged();
				}
			}
		});

		findWordsBtn.requestFocus();
		findWordsBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mLetters = lettersET.getText().toString().trim().toUpperCase(Locale.US);
				lettersET.setText(mLetters);

				if (mLetters.length() != 6 && mLetters.length() != 7) {
					Toast
							.makeText(getApplicationContext(),
									getString(R.string.invalid_length_of_d_should_be_6_or_7_characters, mLetters.length()),
									Toast.LENGTH_LONG).show();
				} else if (mLetters.contains(" ")) {
					Toast.makeText(getApplicationContext(),
							R.string.invalid_character_s_detected_should_only_contain_letters_from_a_to_z, Toast.LENGTH_LONG).show();
				} else {
					InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(lettersET.getWindowToken(), 0);

					// Clear out any old matched strings
					mMatchedWords.clear();
					mMaxWordLength = mLetters.length();
					new FindWordsTask().execute();
				}
			}
		});
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		SaveStateHelper.save(outState, mLetters, mMinWordLength, mMatchedWords);
	}

	/**
	 * Uses a Set to see if a previous instance of this word has already been added.
	 * 
	 * @param arlList
	 */
	public static void removeDuplicateWithOrder(List<String> arlList) {
		Set<String> set = new HashSet<String>();
		List<String> newList = new ArrayList<String>();
		for (Iterator<String> iter = arlList.iterator(); iter.hasNext();) {
			String element = iter.next();
			if (set.add(element)) {
				newList.add(element);
			}
		}
		arlList.clear();
		arlList.addAll(newList);
	}

	/**
	 * Checks to see if the word is valid by checking it against the letters.
	 * 
	 * @param word
	 *          - The word to check for extra characters not defined in original letter list
	 * @return true if the word is valid, false if not
	 */
	private boolean checkForDoubles(String word) {
		boolean valid = true;
		List<String> chars_in_letters = new ArrayList<String>();
		List<String> chars_in_word = new ArrayList<String>();
		for (int i = 0; i < mLetters.length(); i++) {
			chars_in_letters.add(String.valueOf(mLetters.charAt(i)));
		}

		for (int i = 0; i < word.length(); i++) {
			chars_in_word.add(String.valueOf(word.charAt(i)));
		}

		for (Iterator<String> iterator = chars_in_word.iterator(); iterator.hasNext();) {
			String s = iterator.next();
			if (chars_in_letters.contains(s)) {
				chars_in_letters.remove(s);
			} else {
				valid = false;
				break;
			}
		}

		return valid;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.reset) {
			EditText e = (EditText) findViewById(R.id.letters_edittext);
			e.setText("");
			mMatchedWords.clear();
			mAdapter.notifyDataSetChanged();
			return true;
		} else if (itemId == R.id.setminlength) {
			final CharSequence[] items = getResources().getStringArray(R.array.min_word_length_array);
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.pick_a_minimum_word_length);
			builder.setItems(items, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int item) {
					mMinWordLength = Integer.valueOf(items[item].toString());

					TextView tvMinLength = (TextView) findViewById(R.id.minimum_length_textview);
					tvMinLength.setText(getString(R.string.current_minimum_word_length_is_d, mMinWordLength));
				}
			});
			AlertDialog alert = builder.create();
			alert.show();
			return true;
		} else if (itemId == R.id.about) {
			Intent intent = new Intent(this, EveryWordHelperAbout.class);
			startActivity(intent);
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	/**
	 * String comparator that checks length of 2 strings.
	 */
	private class ComparatorEx implements Comparator<String> {
		@Override
		public int compare(String s1, String s2) {
			if (s1.length() > s2.length()) {
				return 1;
			} else if (s1.length() < s2.length()) {
				return -1;
			} else {
				return 0;
			}
		}
	}

	public class FindWordsTask extends AsyncTask<Void, Integer, Void> {

		private final ProgressDialog progressDialog;

		private int mProgressCount;

		public FindWordsTask() {
			mProgressCount = 0;

			progressDialog = new ProgressDialog(EveryWordHelper.this);
			progressDialog.setMax(TOTAL_WORDS);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setMessage(getString(R.string.finding_matching_words));
			progressDialog.setCancelable(false);
			progressDialog.setProgress(0);
		}

		@Override
		protected void onPreExecute() {
			progressDialog.show();
		}

		@Override
		protected Void doInBackground(final Void... args) {
			if (mAllWords == null || mAllWords.isEmpty()) {
				mAllWords = populateList();
			}
			getWords(mAllWords);

			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			progressDialog.setProgress(values[0]);
		}

		@Override
		protected void onPostExecute(final Void result) {
			if (progressDialog.isShowing()) {
				progressDialog.dismiss();
			}

			Toast.makeText(getApplicationContext(),
					getString(R.string.found_words_touch_a_word_to_remove_it_from_the_list, mMatchedWords.size()),
					Toast.LENGTH_LONG).show();
			mAdapter.notifyDataSetChanged();
		}

		/**
		 * Gets all the words defined in the 2+2gfreq word list used by Every Word.
		 * 
		 * @return All words as strings in a List.
		 */
		private List<String> populateList() {

			InputStream is = new BufferedInputStream(getResources().openRawResource(R.raw.twoplustwogfreq_parsed), 8192);
			BufferedReader br = new BufferedReader(new InputStreamReader(is), 8192);

			List<String> lines = new ArrayList<String>();
			String line = null;
			try {
				while ((line = br.readLine()) != null) {
					lines.add(line);
				}
				br.close();
				is.close();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}

			return lines;
		}

		/**
		 * Performs the regex matching of the user supplied letters against the 2+2gfreq word list.
		 * 
		 * @param allWords
		 *          - Parsed results of 2+2gfreq word list.
		 */
		private void getWords(final List<String> allWords) {
			String word;
			String regex3 = "[" + mLetters + "][" + mLetters + "][" + mLetters + "]";
			String regex4 = "[" + mLetters + "][" + mLetters + "][" + mLetters + "][" + mLetters + "]";
			String regex5 = "[" + mLetters + "][" + mLetters + "][" + mLetters + "][" + mLetters + "][" + mLetters + "]";
			String regex6 = "[" + mLetters + "][" + mLetters + "][" + mLetters + "][" + mLetters + "][" + mLetters + "]["
					+ mLetters + "]";
			String regex7 = "[" + mLetters + "][" + mLetters + "][" + mLetters + "][" + mLetters + "][" + mLetters + "]["
					+ mLetters + "][" + mLetters + "]";

			for (Iterator<String> iterator = allWords.iterator(); iterator.hasNext();) {
				word = iterator.next();
				boolean matched = false;
				switch (word.length()) {
				case 3:
					if (mMinWordLength == 3 && word.matches(regex3)) {
						matched = true;
					}
					break;
				case 4:
					if (word.matches(regex4)) {
						matched = true;
					}
					break;
				case 5:
					if (word.matches(regex5)) {
						matched = true;
					}
					break;
				case 6:
					if (word.matches(regex6)) {
						matched = true;
					}
					break;
				case 7:
					if (mMaxWordLength == 7 && word.matches(regex7)) {
						matched = true;
					}
					break;
				default:
					break;
				}

				if (matched && checkForDoubles(word)) {
					mMatchedWords.add(word);
					Log.d(TAG, word);
				}
				matched = false;
				onProgressUpdate(mProgressCount++);
			}

			Collections.sort(mMatchedWords, new ComparatorEx());
			removeDuplicateWithOrder(mMatchedWords);
		}
	}
}
