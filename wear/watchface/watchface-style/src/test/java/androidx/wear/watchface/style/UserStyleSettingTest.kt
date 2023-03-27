/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.wear.watchface.style

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.style.UserStyleSetting.BooleanUserStyleSetting.BooleanOption
import androidx.wear.watchface.style.UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay
import androidx.wear.watchface.style.UserStyleSetting.DoubleRangeUserStyleSetting
import androidx.wear.watchface.style.UserStyleSetting.DoubleRangeUserStyleSetting.DoubleRangeOption
import androidx.wear.watchface.style.UserStyleSetting.ListUserStyleSetting.ListOption
import androidx.wear.watchface.style.UserStyleSetting.LongRangeUserStyleSetting.LongRangeOption
import androidx.wear.watchface.style.UserStyleSetting.Option
import androidx.wear.watchface.style.data.ComplicationOverlayWireFormat
import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import kotlin.test.assertFailsWith
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(StyleTestRunner::class)
public class UserStyleSettingTest {

    private fun doubleToOptionId(value: Double) =
        Option.Id(ByteArray(8).apply { ByteBuffer.wrap(this).putDouble(value) })

    private fun byteArrayToDouble(value: ByteArray) = ByteBuffer.wrap(value).double

    private val icon_100x100 =
        Icon.createWithBitmap(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888))

    private val icon_10x10 =
        Icon.createWithBitmap(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))

    @Test
    public fun rangedUserStyleSetting_getOptionForId_returns_default_for_bad_input() {
        val defaultValue = 0.75
        val rangedUserStyleSetting =
            DoubleRangeUserStyleSetting(
                UserStyleSetting.Id("example_setting"),
                "Example Ranged Setting",
                "An example setting",
                null,
                0.0,
                1.0,
                listOf(WatchFaceLayer.BASE),
                defaultValue
            )

        assertThat(
                (rangedUserStyleSetting.getOptionForId(
                        Option.Id("not a number".encodeToByteArray())
                    ) as DoubleRangeOption)
                    .value
            )
            .isEqualTo(defaultValue)

        assertThat(
                (rangedUserStyleSetting.getOptionForId(Option.Id("-1".encodeToByteArray()))
                        as DoubleRangeOption)
                    .value
            )
            .isEqualTo(defaultValue)

        assertThat(
                (rangedUserStyleSetting.getOptionForId(Option.Id("10".encodeToByteArray()))
                        as DoubleRangeOption)
                    .value
            )
            .isEqualTo(defaultValue)
    }

    @Test
    public fun byteArrayConversion() {
        assertThat(BooleanOption.TRUE.value).isEqualTo(true)
        assertThat(BooleanOption.FALSE.value).isEqualTo(false)
        assertThat(DoubleRangeOption(123.4).value).isEqualTo(123.4)
        assertThat(LongRangeOption(1234).value).isEqualTo(1234)
    }

    @Test
    public fun rangedUserStyleSetting_getOptionForId() {
        val defaultValue = 0.75
        val rangedUserStyleSetting =
            DoubleRangeUserStyleSetting(
                UserStyleSetting.Id("example_setting"),
                "Example Ranged Setting",
                "An example setting",
                null,
                0.0,
                1.0,
                listOf(WatchFaceLayer.BASE),
                defaultValue
            )

        assertThat(
                byteArrayToDouble(
                    rangedUserStyleSetting.getOptionForId(doubleToOptionId(0.0)).id.value
                )
            )
            .isEqualTo(0.0)

        assertThat(
                byteArrayToDouble(
                    rangedUserStyleSetting.getOptionForId(doubleToOptionId(0.5)).id.value
                )
            )
            .isEqualTo(0.5)

        assertThat(
                byteArrayToDouble(
                    rangedUserStyleSetting.getOptionForId(doubleToOptionId(1.0)).id.value
                )
            )
            .isEqualTo(1.0)
    }

    @Test
    public fun maximumUserStyleSettingIdLength() {
        // OK.
        UserStyleSetting.Id("x".repeat(UserStyleSetting.Id.MAX_LENGTH))

        try {
            // Not OK.
            UserStyleSetting.Id("x".repeat(UserStyleSetting.Id.MAX_LENGTH + 1))
            fail("Should have thrown an exception")
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    public fun maximumOptionIdLength() {
        // OK.
        ListOption(
            Option.Id("x".repeat(Option.Id.MAX_LENGTH)),
            displayName = "test",
            screenReaderName = "test",
            icon = null
        )

        try {
            // Not OK.
            ListOption(
                Option.Id("x".repeat(Option.Id.MAX_LENGTH + 1)),
                displayName = "test",
                screenReaderName = "test",
                icon = null
            )
            fail("Should have thrown an exception")
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    public fun maximumCustomValueOptionSize() {
        // OK.
        UserStyleSetting.CustomValueUserStyleSetting.CustomValueOption(
            ByteArray(Option.Id.MAX_LENGTH)
        )

        try {
            // Not OK.
            UserStyleSetting.CustomValueUserStyleSetting.CustomValueOption(
                ByteArray(Option.Id.MAX_LENGTH + 1)
            )
            fail("Should have thrown an exception")
        } catch (e: Exception) {
            // Expected
        }
    }

    @SuppressLint("NewApi")
    @Test
    public fun maximumCustomValueOption2Size() {
        // OK.
        UserStyleSetting.LargeCustomValueUserStyleSetting.CustomValueOption(
            ByteArray(Option.Id.MAX_LENGTH + 1)
        )

        UserStyleSetting.LargeCustomValueUserStyleSetting.CustomValueOption(
            ByteArray(UserStyleSetting.LargeCustomValueUserStyleSetting.CustomValueOption.MAX_SIZE)
        )

        try {
            // Not OK.
            UserStyleSetting.LargeCustomValueUserStyleSetting.CustomValueOption(
                ByteArray(
                    UserStyleSetting.LargeCustomValueUserStyleSetting.CustomValueOption.MAX_SIZE + 1
                )
            )
            fail("Should have thrown an exception")
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    public fun equalsBasedOnId() {
        val setting =
            DoubleRangeUserStyleSetting(
                UserStyleSetting.Id("example_setting"),
                "Example Ranged Setting",
                "An example setting",
                null,
                0.0,
                1.0,
                listOf(WatchFaceLayer.BASE),
                0.1
            )
        val settingCopy =
            DoubleRangeUserStyleSetting(
                UserStyleSetting.Id("example_setting"),
                "Example Ranged Setting",
                "An example setting",
                null,
                0.0,
                1.0,
                listOf(WatchFaceLayer.BASE),
                0.1
            )
        val settings1ModifiedInfo =
            DoubleRangeUserStyleSetting(
                UserStyleSetting.Id("example_setting"),
                "Example Ranged Setting (modified)",
                "An example setting (modified)",
                null,
                0.0,
                100.0,
                listOf(WatchFaceLayer.BASE),
                3.0
            )
        val settings1ModifiedId =
            DoubleRangeUserStyleSetting(
                UserStyleSetting.Id("example_setting_modified"),
                "Example Ranged Setting",
                "An example setting",
                null,
                0.0,
                1.0,
                listOf(WatchFaceLayer.BASE),
                0.1
            )
        assertThat(setting).isEqualTo(setting)
        assertThat(setting).isEqualTo(settingCopy)
        assertThat(setting).isEqualTo(settings1ModifiedInfo)
        assertThat(setting).isNotEqualTo(settings1ModifiedId)
    }

    @Test
    public fun hashcodeBasedOnId() {
        val setting =
            DoubleRangeUserStyleSetting(
                UserStyleSetting.Id("example_setting"),
                "Example Ranged Setting",
                "An example setting",
                null,
                0.0,
                1.0,
                listOf(WatchFaceLayer.BASE),
                0.1
            )
        val settingCopy =
            DoubleRangeUserStyleSetting(
                UserStyleSetting.Id("example_setting"),
                "Example Ranged Setting",
                "An example setting",
                null,
                0.0,
                1.0,
                listOf(WatchFaceLayer.BASE),
                0.1
            )
        val settings1ModifiedInfo =
            DoubleRangeUserStyleSetting(
                UserStyleSetting.Id("example_setting"),
                "Example Ranged Setting (modified)",
                "An example setting (modified)",
                null,
                0.0,
                100.0,
                listOf(WatchFaceLayer.BASE),
                3.0
            )
        val settings1ModifiedId =
            DoubleRangeUserStyleSetting(
                UserStyleSetting.Id("example_setting_modified"),
                "Example Ranged Setting",
                "An example setting",
                null,
                0.0,
                1.0,
                listOf(WatchFaceLayer.BASE),
                0.1
            )
        assertThat(setting.hashCode()).isEqualTo(setting.hashCode())
        assertThat(setting.hashCode()).isEqualTo(settingCopy.hashCode())
        assertThat(setting.hashCode()).isEqualTo(settings1ModifiedInfo.hashCode())
        assertThat(setting.hashCode()).isNotEqualTo(settings1ModifiedId.hashCode())
    }

    @Test
    @Suppress("Deprecation")
    public fun noDuplicatedComplicationSlotOptions() {
        val leftComplicationSlot =
            UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(1)
        val rightComplicationSlot =
            UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(2)
        assertFailsWith<IllegalArgumentException>("should not allow duplicates") {
            UserStyleSetting.ComplicationSlotsUserStyleSetting(
                UserStyleSetting.Id("complication_location"),
                "Complication Location",
                "Configure the location of the complications on the watch face",
                icon = null,
                listOf(
                    UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption(
                        Option.Id("both"),
                        "left and right complications",
                        "left and right complications",
                        icon = null,
                        listOf(leftComplicationSlot, rightComplicationSlot),
                    ),
                    UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption(
                        Option.Id("left"),
                        "left complication",
                        "left complication",
                        icon = null,
                        listOf(leftComplicationSlot),
                    ),
                    UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption(
                        Option.Id("right"),
                        "right complication",
                        "right complication",
                        icon = null,
                        listOf(rightComplicationSlot),
                    ),
                    UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption(
                        Option.Id("both"),
                        "right and left complications",
                        "right and left complications",
                        icon = null,
                        listOf(rightComplicationSlot, leftComplicationSlot),
                    )
                ),
                WatchFaceLayer.ALL_WATCH_FACE_LAYERS
            )
        }
    }

    @Test
    public fun noDuplicatedListOptions() {
        assertFailsWith<IllegalArgumentException>("should not allow duplicates") {
            UserStyleSetting.ListUserStyleSetting(
                UserStyleSetting.Id("hands"),
                "Hands",
                "Configure the hands of the watch face",
                icon = null,
                listOf(
                    UserStyleSetting.ListUserStyleSetting.ListOption(
                        UserStyleSetting.Option.Id("plain"),
                        "plain hands",
                        "plain hands",
                        icon = null
                    ),
                    UserStyleSetting.ListUserStyleSetting.ListOption(
                        UserStyleSetting.Option.Id("florescent"),
                        "florescent hands",
                        "florescent hands",
                        icon = null
                    ),
                    UserStyleSetting.ListUserStyleSetting.ListOption(
                        UserStyleSetting.Option.Id("thick"),
                        "thick hands",
                        "thick hands",
                        icon = null
                    ),
                    UserStyleSetting.ListUserStyleSetting.ListOption(
                        UserStyleSetting.Option.Id("plain"),
                        "simple hands",
                        "simple hands",
                        icon = null
                    )
                ),
                WatchFaceLayer.ALL_WATCH_FACE_LAYERS
            )
        }
    }

    @Test
    public fun partial_ComplicationBounds_in_ComplicationOverlayWireFormat() {
        val wireFormat =
            ComplicationOverlayWireFormat(
                123,
                true,
                mapOf(
                    ComplicationType.SHORT_TEXT.toWireComplicationType() to
                        RectF(0.1f, 0.2f, 0.3f, 0.4f),
                    ComplicationType.LONG_TEXT.toWireComplicationType() to
                        RectF(0.5f, 0.6f, 0.7f, 0.8f)
                ),
                null
            )

        val overlay =
            UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
                wireFormat,
                mapOf(
                    ComplicationType.LONG_TEXT.toWireComplicationType() to
                        RectF(0.2f, 0.2f, 0.2f, 0.2f)
                )
            )
        val bounds = overlay.complicationSlotBounds!!.perComplicationTypeBounds

        // SHORT_TEXT and LONG_TEXT should match the input bounds
        assertThat(bounds[ComplicationType.SHORT_TEXT]).isEqualTo(RectF(0.1f, 0.2f, 0.3f, 0.4f))
        assertThat(bounds[ComplicationType.LONG_TEXT]).isEqualTo(RectF(0.5f, 0.6f, 0.7f, 0.8f))

        // All other types should have been backfilled with an empty rect.
        for (type in ComplicationType.values()) {
            if (type != ComplicationType.SHORT_TEXT && type != ComplicationType.LONG_TEXT) {
                assertThat(bounds[type]).isEqualTo(RectF())
            }
        }

        val margins = overlay.complicationSlotBounds!!.perComplicationTypeMargins

        // LONG_TEXT should match the input bounds
        assertThat(margins[ComplicationType.LONG_TEXT]).isEqualTo(RectF(0.2f, 0.2f, 0.2f, 0.2f))

        // All other types should have been backfilled with an empty rect.
        for (type in ComplicationType.values()) {
            if (type != ComplicationType.LONG_TEXT) {
                assertThat(margins[type]).isEqualTo(RectF())
            }
        }
    }

    @Test
    @Suppress("deprecation")
    public fun complicationSlotsOptionWireFormatRoundTrip() {
        val leftComplicationSlot =
            ComplicationSlotOverlay(1, nameResourceId = null, screenReaderNameResourceId = null)
        val rightComplicationSlot =
            ComplicationSlotOverlay(2, nameResourceId = null, screenReaderNameResourceId = null)
        val option =
            UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption(
                Option.Id("both"),
                "right and left complications",
                "right and left complications",
                icon = null,
                listOf(rightComplicationSlot, leftComplicationSlot),
            )

        val optionAfterRoundTrip =
            UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption(
                option.toWireFormat()
            )

        assertThat(option).isEqualTo(optionAfterRoundTrip)
        assertThat(optionAfterRoundTrip.complicationSlotOverlays)
            .containsExactly(
                ComplicationSlotOverlay(
                    1,
                    nameResourceId = null,
                    screenReaderNameResourceId = null
                ),
                ComplicationSlotOverlay(2, nameResourceId = null, screenReaderNameResourceId = null)
            )
    }
}
