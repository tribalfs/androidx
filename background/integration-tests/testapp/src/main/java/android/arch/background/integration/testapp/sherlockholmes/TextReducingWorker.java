/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.arch.background.integration.testapp.sherlockholmes;

import android.arch.background.integration.testapp.db.TestDatabase;
import android.arch.background.integration.testapp.db.WordCount;
import android.arch.background.workmanager.Work;
import android.arch.background.workmanager.Worker;
import android.arch.background.workmanager.model.Arguments;
import android.util.Log;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Worker that combines the word counts of various works and outputs them.
 */
public class TextReducingWorker extends Worker {

    private static final String INPUT_FILES = "input_files";

    private Map<String, Integer> mWordCount = new HashMap<>();

    /**
     * Creates a {@link Work.Builder} with the necessary arguments.
     *
     * @param inputFiles A list of input files to process
     * @return A {@link Work.Builder} with these arguments
     */
    public static Work.Builder create(String... inputFiles) {
        Arguments args = new Arguments();
        args.putStringArray(INPUT_FILES, inputFiles);
        return new Work.Builder(TextReducingWorker.class).withArguments(args);
    }

    @Override
    public void doWork() throws Exception {
        Arguments args = getArguments();
        String[] inputFiles = args.getStringArray(INPUT_FILES);
        if (inputFiles == null) {
            throw new IllegalArgumentException();
        }

        for (int i = 0; i < inputFiles.length; ++i) {
            FileInputStream fileInputStream = null;
            DataInputStream dataInputStream = null;
            try {
                fileInputStream = getAppContext().openFileInput(inputFiles[i]);
                dataInputStream = new DataInputStream(fileInputStream);
                while (dataInputStream.available() > 0) {
                    String word = dataInputStream.readUTF();
                    int count = dataInputStream.readInt();
                    if (mWordCount.containsKey(word)) {
                        count += mWordCount.get(word);
                    }
                    mWordCount.put(word, count);
                }
            } finally {
                if (dataInputStream != null) {
                    dataInputStream.close();
                }
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
            }
        }

        List<Map.Entry<String, Integer>> sortedList = new ArrayList<>(mWordCount.size());
        sortedList.addAll(mWordCount.entrySet());
        Collections.sort(sortedList, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });

        TestDatabase db = TestDatabase.getInstance(getAppContext());
        db.beginTransaction();
        try {
            for (Map.Entry<String, Integer> entry : sortedList) {
                WordCount wc = new WordCount();
                wc.mWord = entry.getKey();
                wc.mCount = entry.getValue();
                db.getWordCountDao().insertWordCount(wc);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        Log.d("Reduce", "Reduction finished");
    }
}
