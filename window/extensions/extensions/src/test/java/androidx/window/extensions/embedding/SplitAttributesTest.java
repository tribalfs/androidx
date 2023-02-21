/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.window.extensions.embedding;

import static androidx.window.extensions.embedding.SplitAttributes.SplitType.RatioSplitType.splitEqually;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.graphics.Color;

import androidx.test.filters.SmallTest;
import androidx.window.extensions.embedding.SplitAttributes.LayoutDirection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Test for {@link SplitAttributes} */
@SmallTest
@RunWith(RobolectricTestRunner.class)
public class SplitAttributesTest {
    @Test
    public void testSplitAttributesEquals() {
        final SplitAttributes layout1 = new SplitAttributes.Builder()
                .setSplitType(splitEqually())
                .setLayoutDirection(LayoutDirection.LOCALE)
                .setAnimationBackgroundColor(SplitAttributes.DEFAULT_ANIMATION_BACKGROUND_COLOR)
                .build();
        final SplitAttributes layout2 = new SplitAttributes.Builder()
                .setSplitType(new SplitAttributes.SplitType.HingeSplitType(splitEqually()))
                .setLayoutDirection(LayoutDirection.LOCALE)
                .setAnimationBackgroundColor(SplitAttributes.DEFAULT_ANIMATION_BACKGROUND_COLOR)
                .build();
        final SplitAttributes layout3 = new SplitAttributes.Builder()
                .setSplitType(new SplitAttributes.SplitType.HingeSplitType(splitEqually()))
                .setLayoutDirection(LayoutDirection.TOP_TO_BOTTOM)
                .setAnimationBackgroundColor(SplitAttributes.DEFAULT_ANIMATION_BACKGROUND_COLOR)
                .build();
        final SplitAttributes layout4 = new SplitAttributes.Builder()
                .setSplitType(new SplitAttributes.SplitType.HingeSplitType(splitEqually()))
                .setLayoutDirection(LayoutDirection.TOP_TO_BOTTOM)
                .setAnimationBackgroundColor(Color.BLUE)
                .build();
        final SplitAttributes layout5 = new SplitAttributes.Builder()
                .setSplitType(new SplitAttributes.SplitType.HingeSplitType(splitEqually()))
                .setLayoutDirection(LayoutDirection.TOP_TO_BOTTOM)
                .setAnimationBackgroundColor(Color.BLUE)
                .build();

        assertNotEquals(layout1, layout2);
        assertNotEquals(layout1.hashCode(), layout2.hashCode());

        assertNotEquals(layout2, layout3);
        assertNotEquals(layout2.hashCode(), layout3.hashCode());

        assertNotEquals(layout3, layout1);
        assertNotEquals(layout3.hashCode(), layout1.hashCode());

        assertNotEquals(layout4, layout3);
        assertNotEquals(layout4.hashCode(), layout3.hashCode());

        assertEquals(layout4, layout5);
        assertEquals(layout4.hashCode(), layout5.hashCode());
    }

    @Test
    public void testSplitTypeEquals() {
        final SplitAttributes.SplitType[] splitTypes = new SplitAttributes.SplitType[]{
                new SplitAttributes.SplitType.ExpandContainersSplitType(),
                new SplitAttributes.SplitType.RatioSplitType(0.3f),
                splitEqually(),
                new SplitAttributes.SplitType.HingeSplitType(splitEqually()),
                new SplitAttributes.SplitType.HingeSplitType(
                        new SplitAttributes.SplitType.ExpandContainersSplitType()
                ),
        };

        for (int i = 0; i < splitTypes.length; i++) {
            for (int j = 0; j < splitTypes.length; j++) {
                final SplitAttributes.SplitType splitType0 = splitTypes[i];
                final SplitAttributes.SplitType splitType1 = splitTypes[j];
                if (i == j) {
                    assertEquals(splitType0, splitType1);
                    assertEquals(splitType0.hashCode(), splitType1.hashCode());
                } else {
                    assertNotEquals(splitType0, splitType1);
                    assertNotEquals(splitType0.hashCode(), splitType1.hashCode());
                }
            }
        }
    }
}
