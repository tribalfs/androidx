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

package androidx.wear.complications.data

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.test.core.app.ApplicationProvider
import androidx.wear.complications.ParcelableSubject
import androidx.wear.complications.SharedRobolectricTestRunner
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(SharedRobolectricTestRunner::class)
public class AsWireComplicationDataTest {
    @Test
    public fun noDataComplicationData() {
        val data = NoDataComplicationData()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA).build()
            )
    }

    @Test
    public fun emptyComplicationData() {
        val data = EmptyComplicationData()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_EMPTY).build()
            )
    }

    @Test
    public fun notConfiguredComplicationData() {
        val data = NotConfiguredComplicationData()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NOT_CONFIGURED).build()
            )
    }

    @Test
    public fun shortTextComplicationData() {
        val data = ShortTextComplicationData.Builder("text".complicationText)
            .setTitle("title".complicationText)
            .setContentDescription("content description".complicationText)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(WireComplicationText.plainText("text"))
                    .setShortTitle(WireComplicationText.plainText("title"))
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .build()
            )
    }

    @Test
    public fun longTextComplicationData() {
        val data = LongTextComplicationData.Builder("text".complicationText)
            .setTitle("title".complicationText)
            .setContentDescription("content description".complicationText)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_LONG_TEXT)
                    .setLongText(WireComplicationText.plainText("text"))
                    .setLongTitle(WireComplicationText.plainText("title"))
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .build()
            )
    }

    @Test
    public fun rangedValueComplicationData() {
        val data = RangedValueComplicationData.Builder(value = 95f, min = 0f, max = 100f)
            .setTitle("battery".complicationText)
            .setContentDescription("content description".complicationText)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_RANGED_VALUE)
                    .setRangedValue(95f)
                    .setRangedMinValue(0f)
                    .setRangedMaxValue(100f)
                    .setShortTitle(WireComplicationText.plainText("battery"))
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .build()
            )
    }

    @Test
    public fun monochromaticImageComplicationData() {
        val icon = Icon.createWithContentUri("someuri")
        val image = MonochromaticImage.Builder(icon).build()
        val data = MonochromaticImageComplicationData.Builder(image)
            .setContentDescription("content description".complicationText).build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_ICON)
                    .setIcon(icon)
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .build()
            )
    }

    @Test
    public fun smallImageComplicationData() {
        val icon = Icon.createWithContentUri("someuri")
        val image = SmallImage.Builder(icon, SmallImageType.PHOTO).build()
        val data = SmallImageComplicationData.Builder(image)
            .setContentDescription("content description".complicationText).build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_SMALL_IMAGE)
                    .setSmallImage(icon)
                    .setSmallImageStyle(WireComplicationData.IMAGE_STYLE_PHOTO)
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .build()
            )
    }

    @Test
    public fun backgroundImageComplicationData() {
        val photoImage = Icon.createWithContentUri("someuri")
        val data = PhotoImageComplicationData.Builder(photoImage)
            .setContentDescription("content description".complicationText)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_LARGE_IMAGE)
                    .setLargeImage(photoImage)
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .build()
            )
    }

    @Test
    public fun noPermissionComplicationData() {
        val data = NoPermissionComplicationData.Builder()
            .setText("needs location".complicationText)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_PERMISSION)
                    .setShortText(WireComplicationText.plainText("needs location"))
                    .build()
            )
    }
}

