/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.appcompat.widget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.Instrumentation;
import android.widget.TextView;

import androidx.appcompat.testutils.BaseTestActivity;
import androidx.emoji2.text.EmojiCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@MediumTest
public abstract class AppCompatBaseTextViewEmojiTest<ActivityType extends BaseTestActivity,
        ViewType extends TextView> {

    @Rule
    public final ActivityTestRule<ActivityType> mActivityTestRule;
    public EmojiCompat mEmojiCompatMock;
    public Instrumentation mInstrumentation;

    public AppCompatBaseTextViewEmojiTest(Class<ActivityType> clazz) {
        mActivityTestRule = new ActivityTestRule<>(clazz, false, false);
    }

    abstract boolean isEmojiCompatEnabled(ViewType view);
    abstract void setEmojiCompatEnabled(ViewType view, boolean isEnabled);

    @Before
    public void ensureEmojiInitialized() {
        resetEmojiCompatToNewMock();
        mActivityTestRule.launchActivity(null);
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    @After
    public void cleanupEmojiCompat() {
        EmojiCompat.reset((EmojiCompat) null);
    }

    public void resetEmojiCompatToNewMock() {
        mEmojiCompatMock = mock(EmojiCompat.class);
        when(mEmojiCompatMock.getLoadState()).thenReturn(EmojiCompat.LOAD_STATE_FAILED);
        EmojiCompat.reset(mEmojiCompatMock);
    }

    @Test
    public void byDefault_setText_callsEmojiCompat() {
        resetEmojiCompatToNewMock();
        ViewType subject = mActivityTestRule.getActivity()
                .findViewById(androidx.appcompat.test.R.id.emoji_default);
        mInstrumentation.runOnMainSync(() -> {
            subject.setText("Test text");
            verify(mEmojiCompatMock, atLeastOnce()).getLoadState();
        });
    }

    @Test
    public void whenEnabled_setText_callsProcess() {
        resetEmojiCompatToNewMock();
        ViewType subject = mActivityTestRule.getActivity()
                .findViewById(androidx.appcompat.test.R.id.emoji_enabled);
        mInstrumentation.runOnMainSync(() -> {
            String expected = "Test text";
            subject.setText(expected);
            verify(mEmojiCompatMock, atLeastOnce()).getLoadState();
        });
    }

    @Test
    public void whenDisabled_noCalls() {
        resetEmojiCompatToNewMock();
        ViewType subject = mActivityTestRule.getActivity()
                .findViewById(androidx.appcompat.test.R.id.emoji_disabled);
        String expected = "Test text";
        subject.setText(expected);

        verifyNoMoreInteractions(mEmojiCompatMock);
    }

    @Test
    public void whenReEnabled_callsProcess() throws Throwable {
        resetEmojiCompatToNewMock();
        ViewType subject = mActivityTestRule.getActivity()
                .findViewById(androidx.appcompat.test.R.id.emoji_disabled);
        String expected = "Some text";
        mInstrumentation.runOnMainSync(() -> {
            subject.setText(expected);
            verifyNoMoreInteractions(mEmojiCompatMock);

            setEmojiCompatEnabled(subject, true);
        });
        verify(mEmojiCompatMock, atLeastOnce()).getLoadState();
    }

    @Test
    public void whenNotConfigured_andDisabled_doesNotEnable_whenConfigured() throws Throwable {
        EmojiCompat.reset((EmojiCompat) null);
        mActivityTestRule.finishActivity();
        mActivityTestRule.launchActivity(null);

        ActivityType activity = mActivityTestRule.getActivity();
        ViewType disabledInAdvance =
                activity.findViewById(androidx.appcompat.test.R.id.emoji_disabled);
        ViewType enabledInAdvance =
                activity.findViewById(androidx.appcompat.test.R.id.emoji_enabled);
        ViewType defaultEmoji =
                activity.findViewById(androidx.appcompat.test.R.id.emoji_default);

        mInstrumentation.runOnMainSync(() -> {
            setEmojiCompatEnabled(enabledInAdvance, false);
            setEmojiCompatEnabled(defaultEmoji, false);
        });
        // now: confirm no interactions with EmojiCompat with all text views disabled
        resetEmojiCompatToNewMock();
        // set the filters without calling enabled to avoid allowing re-set of disabled to update
        // the disabled state
        disabledInAdvance.setFilters(disabledInAdvance.getFilters());
        disabledInAdvance.setText("Some text");
        enabledInAdvance.setFilters(enabledInAdvance.getFilters());
        enabledInAdvance.setText("Some text");
        defaultEmoji.setFilters(defaultEmoji.getFilters());
        defaultEmoji.setText("Some text");
        verifyNoMoreInteractions(mEmojiCompatMock);
    }

    @Test
    public void whenNotConfigured_callingEnabled_afterConfigure_enablesEmoji() throws Throwable {
        EmojiCompat.reset((EmojiCompat) null);
        mActivityTestRule.finishActivity();
        mActivityTestRule.launchActivity(null);

        ActivityType activity = mActivityTestRule.getActivity();
        ViewType enabledInAdvance =
                activity.findViewById(androidx.appcompat.test.R.id.emoji_enabled);

        resetEmojiCompatToNewMock();
        mInstrumentation.runOnMainSync(() -> {
            setEmojiCompatEnabled(enabledInAdvance, true);
            enabledInAdvance.setText("Some text");
        });

        verify(mEmojiCompatMock, atLeastOnce()).getLoadState();
    }

    @Test
    public void getEnabled() throws Throwable {
        ActivityType activity = mActivityTestRule.getActivity();
        ViewType disabledInAdvance =
                activity.findViewById(androidx.appcompat.test.R.id.emoji_disabled);
        ViewType enabledInAdvance =
                activity.findViewById(androidx.appcompat.test.R.id.emoji_enabled);
        ViewType defaultEmoji =
                activity.findViewById(androidx.appcompat.test.R.id.emoji_default);

        assertFalse(isEmojiCompatEnabled(disabledInAdvance));
        assertTrue(isEmojiCompatEnabled(enabledInAdvance));
        assertTrue(isEmojiCompatEnabled(defaultEmoji));

        mInstrumentation.runOnMainSync(() -> {
            setEmojiCompatEnabled(defaultEmoji, false);
        });
        assertFalse(isEmojiCompatEnabled(defaultEmoji));
    }

}
