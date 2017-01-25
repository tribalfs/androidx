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

package android.support.v4.graphics;

import static org.junit.Assert.assertEquals;

import android.graphics.Paint;
import android.support.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
@SmallTest
public class PaintCompatHasGlyphTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"B", true},
                {"\uDB3F\uDFFD", false},
                {"☺", true},
                {"Hello", false},
                {"\u0020", true},  // white space
                {"\t\t\t", false},  // more white space
        });
    }

    private final String mTestString;
    private final boolean mExpectedResult;

    public PaintCompatHasGlyphTest(String testString, boolean expectedResult) {
        mTestString = testString;
        mExpectedResult = expectedResult;
    }

    @Test
    public void testHasGlyph() {
        assertEquals(mExpectedResult, PaintCompat.hasGlyph(new Paint(), mTestString));
    }
}