@RunWith(SharedRobolectricTestRunner::class)
public class FromWireComplicationDataTest {
    @Test
    public fun noDataComplicationData() {
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA).build(),
            ComplicationType.NO_DATA
        )
    }

    @Test
    public fun emptyComplicationData() {
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_EMPTY).build(),
            ComplicationType.EMPTY
        )
    }

    @Test
    public fun notConfiguredComplicationData() {
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_NOT_CONFIGURED).build(),
            ComplicationType.NOT_CONFIGURED
        )
    }

    @Test
    public fun shortTextComplicationData() {
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_SHORT_TEXT)
                .setShortText(WireComplicationText.plainText("text"))
                .setShortTitle(WireComplicationText.plainText("title"))
                .setContentDescription(WireComplicationText.plainText("content description"))
                .build(),
            ComplicationType.SHORT_TEXT
        )
    }

    @Test
    public fun longTextComplicationData() {
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_LONG_TEXT)
                .setLongText(WireComplicationText.plainText("text"))
                .setLongTitle(WireComplicationText.plainText("title"))
                .setContentDescription(WireComplicationText.plainText("content description"))
                .build(),
            ComplicationType.LONG_TEXT
        )
    }

    @Test
    public fun rangedValueComplicationData() {
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_RANGED_VALUE)
                .setRangedValue(95f)
                .setRangedMinValue(0f)
                .setRangedMaxValue(100f)
                .setShortTitle(WireComplicationText.plainText("battery"))
                .setContentDescription(WireComplicationText.plainText("content description"))
                .build(),
            ComplicationType.RANGED_VALUE
        )
    }

    @Test
    public fun monochromaticImageComplicationData() {
        val icon = Icon.createWithContentUri("someuri")
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_ICON)
                .setIcon(icon)
                .setContentDescription(WireComplicationText.plainText("content description"))
                .build(),
            ComplicationType.MONOCHROMATIC_IMAGE
        )
    }

    @Test
    public fun smallImageComplicationData() {
        val icon = Icon.createWithContentUri("someuri")
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_SMALL_IMAGE)
                .setSmallImage(icon)
                .setSmallImageStyle(WireComplicationData.IMAGE_STYLE_PHOTO)
                .setContentDescription(WireComplicationText.plainText("content description"))
                .build(),
            ComplicationType.SMALL_IMAGE
        )
    }

    @Test
    public fun backgroundImageComplicationData() {
        val icon = Icon.createWithContentUri("someuri")
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_LARGE_IMAGE)
                .setLargeImage(icon)
                .setContentDescription(WireComplicationText.plainText("content description"))
                .build(),
            ComplicationType.PHOTO_IMAGE
        )
    }

    @Test
    public fun noPermissionComplicationData() {
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_NO_PERMISSION)
                .setShortText(WireComplicationText.plainText("needs location"))
                .build(),
            ComplicationType.NO_PERMISSION
        )
    }

    private fun assertRoundtrip(wireData: WireComplicationData, type: ComplicationType) {
        val data = wireData.asApiComplicationData()
        assertThat(data.type).isEqualTo(type)
        ParcelableSubject.assertThat(data.asWireComplicationData()).hasSameSerializationAs(wireData)
    }
}

@RunWith(SharedRobolectricTestRunner::class)
public class TapActionTest {
    private val mPendingIntent = PendingIntent.getBroadcast(
        ApplicationProvider.getApplicationContext(),
        0,
        Intent(),
        0
    )

    @Test
    public fun shortTextComplicationData() {
        assertThat(
            ShortTextComplicationData.Builder("text".complicationText)
                .setTapAction(mPendingIntent)
                .build().asWireComplicationData().tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun longTextComplicationData() {
        assertThat(
            LongTextComplicationData.Builder("text".complicationText)
                .setTapAction(mPendingIntent)
                .build().asWireComplicationData().tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun rangedValueComplicationData() {
        assertThat(
            RangedValueComplicationData.Builder(value = 95f, min = 0f, max = 100f)
                .setTapAction(mPendingIntent)
                .build().asWireComplicationData().tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun monochromaticImageComplicationData() {
        val icon = Icon.createWithContentUri("someuri")
        val image = MonochromaticImage.Builder(icon).build()
        assertThat(
            MonochromaticImageComplicationData.Builder(image)
                .setTapAction(mPendingIntent)
                .build()
                .asWireComplicationData()
                .tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun smallImageComplicationData() {
        val icon = Icon.createWithContentUri("someuri")
        val image = SmallImage.Builder(icon, SmallImageType.PHOTO).build()
        assertThat(
            SmallImageComplicationData.Builder(image).setTapAction(mPendingIntent).build()
                .asWireComplicationData()
                .tapAction
        ).isEqualTo(mPendingIntent)
    }
}

private val String.complicationText get() = PlainComplicationText.Builder(this).build()
