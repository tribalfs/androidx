/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.emoji2.emojipicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.graphics.applyCanvas

/**
 * A customized view to support drawing emojis asynchronously.
 */
internal class EmojiView(context: Context) : View(context) {
    companion object {
        private const val EMOJI_DRAW_TEXT_SIZE_SP = 30
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            EMOJI_DRAW_TEXT_SIZE_SP.toFloat(),
            context.resources.displayMetrics
        )
    }

    private val offscreenCanvasBitmap: Bitmap = with(textPaint.fontMetricsInt) {
        val size = bottom - top
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(size, size)
    }

    override fun draw(canvas: Canvas?) {
        super.draw(canvas)
        canvas?.run {
            save()
            scale(
                width.toFloat() / offscreenCanvasBitmap.width,
                height.toFloat() / offscreenCanvasBitmap.height
            )
            drawBitmap(offscreenCanvasBitmap, 0f, 0f, null)
            restore()
        }
    }

    var emoji: CharSequence? = null
        set(value) {
            field = value
            offscreenCanvasBitmap.eraseColor(Color.TRANSPARENT)
            if (value != null) {
                post {
                    drawEmoji(value)
                    invalidate()
                }
            }
        }

    private fun drawEmoji(emoji: CharSequence) {
        if (emoji == this.emoji) {
            offscreenCanvasBitmap.applyCanvas {
                if (emoji is Spanned) {
                    createStaticLayout(emoji, width).draw(this)
                } else {
                    val textWidth = textPaint.measureText(emoji, 0, emoji.length)
                    drawText(
                        emoji,
                        /* start = */ 0,
                        /* end = */ emoji.length,
                        /* x = */ (width - textWidth) / 2,
                        /* y = */ -textPaint.fontMetrics.top,
                        textPaint,
                    )
                }
            }
        }
    }

    @RequiresApi(23)
    internal object Api23Impl {
        fun createStaticLayout(emoji: Spanned, textPaint: TextPaint, width: Int): StaticLayout =
            StaticLayout.Builder.obtain(
                emoji, 0, emoji.length, textPaint, width
            ).apply {
                setAlignment(Layout.Alignment.ALIGN_CENTER)
                setLineSpacing(/* spacingAdd = */ 0f, /* spacingMult = */ 1f)
                setIncludePad(false)
            }.build()
    }

    private fun createStaticLayout(emoji: Spanned, width: Int): StaticLayout {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Api23Impl.createStaticLayout(emoji, textPaint, width)
        } else {
            @Suppress("DEPRECATION")
            return StaticLayout(
                emoji,
                textPaint,
                width,
                Layout.Alignment.ALIGN_CENTER,
                /* spacingmult = */ 1f,
                /* spacingadd = */ 0f,
                /* includepad = */ false,
            )
        }
    }
}
