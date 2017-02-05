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
package android.support.text.emoji;

import static android.support.text.emoji.util.Emoji.EMOJI_FLAG;
import static android.support.text.emoji.util.Emoji.EMOJI_WITH_ZWJ;
import static android.support.text.emoji.util.EmojiMatcher.hasEmoji;
import static android.support.text.emoji.util.EmojiMatcher.hasEmojiCount;

import static junit.framework.Assert.assertFalse;
import static junit.framework.TestCase.assertEquals;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.text.emoji.util.Emoji;
import android.support.text.emoji.util.TestString;
import android.text.Editable;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.view.inputmethod.InputConnection;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SoftDeleteTest {
    private InputConnection mInputConnection;
    private TestString mTestString;
    private Editable mEditable;

    @BeforeClass
    public static void setupEmojiCompat() {
        EmojiCompat.reset(TestConfigBuilder.config());
    }

    @Before
    public void setup() {
        mInputConnection = mock(InputConnection.class);
        mTestString = new TestString(Emoji.EMOJI_WITH_ZWJ).withPrefix().withSuffix();
        mEditable = new SpannableStringBuilder(mTestString.toString());
        EmojiCompat.get().process(mEditable);
        assertThat(mEditable, hasEmojiCount(1));
        assertThat(mEditable, hasEmoji(EMOJI_WITH_ZWJ));
    }

    @Test
    public void testDelete_doesNotDelete_whenSelectionIsUndefined() {
        // no selection is set on editable
        assertFalse(EmojiCompat.handleDeleteSurroundingText(mInputConnection, mEditable, 1, 0,
                false));

        assertThat(mEditable, hasEmoji(EMOJI_WITH_ZWJ));
        assertEquals(mTestString.toString(), mEditable.toString());
    }

    @Test
    public void testDelete_doesNotDelete_whenThereIsSelectionLongerThanZero() {
        Selection.setSelection(mEditable, mTestString.emojiStartIndex(),
                mTestString.emojiEndIndex() + 1);

        assertFalse(EmojiCompat.handleDeleteSurroundingText(mInputConnection, mEditable, 1, 0,
                false));

        assertThat(mEditable, hasEmoji(EMOJI_WITH_ZWJ));
        assertEquals(mTestString.toString(), mEditable.toString());
    }

    @Test
    public void testDelete_withNullEditable() {
        Selection.setSelection(mEditable, mTestString.emojiEndIndex());

        assertFalse(EmojiCompat.handleDeleteSurroundingText(mInputConnection, null, 1, 0, false));

        assertThat(mEditable, hasEmoji(EMOJI_WITH_ZWJ));
        assertEquals(mTestString.toString(), mEditable.toString());
    }

    @Test
    public void testDelete_withNullInputConnection() {
        Selection.setSelection(mEditable, mTestString.emojiEndIndex());

        assertFalse(EmojiCompat.handleDeleteSurroundingText(null, mEditable, 1, 0, false));

        assertThat(mEditable, hasEmoji(EMOJI_WITH_ZWJ));
        assertEquals(mTestString.toString(), mEditable.toString());
    }

    @Test
    public void testDelete_withInvalidLength() {
        Selection.setSelection(mEditable, mTestString.emojiEndIndex());

        assertFalse(EmojiCompat.handleDeleteSurroundingText(mInputConnection, mEditable, -1, 0,
                false));

        assertThat(mEditable, hasEmoji(EMOJI_WITH_ZWJ));
        assertEquals(mTestString.toString(), mEditable.toString());
    }

    @Test
    public void testDelete_withInvalidAfterLength() {
        Selection.setSelection(mEditable, mTestString.emojiEndIndex());

        assertFalse(EmojiCompat.handleDeleteSurroundingText(mInputConnection, mEditable, 0, -1,
                false));

        assertThat(mEditable, hasEmoji(EMOJI_WITH_ZWJ));
        assertEquals(mTestString.toString(), mEditable.toString());
    }

    @Test
    public void testDelete_backward() {
        Selection.setSelection(mEditable, mTestString.emojiEndIndex());

        // backwards delete 1 character, it will delete the emoji
        assertTrue(EmojiCompat.handleDeleteSurroundingText(mInputConnection, mEditable, 1, 0,
                false));

        assertThat(mEditable, not(hasEmoji()));
        assertEquals(new TestString().withPrefix().withSuffix().toString(), mEditable.toString());
    }

    @Test
    public void testDelete_backward_inCodepoints() {
        Selection.setSelection(mEditable, mTestString.emojiEndIndex());

        // backwards delete 1 character, it will delete the emoji
        assertTrue(EmojiCompat.handleDeleteSurroundingText(mInputConnection, mEditable, 1, 0,
                true));

        assertThat(mEditable, not(hasEmoji()));
        assertEquals(new TestString().withPrefix().withSuffix().toString(), mEditable.toString());
    }

    @Test
    public void testDelete_forward() {
        Selection.setSelection(mEditable, mTestString.emojiStartIndex());

        // forward delete 1 character, it will dele the emoji.
        assertTrue(EmojiCompat.handleDeleteSurroundingText(mInputConnection, mEditable, 0, 1,
                false));

        assertThat(mEditable, not(hasEmoji()));
        assertEquals(new TestString().withPrefix().withSuffix().toString(), mEditable.toString());
    }

    @Test
    public void testDelete_forward_inCodepoints() {
        Selection.setSelection(mEditable, mTestString.emojiStartIndex());

        // forward delete 1 codepoint, it will delete the emoji.
        assertTrue(EmojiCompat.handleDeleteSurroundingText(mInputConnection, mEditable, 0, 1,
                false));

        assertThat(mEditable, not(hasEmoji()));
        assertEquals(new TestString().withPrefix().withSuffix().toString(), mEditable.toString());
    }

    @Test
    public void testDelete_backward_doesNotDeleteWhenSelectionAtCharSequenceStart() {
        // make sure selection at 0 does not do something weird for backward delete
        Selection.setSelection(mEditable, 0);

        assertFalse(EmojiCompat.handleDeleteSurroundingText(mInputConnection, mEditable, 1, 0,
                false));

        assertThat(mEditable, hasEmoji());
        assertEquals(mTestString.toString(), mEditable.toString());
    }

    @Test
    public void testDelete_forward_doesNotDeleteWhenSelectionAtCharSequenceEnd() {
        // make sure selection at end does not do something weird for forward delete
        Selection.setSelection(mEditable, mTestString.emojiEndIndex());

        assertFalse(EmojiCompat.handleDeleteSurroundingText(mInputConnection, mEditable, 0, 1,
                false));

        assertThat(mEditable, hasEmoji());
        assertEquals(mTestString.toString(), mEditable.toString());
    }

    @Test
    public void testDelete_withMultipleCharacters() {
        // prepare string as abc[emoji]def
        mTestString = new TestString(EMOJI_FLAG);
        mEditable = new SpannableStringBuilder("abc" + mTestString.toString() + "def");
        EmojiCompat.get().process(mEditable);

        // set the selection in the middle of emoji
        Selection.setSelection(mEditable, "abc".length() + EMOJI_FLAG.charCount() / 2);

        // delete 4 characters forward, 4 character backwards
        assertTrue(
                EmojiCompat.handleDeleteSurroundingText(mInputConnection, mEditable, 4, 4, false));

        assertThat(mEditable, not(hasEmoji()));
        assertEquals("af", mEditable.toString());
    }

    @Test
    public void testDelete_withMultipleCodepoints() {
        // prepare string as abc[emoji]def
        mTestString = new TestString(EMOJI_FLAG);
        mEditable = new SpannableStringBuilder("abc" + mTestString.toString() + "def");
        EmojiCompat.get().process(mEditable);

        // set the selection in the middle of emoji
        Selection.setSelection(mEditable, "abc".length() + EMOJI_FLAG.charCount() / 2);

        // delete 3 codepoints forward, 3 codepoints backwards
        assertTrue(
                EmojiCompat.handleDeleteSurroundingText(mInputConnection, mEditable, 3, 3, true));

        assertThat(mEditable, not(hasEmoji()));
        assertEquals("af", mEditable.toString());
    }

    @Test
    public void testDelete_withMultipleCharacters_withDeleteLengthLongerThanString() {
        // prepare string as abc[emoji]def
        mTestString = new TestString(EMOJI_FLAG);
        mEditable = new SpannableStringBuilder("abc" + mTestString.toString() + "def");
        EmojiCompat.get().process(mEditable);

        // set the selection in the middle of emoji
        Selection.setSelection(mEditable, "abc".length() + EMOJI_FLAG.charCount() / 2);

        assertTrue(EmojiCompat.handleDeleteSurroundingText(mInputConnection, mEditable, 100, 100,
                false));

        assertThat(mEditable, not(hasEmoji()));
        assertEquals("", mEditable.toString());
    }

    @Test
    public void testDelete_withMultipleCodepoints_withDeleteLengthLongerThanString() {
        // prepare string as abc[emoji]def
        mTestString = new TestString(EMOJI_FLAG);
        mEditable = new SpannableStringBuilder("abc" + mTestString.toString() + "def");
        EmojiCompat.get().process(mEditable);

        // set the selection in the middle of emoji
        Selection.setSelection(mEditable, "abc".length() + EMOJI_FLAG.charCount() / 2);

        assertTrue(EmojiCompat.handleDeleteSurroundingText(mInputConnection, mEditable, 100, 100,
                true));

        assertThat(mEditable, not(hasEmoji()));
        assertEquals("", mEditable.toString());
    }
}
