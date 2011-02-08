/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.support.v4.util;

import android.util.Log;

import java.io.Writer;

/**
 * Useful logging utility that is not available on all versions of Android.
 */
public class LogWriter extends Writer {
    private final String mTag;
    private StringBuilder mBuilder = new StringBuilder(128);

    /**
     * Create a new Writer that sends to the log with the given priority
     * and tag.
     *
     * @param priority The desired log priority:
     * {@link android.util.Log#VERBOSE Log.VERBOSE},
     * {@link android.util.Log#DEBUG Log.DEBUG},
     * {@link android.util.Log#INFO Log.INFO},
     * {@link android.util.Log#WARN Log.WARN}, or
     * {@link android.util.Log#ERROR Log.ERROR}.
     * @param tag A string tag to associate with each printed log statement.
     */
    public LogWriter(String tag) {
        mTag = tag;
    }

    @Override public void close() {
        flushBuilder();
    }

    @Override public void flush() {
        flushBuilder();
    }

    @Override public void write(char[] buf, int offset, int count) {
        for(int i = 0; i < count; i++) {
            char c = buf[offset + i];
            if ( c == '\n') {
                flushBuilder();
            }
            else {
                mBuilder.append(c);
            }
        }
    }

    private void flushBuilder() {
        if (mBuilder.length() > 0) {
            Log.d(mTag, mBuilder.toString());
            mBuilder.delete(0, mBuilder.length());
        }
    }
}
