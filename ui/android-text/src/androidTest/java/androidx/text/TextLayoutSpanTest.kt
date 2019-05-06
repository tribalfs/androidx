/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.text

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ScaleXSpan
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.text.style.BaselineShiftSpan
import androidx.text.style.SkewXSpan
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.spy
import org.mockito.stubbing.Answer

@RunWith(JUnit4::class)
@SmallTest
class TextLayoutSpanTest {
    lateinit var sampleTypeface: Typeface
    @Before
    fun setup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // This sample font provides the following features:
        // 1. The width of most of visible characters equals to font size.
        // 2. The LTR/RTL characters are rendered as ▶/◀.
        // 3. The fontMetrics passed to TextPaint has descend - ascend equal to 1.2 * fontSize.
        // 4. The fontMetrics passed to TextPaint has ascend equal to fontSize.
        sampleTypeface = Typeface.createFromAsset(
            instrumentation.context.assets, "sample_font.ttf")!!
    }

    @Test
    fun baselineShiftSpan_updateMeasureStateNestTest() {
        val text = SpannableString("abc")
        val fontSize = 20

        val spanOutterMult = 0.5f
        val spanOutter = spy(BaselineShiftSpan(spanOutterMult))
        val spanInnerMult = 0.3f
        val spanInner = spy(BaselineShiftSpan(spanInnerMult))

        text.setSpan(spanOutter, 1, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(spanInner, 2, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // first baselineShiftSpan is applied
        var expectShift = (-fontSize * spanOutterMult).toInt()
        doAnswer(updatePaintAnswer(baselineShift = expectShift))
            .`when`(spanOutter).updateMeasureState(ArgumentMatchers.any())

        // second baselineShiftSpan is applied
        expectShift = (-fontSize * (spanOutterMult + spanInnerMult)).toInt()
        doAnswer(updatePaintAnswer(baselineShift = expectShift))
            .`when`(spanInner).updateMeasureState(ArgumentMatchers.any())

        val paint = simplePaint(fontSize.toFloat())
        TextLayout(
            text,
            100f, // nit for this test
            paint
        )
    }

    @Test
    fun baselineShiftSpan_updateDrawStateNestTest() {
        val text = SpannableString("abc")
        val fontSize = 20

        val spanOutterMult = 0.5f
        val spanOutter = spy(BaselineShiftSpan(spanOutterMult))
        val spanInnerMult = 0.3f
        val spanInner = spy(BaselineShiftSpan(spanInnerMult))

        text.setSpan(spanOutter, 1, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(spanInner, 2, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // first baselineShiftSpan is applied
        var expectShift = (-fontSize * spanOutterMult).toInt()
        doAnswer(updatePaintAnswer(baselineShift = expectShift))
            .`when`(spanOutter).updateDrawState(ArgumentMatchers.any())

        // second baselineShiftSpan is applied
        expectShift = (-fontSize * (spanOutterMult + spanInnerMult)).toInt()
        doAnswer(updatePaintAnswer(baselineShift = expectShift))
            .`when`(spanInner).updateDrawState(ArgumentMatchers.any())

        val paint = simplePaint(fontSize.toFloat())
        TextLayout(
            text,
            100f, // nit for this test
            paint
        )
    }

    @Test
    fun skewXSpan_updateDrawStateNestTest() {
        val text = SpannableString("abc")

        val skewXOutter = 0.3f
        val spanOutter = spy(SkewXSpan(skewXOutter))
        val skewXInner = 0.5f
        val spanInner = spy(SkewXSpan(skewXInner))

        text.setSpan(spanOutter, 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(spanInner, 0, text.length / 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        doAnswer(updatePaintAnswer(skewX = skewXOutter))
            .`when`(spanOutter).updateDrawState(ArgumentMatchers.any())
        doAnswer(updatePaintAnswer(skewX = skewXInner + skewXOutter))
            .`when`(spanInner).updateDrawState(ArgumentMatchers.any())

        TextLayout(
            text,
            100f, // nit for this test
            simplePaint()
        )
    }

    @Test
    fun skewXSpan_updateMeasureStateNestTest() {
        val text = SpannableString("abc")

        val skewXOutter = 0.3f
        val spanOutter = spy(SkewXSpan(skewXOutter))
        val skewXInner = 0.5f
        val spanInner = spy(SkewXSpan(skewXInner))

        text.setSpan(spanOutter, 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(spanInner, 0, text.length / 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        doAnswer(updatePaintAnswer(skewX = skewXOutter))
            .`when`(spanOutter).updateMeasureState(ArgumentMatchers.any())
        doAnswer(updatePaintAnswer(skewX = skewXInner + skewXOutter))
            .`when`(spanInner).updateMeasureState(ArgumentMatchers.any())

        TextLayout(
            text,
            100f, // nit for this test
            simplePaint()
        )
    }

    @Test
    fun scaleXSpan_updateDrawStateNestTest() {
        val text = SpannableString("abc")

        val scaleXOutter = 0.5f
        val spanOutter = spy(ScaleXSpan(scaleXOutter))
        val scaleXInner = 0.3f
        val spanInner = spy(ScaleXSpan(scaleXInner))

        text.setSpan(spanOutter, 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(spanInner, 0, text.length / 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        doAnswer(updatePaintAnswer(scaleX = scaleXOutter))
            .`when`(spanOutter).updateDrawState(ArgumentMatchers.any())
        doAnswer(updatePaintAnswer(scaleX = scaleXInner * scaleXOutter))
            .`when`(spanInner).updateDrawState(ArgumentMatchers.any())

        TextLayout(
            text,
            100f, // nit for this test
            simplePaint()
        )
    }

    @Test
    fun scaleXSpan_updateMeasureStateNestTest() {
        val text = SpannableString("abc")

        val scaleXOutter = 0.5f
        val spanOutter = spy(ScaleXSpan(scaleXOutter))
        val scaleXInner = 0.3f
        val spanInner = spy(ScaleXSpan(scaleXInner))

        text.setSpan(spanOutter, 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(spanInner, 0, text.length / 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        doAnswer(updatePaintAnswer(scaleX = scaleXOutter))
            .`when`(spanOutter).updateMeasureState(ArgumentMatchers.any())
        doAnswer(updatePaintAnswer(scaleX = scaleXInner * scaleXOutter))
            .`when`(spanInner).updateMeasureState(ArgumentMatchers.any())

        TextLayout(
            text,
            100f, // nit for this test
            simplePaint()
        )
    }

    // check the paint after modified by updateMeasureState/updateDrawState
    private fun updatePaintAnswer(
        baselineShift: Int? = null,
        skewX: Float? = null,
        scaleX: Float? = null
    ): Answer<Any> {
        return Answer { invoc ->
            val ret = invoc.callRealMethod()
            val paint = invoc.getArgument(0) as TextPaint
            baselineShift?.let {
                assertThat(paint.baselineShift, equalTo(it))
            }
            skewX?.let {
                assertThat(paint.textSkewX, equalTo(it))
            }
            scaleX?.let {
                assertThat(paint.textScaleX, equalTo(it))
            }
            ret
        }
    }

    private fun simplePaint(textSize: Float? = null): TextPaint {
        val textPaint = TextPaint()
        textPaint.typeface = sampleTypeface
        textSize?.let {
            textPaint.textSize = it
        }
        return textPaint
    }
}