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
package android.support.text.emoji.widget;

import android.os.Build;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.text.emoji.EmojiCompat;
import android.support.v4.util.Preconditions;
import android.text.method.KeyListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Utility class to enhance custom EditText widgets with {@link EmojiCompat}.
 * <p/>
 * <pre>
 * public class MyEmojiEditText extends EditText {
 *      public MyEmojiEditText(Context context) {
 *          super(context);
 *          init();
 *      }
 *      // ...
 *      private void init() {
 *          super.setKeyListener(getEmojiEditTextHelper().getKeyListener(getKeyListener()));
 *      }
 *
 *      {@literal @}Override
 *      public void setKeyListener(android.text.method.KeyListener keyListener) {
 *          super.setKeyListener(getEmojiEditTextHelper().getKeyListener(keyListener));
 *      }
 *
 *      {@literal @}Override
 *      public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
 *          InputConnection inputConnection = super.onCreateInputConnection(outAttrs);
 *          return getEmojiEditTextHelper().onCreateInputConnection(inputConnection, outAttrs);
 *      }
 *
 *      private EmojiEditTextHelper getEmojiEditTextHelper() {
 *          if (mEmojiEditTextHelper == null) {
 *              mEmojiEditTextHelper = new EmojiEditTextHelper(this);
 *          }
 *          return mEmojiEditTextHelper;
 *      }
 * }
 * </pre>
 *
 */
public final class EmojiEditTextHelper {

    private final HelperInternal mHelper;
    private int mMaxEmojiCount;

    /**
     * Default constructor.
     *
     * @param editText EditText instance
     */
    public EmojiEditTextHelper(@NonNull final EditText editText) {
        Preconditions.checkNotNull(editText, "editText cannot be null");
        mHelper = Build.VERSION.SDK_INT >= 19 ? new HelperInternal19(editText)
                : new HelperInternal();
    }

    /**
     * Set the maximum number of EmojiSpans to be added to a CharSequence. The number of spans in a
     * CharSequence affects the performance of the EditText insert/delete operations. Insert/delete
     * operations slow down as the number of spans increases.
     * <p/>
     *
     * @param maxEmojiCount maximum number of EmojiSpans to be added to a single CharSequence,
     *                      should be equal or greater than 0
     *
     * @see EmojiCompat#process(CharSequence, int, int, int)
     */
    public void setMaxEmojiCount(@IntRange(from = 0) int maxEmojiCount) {
        Preconditions.checkArgumentNonnegative(maxEmojiCount,
                "maxEmojiCount should be greater than 0");
        mMaxEmojiCount = maxEmojiCount;
        mHelper.setMaxEmojiCount(maxEmojiCount);
    }


    /**
     * Returns the maximum number of EmojiSpans to be added to a CharSequence.
     *
     * @see #setMaxEmojiCount(int)
     * @see EmojiCompat#process(CharSequence, int, int, int)
     */
    public int getMaxEmojiCount() {
        return mMaxEmojiCount;
    }

    /**
     * Attaches EmojiCompat KeyListener to the widget. Should be called from {@link
     * TextView#setKeyListener(KeyListener)}. Existing keyListener is wrapped into EmojiCompat
     * KeyListener. When used on devices running API 18 or below, this method returns
     * {@code keyListener} that is given as a parameter.
     *
     * @param keyListener KeyListener passed into {@link TextView#setKeyListener(KeyListener)}
     *
     * @return a new KeyListener instance that wraps {@code keyListener}.
     */
    @NonNull
    public KeyListener getKeyListener(@NonNull final KeyListener keyListener) {
        Preconditions.checkNotNull(keyListener, "keyListener cannot be null");
        return mHelper.getKeyListener(keyListener);
    }

    /**
     * Updates the InputConnection with emoji support. Should be called from {@link
     * TextView#onCreateInputConnection(EditorInfo)}. When used on devices running API 18 or below,
     * this method returns {@code inputConnection} that is given as a parameter.
     *
     * @param inputConnection InputConnection instance created by TextView
     * @param outAttrs        EditorInfo passed into
     *                        {@link TextView#onCreateInputConnection(EditorInfo)}
     *
     * @return a new InputConnection instance that wraps {@code inputConnection}
     */
    @NonNull
    public InputConnection onCreateInputConnection(@NonNull final InputConnection inputConnection,
            @NonNull final EditorInfo outAttrs) {
        Preconditions.checkNotNull(inputConnection, "inputConnection cannot be null");
        return mHelper.onCreateInputConnection(inputConnection, outAttrs);
    }

    private static class HelperInternal {

        KeyListener getKeyListener(@NonNull KeyListener keyListener) {
            return keyListener;
        }

        InputConnection onCreateInputConnection(@NonNull InputConnection inputConnection,
                @NonNull EditorInfo outAttrs) {
            return inputConnection;
        }

        public void setMaxEmojiCount(int maxEmojiCount) {
            // do nothing
        }
    }

    @RequiresApi(19)
    private static class HelperInternal19 extends HelperInternal {
        private final EditText mEditText;
        private final EmojiTextWatcher mTextWatcher;

        HelperInternal19(@NonNull EditText editText) {
            mEditText = editText;
            mTextWatcher = new EmojiTextWatcher(mEditText);
            mEditText.addTextChangedListener(mTextWatcher);
            mEditText.setEditableFactory(EmojiEditableFactory.getInstance());
        }

        @Override
        public void setMaxEmojiCount(int maxEmojiCount) {
            mTextWatcher.setMaxEmojiCount(maxEmojiCount);
        }

        @Override
        KeyListener getKeyListener(@NonNull final KeyListener keyListener) {
            if (keyListener instanceof EmojiKeyListener) {
                return keyListener;
            }
            return new EmojiKeyListener(keyListener);
        }

        @Override
        InputConnection onCreateInputConnection(@NonNull final InputConnection inputConnection,
                @NonNull final EditorInfo outAttrs) {
            if (inputConnection instanceof EmojiInputConnection) {
                return inputConnection;
            }
            return new EmojiInputConnection(mEditText, inputConnection, outAttrs);
        }
    }
}
