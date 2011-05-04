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
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class EveryWordHelper extends Activity {
    private static final String TAG = "EveryWordHelper";

    private static final int PROGRESS_DIALOG = 0;

    private static final int TOTAL_WORDS = 29218;
    private static final String TOTAL = "TOTAL";

    // Starting values for word length
    private int minWordLength = 3;
    private int maxWordLength = 6;

    private ArrayAdapter<String> adapter;
    private final List<String> matchedWords = new ArrayList<String>();
    private List<String> allWords = new ArrayList<String>();
    private String letters;

    final Handler mHandler = new Handler();

    private ProgressThread progressThread;
    private ProgressDialog progressDialog;
    private int progressCount = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        TextView tvMinLength = (TextView) findViewById(R.id.tvMinLength);
        tvMinLength.setText(getString(R.string.current_minimum_word_length_is_d, minWordLength));
        final EditText etLetters = (EditText) findViewById(R.id.etLetters);

        final ListView lvResults = (ListView) findViewById(R.id.lvResults);
        adapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.list_item, matchedWords);
        lvResults.setAdapter(adapter);

        lvResults.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                adapter.remove(matchedWords.get(position));
                if (matchedWords.isEmpty()) {
                    etLetters.setText("");
                    etLetters.requestFocus();
                    matchedWords.add(getString(R.string.that_s_every_word_you_can_enter_a_new_set_of_letters_now_for_the_next_round));
                    adapter.notifyDataSetChanged();
                }
            }
        });

        Button btnFind = (Button) findViewById(R.id.btnFind);
        btnFind.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                letters = etLetters.getText().toString().trim().toUpperCase();
                etLetters.setText(letters);

                if (letters.length() != 6 && letters.length() != 7) {
                    Toast.makeText(getApplicationContext(), getString(R.string.invalid_length_of_d_should_be_6_or_7_characters, letters.length()), Toast.LENGTH_LONG).show();
                } else if (letters.contains(" ")) {
                    Toast.makeText(getApplicationContext(), R.string.invalid_character_s_detected_should_only_contain_letters_from_a_to_z, Toast.LENGTH_LONG).show();
                } else {
                    // Clear out any old matched strings
                    if (!matchedWords.isEmpty()) {
                        Iterator<String> iterator = matchedWords.iterator();
                        while (iterator.hasNext()) {
                            iterator.next();
                            iterator.remove();
                        }
                        adapter.notifyDataSetChanged();
                    }
                    progressCount = 0;
                    showDialog(PROGRESS_DIALOG);
                    maxWordLength = letters.length();
                    allWords = populateList();
                    getWords(allWords);
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.reset:
                progressCount = 0;
                EditText e = (EditText) findViewById(R.id.etLetters);
                e.setText("");
                e.requestFocus();
                if (!matchedWords.isEmpty()) {
                    Iterator<String> iterator = matchedWords.iterator();
                    while (iterator.hasNext()) {
                        iterator.next();
                        iterator.remove();
                    }
                    adapter.notifyDataSetChanged();
                }
                return true;
            case R.id.setminlength:
                final CharSequence[] items = getResources().getStringArray(R.array.min_word_length_array);

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.pick_a_minimum_word_length);
                builder.setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        minWordLength = Integer.valueOf(items[item].toString());

                        TextView tvMinLength = (TextView) findViewById(R.id.tvMinLength);
                        tvMinLength.setText(getString(R.string.current_minimum_word_length_is_d, minWordLength));

                        Toast.makeText(getApplicationContext(), getString(R.string.set_minimum_word_length_to_d, minWordLength), Toast.LENGTH_SHORT).show();
                        Log.d(TAG, getString(R.string.current_minimum_word_length_is_d, minWordLength));
                    }
                });
                AlertDialog alert = builder.create();
                alert.show();

                return true;
            case R.id.about:
                Intent intent = new Intent(this, EveryWordHelperAbout.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case PROGRESS_DIALOG:
                progressDialog = new ProgressDialog(EveryWordHelper.this);
                progressDialog.setMax(TOTAL_WORDS);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setMessage(getString(R.string.finding_matching_words));
                progressDialog.setCancelable(false);
                progressDialog.setProgress(0);
                progressThread = new ProgressThread(handler);
                progressThread.start();
                return progressDialog;
            default:
                return null;
        }
    }

    /**
     * Create runnable for posting to GUI
     */
    final Runnable mUpdateResults = new Runnable() {
        public void run() {
            updateResultsInUi();
        }
    };

    /**
     * Shows size of results to user and updates the ListView.
     */
    private void updateResultsInUi() {
        Toast.makeText(getApplicationContext(), getString(R.string.found_words_touch_a_word_to_remove_it_from_the_list, matchedWords.size()), Toast.LENGTH_LONG).show();
        adapter.notifyDataSetChanged();
    }

    /**
     * Gets all the words defined in the 2+2gfreq word list used by Every Word.
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
     * @param allWords - Parsed results of 2+2gfreq word list.
     */
    private void getWords(final List<String> allWords) {
        Thread t = new Thread() {
            @Override
            public void run() {

                String word;
                String regex3 = "[" + letters + "][" + letters + "][" + letters + "]";
                String regex4 = "[" + letters + "][" + letters + "][" + letters + "][" + letters + "]";
                String regex5 = "[" + letters + "][" + letters + "][" + letters + "][" + letters + "][" + letters + "]";
                String regex6 = "[" + letters + "][" + letters + "][" + letters + "][" + letters + "][" + letters + "][" + letters + "]";
                String regex7 = "[" + letters + "][" + letters + "][" + letters + "][" + letters + "][" + letters + "][" + letters + "][" + letters + "]";

                for (Iterator<String> iterator = allWords.iterator(); iterator.hasNext();) {
                    word = iterator.next();
                    boolean matched = false;
                    switch (word.length()) {
                        case 3:
                            if (minWordLength == 3 && word.matches(regex3)) {
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
                            if (maxWordLength == 7 && word.matches(regex7)) {
                                matched = true;
                            }
                            break;
                        default:
                            break;
                    }

                    if (matched && checkForDoubles(word)) {
                        matchedWords.add(word);
                        Log.d(TAG, word);
                    }
                    matched = false;
                    progressCount++;
                }

                Collections.sort(matchedWords, new ComparatorEx());
                removeDuplicateWithOrder(matchedWords);

                mHandler.post(mUpdateResults);
            }
        };
        t.start();
    }

    /**
     * Checks to see if the word is valid by checking it against the letters.
     * @param word - The word to check for extra characters not defined in original letter list
     * @return true if the word is valid, false if not
     */
    private boolean checkForDoubles(String word) {
        boolean valid = true;
        List<String> chars_in_letters = new ArrayList<String>();
        List<String> chars_in_word = new ArrayList<String>();
        for (int i = 0; i < letters.length(); i++) {
            chars_in_letters.add(String.valueOf((letters.charAt(i))));
        }

        for (int i = 0; i < word.length(); i++) {
            chars_in_word.add(String.valueOf((word.charAt(i))));
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

    /**
     * Uses a Set to see if a previous instance of this word has already been added.
     * @param arlList
     */
    public static void removeDuplicateWithOrder(List<String> arlList) {
        Set<String> set = new HashSet<String>();
        List<String> newList = new ArrayList<String>();
        for (Iterator<String> iter = arlList.iterator(); iter.hasNext();) {
            String element = iter.next();
            if (set.add(element))
                newList.add(element);
        }
        arlList.clear();
        arlList.addAll(newList);
    }

    /**
     *  Define the Handler that receives messages from the thread and update the progress
     */
    final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            int total = msg.getData().getInt(TOTAL);
            progressDialog.setProgress(total);
            if (total >= TOTAL_WORDS) {
                removeDialog(PROGRESS_DIALOG);
                progressThread.setState(ProgressThread.STATE_DONE);

            }
        }
    };

    /**
     * String comparator that checks length of 2 strings.
     */
    private class ComparatorEx implements Comparator<String> {
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

    /**
     * Nested class that performs progress calculations (counting)
     */
    private class ProgressThread extends Thread {
        Handler mHandler;
        final static int STATE_DONE = 0;
        final static int STATE_RUNNING = 1;
        int mState;

        ProgressThread(Handler h) {
            mHandler = h;
        }

        /**
         * Updates progress dialog every 1/3 sec.
         */
        @Override
        public void run() {
            mState = STATE_RUNNING;
            while (mState == STATE_RUNNING) {
                try {
                    Thread.sleep(333);
                } catch (InterruptedException e) {
                    Log.e("ERROR", "Thread Interrupted");
                }
                Message msg = mHandler.obtainMessage();
                Bundle b = new Bundle();
                b.putInt(TOTAL, progressCount);
                msg.setData(b);
                mHandler.sendMessage(msg);
            }
        }

        public void setState(int state) {
            mState = state;
        }
    }
}
