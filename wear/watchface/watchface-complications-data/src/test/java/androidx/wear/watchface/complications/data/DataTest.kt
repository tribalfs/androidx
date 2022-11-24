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

@file:OptIn(ComplicationExperimental::class)

package androidx.wear.watchface.complications.data

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.Instant
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

@RunWith(SharedRobolectricTestRunner::class)
@Suppress("NewApi")
public class AsWireComplicationDataTest {
    val resources = ApplicationProvider.getApplicationContext<Context>().resources
    val dataSourceA = ComponentName("com.pkg_a", "com.a")
    val dataSourceB = ComponentName("com.pkg_a", "com.a")
    val monochromaticImageIcon = Icon.createWithContentUri("someuri")
    val smallImageIcon = Icon.createWithContentUri("someuri2")
    val monochromaticImage = MonochromaticImage.Builder(monochromaticImageIcon).build()
    val smallImage = SmallImage.Builder(smallImageIcon, SmallImageType.PHOTO).build()
    val monochromaticImageIcon2 = Icon.createWithContentUri("someuri")
    val smallImageIcon2 = Icon.createWithContentUri("someuri2")
    val monochromaticImage2 = MonochromaticImage.Builder(monochromaticImageIcon2).build()
    val smallImage2 = SmallImage.Builder(smallImageIcon2, SmallImageType.PHOTO).build()
    val icon = Icon.createWithContentUri("someuri")
    val image = MonochromaticImage.Builder(icon).build()
    val icon2 = Icon.createWithContentUri("someuri")
    val image2 = MonochromaticImage.Builder(icon2).build()
    val icon3 = Icon.createWithContentUri("someuri3")
    val image3 = MonochromaticImage.Builder(icon3).build()

    @Before
    fun setup() {
        ShadowLog.setLoggable("ComplicationData", Log.DEBUG)
    }

    @Test
    public fun noDataComplicationData() {
        val data = NoDataComplicationData()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                    .setPlaceholder(null)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        assertThat(serializeAndDeserialize(data)).isInstanceOf(NoDataComplicationData::class.java)

        assertThat(data).isEqualTo(NoDataComplicationData())
        assertThat(data.hashCode()).isEqualTo(NoDataComplicationData().hashCode())
        assertThat(data.toString()).isEqualTo(
            "NoDataComplicationData(placeholder=null, tapActionLostDueToSerialization=false," +
                " tapAction=null, validTimeRange=TimeRange(startDateTimeMillis=" +
                "-1000000000-01-01T00:00:00Z, endDateTimeMillis=" +
                "+1000000000-12-31T23:59:59.999999999Z), persistencePolicy=0, displayPolicy=0)"
        )
    }

    @Test
    public fun emptyComplicationData() {
        val data = EmptyComplicationData()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_EMPTY).build()
            )
        testRoundTripConversions(data)
        assertThat(serializeAndDeserialize(data)).isInstanceOf(EmptyComplicationData::class.java)

        assertThat(data).isEqualTo(EmptyComplicationData())
        assertThat(data.hashCode()).isEqualTo(EmptyComplicationData().hashCode())
        assertThat(data.toString()).isEqualTo("EmptyComplicationData()")
    }

    @Test
    public fun notConfiguredComplicationData() {
        val data = NotConfiguredComplicationData()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NOT_CONFIGURED).build()
            )
        testRoundTripConversions(data)
        assertThat(serializeAndDeserialize(data))
            .isInstanceOf(NotConfiguredComplicationData::class.java)

        assertThat(data).isEqualTo(NotConfiguredComplicationData())
        assertThat(data.hashCode()).isEqualTo(NotConfiguredComplicationData().hashCode())
        assertThat(data.toString()).isEqualTo("NotConfiguredComplicationData()")
    }

    @Test
    public fun shortTextComplicationData() {
        val data = ShortTextComplicationData.Builder(
            "text".complicationText,
            "content description".complicationText
        )
            .setTitle("title".complicationText)
            .setDataSource(dataSourceA)
            .setPersistencePolicy(ComplicationPersistencePolicies.DO_NOT_PERSIST)
            .setDisplayPolicy(ComplicationDisplayPolicies.DO_NOT_SHOW_WHEN_DEVICE_LOCKED)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(WireComplicationText.plainText("text"))
                    .setShortTitle(WireComplicationText.plainText("title"))
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setPersistencePolicy(ComplicationPersistencePolicies.DO_NOT_PERSIST)
                    .setDisplayPolicy(ComplicationDisplayPolicies.DO_NOT_SHOW_WHEN_DEVICE_LOCKED)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as ShortTextComplicationData
        assertThat(deserialized.text.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("text")
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")
        assertThat(deserialized.title!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("title")

        val data2 = ShortTextComplicationData.Builder(
            "text".complicationText,
            "content description".complicationText
        )
            .setTitle("title".complicationText)
            .setDataSource(dataSourceA)
            .setPersistencePolicy(ComplicationPersistencePolicies.DO_NOT_PERSIST)
            .setDisplayPolicy(ComplicationDisplayPolicies.DO_NOT_SHOW_WHEN_DEVICE_LOCKED)
            .build()
        val data3 = ShortTextComplicationData.Builder(
            "text3".complicationText,
            "content description3".complicationText
        )
            .setTitle("title3".complicationText)
            .setDataSource(dataSourceB)
            .setPersistencePolicy(ComplicationPersistencePolicies.DO_NOT_PERSIST)
            .setDisplayPolicy(ComplicationDisplayPolicies.DO_NOT_SHOW_WHEN_DEVICE_LOCKED)
            .build()

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "ShortTextComplicationData(text=ComplicationText{mSurroundingText=text, " +
                "mTimeDependentText=null, mStringExpression=null}, title=ComplicationText{" +
                "mSurroundingText=title, mTimeDependentText=null, mStringExpression=null}, " +
                "monochromaticImage=null, smallImage=null, contentDescription=ComplicationText{" +
                "mSurroundingText=content description, mTimeDependentText=null, " +
                "mStringExpression=null}, tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, persistencePolicy=1, displayPolicy=1)"
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @Test
    public fun shortTextComplicationData_withImages() {
        val data = ShortTextComplicationData.Builder(
            "text".complicationText,
            "content description".complicationText
        )
            .setTitle("title".complicationText)
            .setMonochromaticImage(monochromaticImage)
            .setSmallImage(smallImage)
            .setDataSource(dataSourceA)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(WireComplicationText.plainText("text"))
                    .setShortTitle(WireComplicationText.plainText("title"))
                    .setIcon(monochromaticImageIcon)
                    .setSmallImage(smallImageIcon)
                    .setSmallImageStyle(WireComplicationData.IMAGE_STYLE_PHOTO)
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as ShortTextComplicationData
        assertThat(deserialized.text.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("text")
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")
        assertThat(deserialized.title!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("title")
        assertThat(deserialized.monochromaticImage!!.image.uri.toString())
            .isEqualTo("someuri")
        assertThat(deserialized.smallImage!!.image.uri.toString())
            .isEqualTo("someuri2")
        assertThat(deserialized.smallImage!!.type).isEqualTo(SmallImageType.PHOTO)

        val data2 = ShortTextComplicationData.Builder(
            "text".complicationText,
            "content description".complicationText
        )
            .setTitle("title".complicationText)
            .setMonochromaticImage(monochromaticImage2)
            .setSmallImage(smallImage2)
            .setDataSource(dataSourceA)
            .build()
        val data3 = ShortTextComplicationData.Builder(
            "text3".complicationText,
            "content description3".complicationText
        )
            .setTitle("title3".complicationText)
            .setDataSource(dataSourceB)
            .build()

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "ShortTextComplicationData(text=ComplicationText{mSurroundingText=text, " +
                "mTimeDependentText=null, mStringExpression=null}, title=ComplicationText{" +
                "mSurroundingText=title, mTimeDependentText=null, mStringExpression=null}, " +
                "monochromaticImage=MonochromaticImage(image=Icon(typ=URI uri=someuri), " +
                "ambientImage=null), smallImage=SmallImage(image=Icon(typ=URI uri=someuri2), " +
                "type=PHOTO, ambientImage=null), contentDescription=ComplicationText{" +
                "mSurroundingText=content description, mTimeDependentText=null, " +
                "mStringExpression=null}, tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, persistencePolicy=0, displayPolicy=0)"
        )
    }

    @Test
    public fun longTextComplicationData() {
        val data = LongTextComplicationData.Builder(
            "text".complicationText,
            "content description".complicationText
        )
            .setTitle("title".complicationText)
            .setDataSource(dataSourceA)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_LONG_TEXT)
                    .setLongText(WireComplicationText.plainText("text"))
                    .setLongTitle(WireComplicationText.plainText("title"))
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as LongTextComplicationData
        assertThat(deserialized.text.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("text")
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")
        assertThat(deserialized.title!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("title")

        val data2 = LongTextComplicationData.Builder(
            "text".complicationText,
            "content description".complicationText
        )
            .setTitle("title".complicationText)
            .setDataSource(dataSourceA)
            .build()
        val data3 = LongTextComplicationData.Builder(
            "text3".complicationText,
            "content description3".complicationText
        )
            .setTitle("title3".complicationText)
            .setDataSource(dataSourceB)
            .build()

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "LongTextComplicationData(text=ComplicationText{mSurroundingText=text, " +
                "mTimeDependentText=null, mStringExpression=null}, title=ComplicationText{" +
                "mSurroundingText=title, mTimeDependentText=null, mStringExpression=null}, " +
                "monochromaticImage=null, smallImage=null, contentDescription=ComplicationText{" +
                "mSurroundingText=content description, mTimeDependentText=null, " +
                "mStringExpression=null}), tapActionLostDueToSerialization=false, tapAction=null," +
                " validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, persistencePolicy=0, displayPolicy=0)"
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @Test
    public fun longTextComplicationData_withImages() {
        val data = LongTextComplicationData.Builder(
            "text".complicationText,
            "content description".complicationText
        )
            .setTitle("title".complicationText)
            .setMonochromaticImage(monochromaticImage)
            .setSmallImage(smallImage)
            .setDataSource(dataSourceA)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_LONG_TEXT)
                    .setLongText(WireComplicationText.plainText("text"))
                    .setLongTitle(WireComplicationText.plainText("title"))
                    .setIcon(monochromaticImageIcon)
                    .setSmallImage(smallImageIcon)
                    .setSmallImageStyle(WireComplicationData.IMAGE_STYLE_PHOTO)
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as LongTextComplicationData
        assertThat(deserialized.text.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("text")
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")
        assertThat(deserialized.title!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("title")
        assertThat(deserialized.monochromaticImage!!.image.uri.toString())
            .isEqualTo("someuri")
        assertThat(deserialized.smallImage!!.image.uri.toString())
            .isEqualTo("someuri2")
        assertThat(deserialized.smallImage!!.type).isEqualTo(SmallImageType.PHOTO)

        val data2 = LongTextComplicationData.Builder(
            "text".complicationText,
            "content description".complicationText
        )
            .setTitle("title".complicationText)
            .setMonochromaticImage(monochromaticImage2)
            .setSmallImage(smallImage2)
            .setDataSource(dataSourceA)
            .build()
        val data3 = LongTextComplicationData.Builder(
            "text3".complicationText,
            "content description3".complicationText
        )
            .setTitle("title3".complicationText)
            .setDataSource(dataSourceB)
            .build()

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "LongTextComplicationData(text=ComplicationText{mSurroundingText=text, " +
                "mTimeDependentText=null, mStringExpression=null}, " +
                "title=ComplicationText{mSurroundingText=title, mTimeDependentText=null, " +
                "mStringExpression=null}, monochromaticImage=MonochromaticImage(" +
                "image=Icon(typ=URI uri=someuri), ambientImage=null), smallImage=SmallImage(" +
                "image=Icon(typ=URI uri=someuri2), type=PHOTO, ambientImage=null), " +
                "contentDescription=ComplicationText{mSurroundingText=content description, " +
                "mTimeDependentText=null, mStringExpression=null}), " +
                "tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, persistencePolicy=0, displayPolicy=0)"
        )
    }

    @Test
    public fun rangedValueComplicationData_withFixedValue() {
        val data = RangedValueComplicationData.Builder(
            value = 95f, min = 0f, max = 100f,
            contentDescription = "content description".complicationText
        )
            .setTitle("battery".complicationText)
            .setDataSource(dataSourceA)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_RANGED_VALUE)
                    .setRangedValue(95f)
                    .setRangedValueType(RangedValueComplicationData.TYPE_UNDEFINED)
                    .setRangedMinValue(0f)
                    .setRangedMaxValue(100f)
                    .setShortTitle(WireComplicationText.plainText("battery"))
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as RangedValueComplicationData
        assertThat(deserialized.max).isEqualTo(100f)
        assertThat(deserialized.min).isEqualTo(0f)
        assertThat(deserialized.value).isEqualTo(95f)
        assertThat(deserialized.dynamicValue).isNull()
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")
        assertThat(deserialized.title!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("battery")

        val data2 = RangedValueComplicationData.Builder(
            value = 95f, min = 0f, max = 100f,
            contentDescription = "content description".complicationText
        )
            .setTitle("battery".complicationText)
            .setDataSource(dataSourceA)
            .build()

        val data3 = RangedValueComplicationData.Builder(
            value = 95f, min = 0f, max = 100f,
            contentDescription = "content description2".complicationText
        )
            .setTitle("battery2".complicationText)
            .setDataSource(dataSourceB)
            .build()

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "RangedValueComplicationData(value=95.0, dynamicValue=null, valueType=0, " +
                "min=0.0, max=100.0, monochromaticImage=null, smallImage=null, " +
                "title=ComplicationText{mSurroundingText=battery, mTimeDependentText=null, " +
                "mStringExpression=null}, text=null, contentDescription=ComplicationText{" +
                "mSurroundingText=content description, mTimeDependentText=null, " +
                "mStringExpression=null}), tapActionLostDueToSerialization=false, tapAction=null," +
                " validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, colorRamp=null, persistencePolicy=0, " +
                "displayPolicy=0)"
        )
    }

    @Test
    public fun rangedValueComplicationData_withDynamicValue() {
        val data = RangedValueComplicationData.Builder(
            dynamicValue = byteArrayOf(42, 107).toDynamicFloat(),
            min = 5f,
            max = 100f,
            contentDescription = "content description".complicationText
        )
            .setTitle("battery".complicationText)
            .setDataSource(dataSourceA)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_RANGED_VALUE)
                    .setRangedDynamicValue(byteArrayOf(42, 107).toDynamicFloat())
                    .setRangedValue(5f) // min as a sensible default
                    .setRangedValueType(RangedValueComplicationData.TYPE_UNDEFINED)
                    .setRangedMinValue(5f)
                    .setRangedMaxValue(100f)
                    .setShortTitle(WireComplicationText.plainText("battery"))
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as RangedValueComplicationData
        assertThat(deserialized.max).isEqualTo(100f)
        assertThat(deserialized.min).isEqualTo(5f)
        assertThat(deserialized.dynamicValue!!.asByteArray()).isEqualTo(byteArrayOf(42, 107))
        assertThat(deserialized.value).isEqualTo(5f) // min as a sensible default
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")
        assertThat(deserialized.title!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("battery")

        val sameData = RangedValueComplicationData.Builder(
            dynamicValue = byteArrayOf(42, 107).toDynamicFloat(),
            min = 5f,
            max = 100f,
            contentDescription = "content description".complicationText
        )
            .setTitle("battery".complicationText)
            .setDataSource(dataSourceA)
            .build()
        val diffDataFixedValue = RangedValueComplicationData.Builder(
            value = 5f, // Even though it's the sensible default
            min = 5f,
            max = 100f,
            contentDescription = "content description".complicationText
        )
            .setTitle("battery".complicationText)
            .setDataSource(dataSourceA)
            .build()
        val diffDataDynamicValue = RangedValueComplicationData.Builder(
            dynamicValue = byteArrayOf(43, 108).toDynamicFloat(),
            min = 5f,
            max = 100f,
            contentDescription = "content description".complicationText
        )
            .setTitle("battery".complicationText)
            .setDataSource(dataSourceA)
            .build()

        assertThat(data).isEqualTo(sameData)
        assertThat(data).isNotEqualTo(diffDataFixedValue)
        assertThat(data).isNotEqualTo(diffDataDynamicValue)
        assertThat(data.hashCode()).isEqualTo(sameData.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(diffDataFixedValue.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(diffDataDynamicValue.hashCode())
        assertThat(data.toString()).isEqualTo(
            "RangedValueComplicationData(value=5.0, " +
                "dynamicValue=DynamicFloatPlaceholder[42, 107], valueType=0, min=5.0, max=100.0, " +
                "monochromaticImage=null, smallImage=null, title=ComplicationText{" +
                "mSurroundingText=battery, mTimeDependentText=null, mStringExpression=null}, " +
                "text=null, contentDescription=ComplicationText{" +
                "mSurroundingText=content description, mTimeDependentText=null, " +
                "mStringExpression=null}), tapActionLostDueToSerialization=false, tapAction=null," +
                " validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), dataSource=" +
                "ComponentInfo{com.pkg_a/com.a}, colorRamp=null, persistencePolicy=0, " +
                "displayPolicy=0)"
        )
    }

    @Test
    public fun rangedValueComplicationData_withStringExpression() {
        val data = RangedValueComplicationData.Builder(
            value = 95f, min = 0f, max = 100f,
            contentDescription = "content description".complicationText
        )
            .setTitle(
                StringExpressionComplicationText(StringExpression(byteArrayOf(1, 2, 3, 4, 5)))
            )
            .setDataSource(dataSourceA)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_RANGED_VALUE)
                    .setRangedValue(95f)
                    .setRangedValueType(RangedValueComplicationData.TYPE_UNDEFINED)
                    .setRangedMinValue(0f)
                    .setRangedMaxValue(100f)
                    .setShortTitle(
                        WireComplicationText(StringExpression(byteArrayOf(1, 2, 3, 4, 5)))
                    )
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as RangedValueComplicationData
        assertThat(deserialized.max).isEqualTo(100f)
        assertThat(deserialized.min).isEqualTo(0f)
        assertThat(deserialized.value).isEqualTo(95f)
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")

        val sameData = RangedValueComplicationData.Builder(
            value = 95f, min = 0f, max = 100f,
            contentDescription = "content description".complicationText
        )
            .setTitle(
                StringExpressionComplicationText(StringExpression(byteArrayOf(1, 2, 3, 4, 5)))
            )
            .setDataSource(dataSourceA)
            .build()

        val differentData = RangedValueComplicationData.Builder(
            value = 95f, min = 0f, max = 100f,
            contentDescription = "content description".complicationText
        )
            .setTitle(StringExpressionComplicationText(StringExpression(byteArrayOf(1, 2, 4, 5))))
            .setDataSource(dataSourceB)
            .build()

        assertThat(data).isEqualTo(sameData)
        assertThat(data).isNotEqualTo(differentData)
        assertThat(data.hashCode()).isEqualTo(sameData.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(differentData.hashCode())
        assertThat(data.toString()).isEqualTo(
            "RangedValueComplicationData(value=95.0, dynamicValue=null, valueType=0, " +
                "min=0.0, max=100.0, monochromaticImage=null, smallImage=null, " +
                "title=ComplicationText{mSurroundingText=(null), mTimeDependentText=null, " +
                "mStringExpression=StringExpression(expression=[1, 2, 3, 4, 5])}, text=null, " +
                "contentDescription=ComplicationText{mSurroundingText=content description, " +
                "mTimeDependentText=null, mStringExpression=null}), " +
                "tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, colorRamp=null, persistencePolicy=0, " +
                "displayPolicy=0)"
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @Test
    public fun rangedValueComplicationData_withImages() {
        val data = RangedValueComplicationData.Builder(
            value = 95f, min = 0f, max = 100f,
            contentDescription = "content description".complicationText
        )
            .setTitle("battery".complicationText)
            .setMonochromaticImage(monochromaticImage)
            .setSmallImage(smallImage)
            .setDataSource(dataSourceA)
            .setValueType(RangedValueComplicationData.TYPE_RATING)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_RANGED_VALUE)
                    .setRangedValue(95f)
                    .setRangedMinValue(0f)
                    .setRangedMaxValue(100f)
                    .setIcon(monochromaticImageIcon)
                    .setSmallImage(smallImageIcon)
                    .setSmallImageStyle(WireComplicationData.IMAGE_STYLE_PHOTO)
                    .setShortTitle(WireComplicationText.plainText("battery"))
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .setRangedValueType(RangedValueComplicationData.TYPE_RATING)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as RangedValueComplicationData
        assertThat(deserialized.max).isEqualTo(100f)
        assertThat(deserialized.min).isEqualTo(0f)
        assertThat(deserialized.value).isEqualTo(95f)
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")
        assertThat(deserialized.title!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("battery")
        assertThat(deserialized.monochromaticImage!!.image.uri.toString())
            .isEqualTo("someuri")
        assertThat(deserialized.smallImage!!.image.uri.toString())
            .isEqualTo("someuri2")
        assertThat(deserialized.smallImage!!.type).isEqualTo(SmallImageType.PHOTO)

        val data2 = RangedValueComplicationData.Builder(
            value = 95f, min = 0f, max = 100f,
            contentDescription = "content description".complicationText
        )
            .setTitle("battery".complicationText)
            .setMonochromaticImage(monochromaticImage2)
            .setSmallImage(smallImage2)
            .setDataSource(dataSourceA)
            .setValueType(RangedValueComplicationData.TYPE_RATING)
            .build()

        val data3 = RangedValueComplicationData.Builder(
            value = 95f, min = 0f, max = 100f,
            contentDescription = "content description2".complicationText
        )
            .setTitle("battery2".complicationText)
            .setDataSource(dataSourceB)
            .setValueType(RangedValueComplicationData.TYPE_RATING)
            .build()

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "RangedValueComplicationData(value=95.0, dynamicValue=null, " +
                "valueType=1, min=0.0, max=100.0, " +
                "monochromaticImage=MonochromaticImage(image=Icon(typ=URI uri=someuri), " +
                "ambientImage=null), smallImage=SmallImage(image=Icon(typ=URI uri=someuri2), " +
                "type=PHOTO, ambientImage=null), title=ComplicationText{mSurroundingText=battery," +
                " mTimeDependentText=null, mStringExpression=null}, text=null, " +
                "contentDescription=ComplicationText{mSurroundingText=content description, " +
                "mTimeDependentText=null, mStringExpression=null}), " +
                "tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, colorRamp=null, persistencePolicy=0, " +
                "displayPolicy=0)"
        )
    }

    @Test
    public fun goalProgressComplicationData_withFixedValue() {
        val data = GoalProgressComplicationData.Builder(
            value = 1200f, targetValue = 10000f,
            contentDescription = "content description".complicationText
        )
            .setTitle("steps".complicationText)
            .setDataSource(dataSourceA)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_GOAL_PROGRESS)
                    .setRangedValue(1200f)
                    .setTargetValue(10000f)
                    .setShortTitle(WireComplicationText.plainText("steps"))
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as GoalProgressComplicationData
        assertThat(deserialized.value).isEqualTo(1200f)
        assertThat(deserialized.dynamicValue).isNull()
        assertThat(deserialized.targetValue).isEqualTo(10000f)
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")
        assertThat(deserialized.title!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("steps")

        val sameData = GoalProgressComplicationData.Builder(
            value = 1200f, targetValue = 10000f,
            contentDescription = "content description".complicationText
        )
            .setTitle("steps".complicationText)
            .setDataSource(dataSourceA)
            .build()

        val diffData = GoalProgressComplicationData.Builder(
            value = 1201f, targetValue = 10000f,
            contentDescription = "content description".complicationText
        )
            .setTitle("steps".complicationText)
            .setDataSource(dataSourceB)
            .build()

        assertThat(data).isEqualTo(sameData)
        assertThat(data).isNotEqualTo(diffData)
        assertThat(data.hashCode()).isEqualTo(sameData.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(diffData.hashCode())
        assertThat(data.toString()).isEqualTo(
            "GoalProgressComplicationData(value=1200.0, dynamicValue=null, " +
                "targetValue=10000.0, monochromaticImage=null, smallImage=null, " +
                "title=ComplicationText{mSurroundingText=steps, mTimeDependentText=null, " +
                "mStringExpression=null}, text=null, contentDescription=ComplicationText{" +
                "mSurroundingText=content description, mTimeDependentText=null, " +
                "mStringExpression=null}), tapActionLostDueToSerialization=false, tapAction=null," +
                " validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, colorRamp=null, persistencePolicy=0, " +
                "displayPolicy=0)"
        )
    }

    @Test
    public fun goalProgressComplicationData_withDynamicValue() {
        val data = GoalProgressComplicationData.Builder(
            dynamicValue = byteArrayOf(42, 107).toDynamicFloat(),
            targetValue = 10000f,
            contentDescription = "content description".complicationText
        )
            .setTitle("steps".complicationText)
            .setDataSource(dataSourceA)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_GOAL_PROGRESS)
                    .setRangedDynamicValue(byteArrayOf(42, 107).toDynamicFloat())
                    .setRangedValue(0f) // sensible default
                    .setTargetValue(10000f)
                    .setShortTitle(WireComplicationText.plainText("steps"))
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as GoalProgressComplicationData
        assertThat(deserialized.dynamicValue!!.asByteArray()).isEqualTo(byteArrayOf(42, 107))
        assertThat(deserialized.value).isEqualTo(0f) // sensible default
        assertThat(deserialized.targetValue).isEqualTo(10000f)
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")
        assertThat(deserialized.title!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("steps")

        val sameData = GoalProgressComplicationData.Builder(
            dynamicValue = byteArrayOf(42, 107).toDynamicFloat(),
            targetValue = 10000f,
            contentDescription = "content description".complicationText
        )
            .setTitle("steps".complicationText)
            .setDataSource(dataSourceA)
            .build()

        val diffData = GoalProgressComplicationData.Builder(
            dynamicValue = byteArrayOf(43, 108).toDynamicFloat(),
            targetValue = 10000f,
            contentDescription = "content description".complicationText
        )
            .setTitle("steps".complicationText)
            .setDataSource(dataSourceB)
            .build()

        assertThat(data).isEqualTo(sameData)
        assertThat(data).isNotEqualTo(diffData)
        assertThat(data.hashCode()).isEqualTo(sameData.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(diffData.hashCode())
        assertThat(data.toString()).isEqualTo(
            "GoalProgressComplicationData(value=0.0, dynamicValue=" +
                "DynamicFloatPlaceholder[42, 107], targetValue=10000.0, monochromaticImage=null, " +
                "smallImage=null, title=ComplicationText{mSurroundingText=steps, " +
                "mTimeDependentText=null, mStringExpression=null}, text=null, " +
                "contentDescription=ComplicationText{mSurroundingText=content description, " +
                "mTimeDependentText=null, mStringExpression=null}), " +
                "tapActionLostDueToSerialization=false, tapAction=null, validTimeRange=TimeRange(" +
                "startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, colorRamp=null, persistencePolicy=0, " +
                "displayPolicy=0)"
        )
    }

    @Test
    public fun goalProgressComplicationData_withColorRamp() {
        val data = GoalProgressComplicationData.Builder(
            value = 1200f, targetValue = 10000f,
            contentDescription = "content description".complicationText
        )
            .setTitle("steps".complicationText)
            .setDataSource(dataSourceA)
            .setColorRamp(ColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE), true))
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_GOAL_PROGRESS)
                    .setRangedValue(1200f)
                    .setTargetValue(10000f)
                    .setShortTitle(WireComplicationText.plainText("steps"))
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE))
                    .setColorRampIsSmoothShaded(true)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as GoalProgressComplicationData
        assertThat(deserialized.value).isEqualTo(1200f)
        assertThat(deserialized.targetValue).isEqualTo(10000f)
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")
        assertThat(deserialized.title!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("steps")

        val data2 = GoalProgressComplicationData.Builder(
            value = 1200f, targetValue = 10000f,
            contentDescription = "content description".complicationText
        )
            .setTitle("steps".complicationText)
            .setDataSource(dataSourceA)
            .setColorRamp(ColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE), true))
            .build()

        val data3 = GoalProgressComplicationData.Builder(
            value = 1200f, targetValue = 10000f,
            contentDescription = "content description".complicationText
        )
            .setTitle("steps".complicationText)
            .setDataSource(dataSourceB)
            .setColorRamp(ColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE), false))
            .build()

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "GoalProgressComplicationData(value=1200.0, dynamicValue=null, " +
                "targetValue=10000.0, monochromaticImage=null, smallImage=null, " +
                "title=ComplicationText{mSurroundingText=steps, mTimeDependentText=null, " +
                "mStringExpression=null}, text=null, contentDescription=ComplicationText{" +
                "mSurroundingText=content description, mTimeDependentText=null, " +
                "mStringExpression=null}), tapActionLostDueToSerialization=false, tapAction=null," +
                " validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, colorRamp=ColorRamp(colors=[-65536, " +
                "-16711936, -16776961], interpolated=true), persistencePolicy=0, displayPolicy=0)"
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @Test
    public fun goalProgressComplicationData_withColorRampAndImages() {
        val data = GoalProgressComplicationData.Builder(
            value = 1200f, targetValue = 10000f,
            contentDescription = "content description".complicationText
        )
            .setTitle("steps".complicationText)
            .setMonochromaticImage(monochromaticImage)
            .setSmallImage(smallImage)
            .setDataSource(dataSourceA)
            .setColorRamp(ColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE), true))
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_GOAL_PROGRESS)
                    .setRangedValue(1200f)
                    .setTargetValue(10000f)
                    .setIcon(monochromaticImageIcon)
                    .setSmallImage(smallImageIcon)
                    .setSmallImageStyle(WireComplicationData.IMAGE_STYLE_PHOTO)
                    .setShortTitle(WireComplicationText.plainText("steps"))
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE))
                    .setColorRampIsSmoothShaded(true)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as GoalProgressComplicationData
        assertThat(deserialized.value).isEqualTo(1200f)
        assertThat(deserialized.targetValue).isEqualTo(10000f)
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")
        assertThat(deserialized.title!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("steps")
        assertThat(deserialized.monochromaticImage!!.image.uri.toString())
            .isEqualTo("someuri")
        assertThat(deserialized.smallImage!!.image.uri.toString())
            .isEqualTo("someuri2")
        assertThat(deserialized.smallImage!!.type).isEqualTo(SmallImageType.PHOTO)

        val data2 = GoalProgressComplicationData.Builder(
            value = 1200f, targetValue = 10000f,
            contentDescription = "content description".complicationText
        )
            .setTitle("steps".complicationText)
            .setMonochromaticImage(monochromaticImage2)
            .setSmallImage(smallImage2)
            .setDataSource(dataSourceA)
            .setColorRamp(ColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE), true))
            .build()

        val data3 = GoalProgressComplicationData.Builder(
            value = 1200f, targetValue = 10000f,
            contentDescription = "content description".complicationText
        )
            .setTitle("steps".complicationText)
            .setDataSource(dataSourceB)
            .setColorRamp(ColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE), false))
            .build()

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "GoalProgressComplicationData(value=1200.0, dynamicValue=null, " +
                "targetValue=10000.0, " +
                "monochromaticImage=MonochromaticImage(image=Icon(typ=URI uri=someuri), " +
                "ambientImage=null), smallImage=SmallImage(image=Icon(typ=URI uri=someuri2), " +
                "type=PHOTO, ambientImage=null), title=ComplicationText{mSurroundingText=steps, " +
                "mTimeDependentText=null, mStringExpression=null}, text=null, " +
                "contentDescription=ComplicationText{mSurroundingText=content description, " +
                "mTimeDependentText=null, mStringExpression=null}), " +
                "tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, colorRamp=ColorRamp(colors=[-65536, " +
                "-16711936, -16776961], interpolated=true), persistencePolicy=0, displayPolicy=0)"
        )
    }

    @Test
    public fun rangedValueComplicationData_withColorRamp() {
        val data = RangedValueComplicationData.Builder(
            value = 95f, min = 0f, max = 100f,
            contentDescription = "content description".complicationText
        )
            .setTitle("battery".complicationText)
            .setDataSource(dataSourceA)
            .setColorRamp(ColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE), true))
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_RANGED_VALUE)
                    .setRangedValue(95f)
                    .setRangedValueType(RangedValueComplicationData.TYPE_UNDEFINED)
                    .setRangedMinValue(0f)
                    .setRangedMaxValue(100f)
                    .setShortTitle(WireComplicationText.plainText("battery"))
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE))
                    .setColorRampIsSmoothShaded(true)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as RangedValueComplicationData
        assertThat(deserialized.max).isEqualTo(100f)
        assertThat(deserialized.min).isEqualTo(0f)
        assertThat(deserialized.value).isEqualTo(95f)
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")
        assertThat(deserialized.title!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("battery")

        val data2 = RangedValueComplicationData.Builder(
            value = 95f, min = 0f, max = 100f,
            contentDescription = "content description".complicationText
        )
            .setTitle("battery".complicationText)
            .setDataSource(dataSourceA)
            .setColorRamp(ColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE), true))
            .build()

        val data3 = RangedValueComplicationData.Builder(
            value = 95f, min = 0f, max = 100f,
            contentDescription = "content description2".complicationText
        )
            .setTitle("battery".complicationText)
            .setDataSource(dataSourceB)
            .setColorRamp(ColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.YELLOW), true))
            .build()

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "RangedValueComplicationData(value=95.0, dynamicValue=null, " +
                "valueType=0, min=0.0, max=100.0, " +
                "monochromaticImage=null, smallImage=null, title=ComplicationText{" +
                "mSurroundingText=battery, mTimeDependentText=null, mStringExpression=null}, " +
                "text=null, contentDescription=ComplicationText{mSurroundingText=content " +
                "description, mTimeDependentText=null, mStringExpression=null}), " +
                "tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, colorRamp=ColorRamp(colors=[-65536, " +
                "-16711936, -16776961], interpolated=true), persistencePolicy=0, displayPolicy=0)"
        )
    }

    @Test
    fun weightedElementsComplicationDataTruncation() {
        val data = WeightedElementsComplicationData.Builder(
            MutableList(WeightedElementsComplicationData.getMaxElements() + 5) {
                WeightedElementsComplicationData.Element(0.5f, Color.RED)
            },
            contentDescription = "content description".complicationText
        )
            .setTitle("test".complicationText)
            .build()

        assertThat(data.elements.size).isEqualTo(WeightedElementsComplicationData.getMaxElements())
    }

    @Test
    public fun weightedElementsComplicationData() {
        val data = WeightedElementsComplicationData.Builder(
            listOf(
                WeightedElementsComplicationData.Element(0.5f, Color.RED),
                WeightedElementsComplicationData.Element(1f, Color.GREEN),
                WeightedElementsComplicationData.Element(2f, Color.BLUE),
            ),
            contentDescription = "content description".complicationText
        )
            .setElementBackgroundColor(Color.GRAY)
            .setTitle("calories".complicationText)
            .setDataSource(dataSourceA)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_WEIGHTED_ELEMENTS)
                    .setElementWeights(floatArrayOf(0.5f, 1f, 2f))
                    .setElementColors(intArrayOf(Color.RED, Color.GREEN, Color.BLUE))
                    .setElementBackgroundColor(Color.GRAY)
                    .setShortTitle(WireComplicationText.plainText("calories"))
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as WeightedElementsComplicationData
        assertThat(deserialized.elements).isEqualTo(
            listOf(
                WeightedElementsComplicationData.Element(0.5f, Color.RED),
                WeightedElementsComplicationData.Element(1f, Color.GREEN),
                WeightedElementsComplicationData.Element(2f, Color.BLUE),
            )
        )
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")
        assertThat(deserialized.title!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("calories")

        val data2 = WeightedElementsComplicationData.Builder(
            listOf(
                WeightedElementsComplicationData.Element(0.5f, Color.RED),
                WeightedElementsComplicationData.Element(1f, Color.GREEN),
                WeightedElementsComplicationData.Element(2f, Color.BLUE),
            ),
            contentDescription = "content description".complicationText
        )
            .setElementBackgroundColor(Color.GRAY)
            .setTitle("calories".complicationText)
            .setDataSource(dataSourceA)
            .build()

        val data3 = WeightedElementsComplicationData.Builder(
            listOf(
                WeightedElementsComplicationData.Element(0.5f, Color.RED),
                WeightedElementsComplicationData.Element(10f, Color.GREEN),
                WeightedElementsComplicationData.Element(2f, Color.BLUE),
            ),
            contentDescription = "content description".complicationText
        )
            .setTitle("battery".complicationText)
            .setDataSource(dataSourceA)
            .build()

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "WeightedElementsComplicationData(elements=Element(color=-65536, weight=0.5)," +
                " Element(color=-16711936, weight=1.0), Element(color=-16776961, weight=2.0), " +
                "elementBackgroundColor=-7829368, monochromaticImage=null, smallImage=null, " +
                "title=ComplicationText{mSurroundingText=calories, mTimeDependentText=null, " +
                "mStringExpression=null}, text=null, contentDescription=ComplicationText{" +
                "mSurroundingText=content description, mTimeDependentText=null, " +
                "mStringExpression=null}), tapActionLostDueToSerialization=false, tapAction=null," +
                " validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, persistencePolicy=0, displayPolicy=0)"
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @Test
    public fun weightedElementsComplicationData_withImages() {
        val data = WeightedElementsComplicationData.Builder(
            listOf(
                WeightedElementsComplicationData.Element(0.5f, Color.RED),
                WeightedElementsComplicationData.Element(1f, Color.GREEN),
                WeightedElementsComplicationData.Element(2f, Color.BLUE),
            ),
            contentDescription = "content description".complicationText
        )
            .setTitle("calories".complicationText)
            .setMonochromaticImage(monochromaticImage)
            .setSmallImage(smallImage)
            .setDataSource(dataSourceA)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_WEIGHTED_ELEMENTS)
                    .setElementWeights(floatArrayOf(0.5f, 1f, 2f))
                    .setElementColors(intArrayOf(Color.RED, Color.GREEN, Color.BLUE))
                    .setElementBackgroundColor(Color.TRANSPARENT)
                    .setShortTitle(WireComplicationText.plainText("calories"))
                    .setIcon(monochromaticImageIcon)
                    .setSmallImage(smallImageIcon)
                    .setSmallImageStyle(WireComplicationData.IMAGE_STYLE_PHOTO)
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as WeightedElementsComplicationData
        assertThat(deserialized.elements).isEqualTo(
            listOf(
                WeightedElementsComplicationData.Element(0.5f, Color.RED),
                WeightedElementsComplicationData.Element(1f, Color.GREEN),
                WeightedElementsComplicationData.Element(2f, Color.BLUE),
            )
        )
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")
        assertThat(deserialized.title!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("calories")
        assertThat(deserialized.monochromaticImage!!.image.uri.toString())
            .isEqualTo("someuri")
        assertThat(deserialized.smallImage!!.image.uri.toString())
            .isEqualTo("someuri2")
        assertThat(deserialized.smallImage!!.type).isEqualTo(SmallImageType.PHOTO)

        val data2 = WeightedElementsComplicationData.Builder(
            listOf(
                WeightedElementsComplicationData.Element(0.5f, Color.RED),
                WeightedElementsComplicationData.Element(1f, Color.GREEN),
                WeightedElementsComplicationData.Element(2f, Color.BLUE),
            ),
            contentDescription = "content description".complicationText
        )
            .setTitle("calories".complicationText)
            .setMonochromaticImage(monochromaticImage2)
            .setSmallImage(smallImage2)
            .setDataSource(dataSourceA)
            .build()

        val data3 = WeightedElementsComplicationData.Builder(
            listOf(
                WeightedElementsComplicationData.Element(0.5f, Color.RED),
                WeightedElementsComplicationData.Element(10f, Color.GREEN),
                WeightedElementsComplicationData.Element(2f, Color.BLUE),
            ),
            contentDescription = "content description".complicationText
        )
            .setTitle("battery".complicationText)
            .setDataSource(dataSourceA)
            .build()

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "WeightedElementsComplicationData(elements=Element(color=-65536, weight=0.5)," +
                " Element(color=-16711936, weight=1.0), Element(color=-16776961, weight=2.0), " +
                "elementBackgroundColor=0, monochromaticImage=MonochromaticImage(" +
                "image=Icon(typ=URI uri=someuri), ambientImage=null), " +
                "smallImage=SmallImage(image=Icon(typ=URI uri=someuri2), type=PHOTO, " +
                "ambientImage=null), title=ComplicationText{mSurroundingText=calories, " +
                "mTimeDependentText=null, mStringExpression=null}, text=null, " +
                "contentDescription=ComplicationText{mSurroundingText=content description, " +
                "mTimeDependentText=null, mStringExpression=null}), " +
                "tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, persistencePolicy=0, displayPolicy=0)"
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @Test
    public fun monochromaticImageComplicationData() {
        val data = MonochromaticImageComplicationData.Builder(
            image, "content description".complicationText
        ).setDataSource(dataSourceA).build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_ICON)
                    .setIcon(icon)
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as MonochromaticImageComplicationData
        assertThat(deserialized.monochromaticImage.image.uri.toString())
            .isEqualTo("someuri")
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")

        val data2 = MonochromaticImageComplicationData.Builder(
            image2, "content description".complicationText
        ).setDataSource(dataSourceA).build()

        val data3 = MonochromaticImageComplicationData.Builder(
            image3, "content description".complicationText
        ).setDataSource(dataSourceB).build()

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "MonochromaticImageComplicationData(monochromaticImage=MonochromaticImage(" +
                "image=Icon(typ=URI uri=someuri), ambientImage=null), contentDescription=" +
                "ComplicationText{mSurroundingText=content description, mTimeDependentText=null, " +
                "mStringExpression=null}), tapActionLostDueToSerialization=false, tapAction=null," +
                " validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, persistencePolicy=0, displayPolicy=0)"
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @Test
    public fun smallImageComplicationData() {
        val image = SmallImage.Builder(icon, SmallImageType.PHOTO).build()
        val data = SmallImageComplicationData.Builder(
            image, "content description".complicationText
        ).setDataSource(dataSourceA).build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_SMALL_IMAGE)
                    .setSmallImage(icon)
                    .setSmallImageStyle(WireComplicationData.IMAGE_STYLE_PHOTO)
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as SmallImageComplicationData
        assertThat(deserialized.smallImage.image.uri.toString())
            .isEqualTo("someuri")
        assertThat(deserialized.smallImage.type).isEqualTo(SmallImageType.PHOTO)
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")

        val image2 = SmallImage.Builder(icon2, SmallImageType.PHOTO).build()
        val data2 = SmallImageComplicationData.Builder(
            image2, "content description".complicationText
        ).setDataSource(dataSourceA).build()

        val image3 = SmallImage.Builder(icon3, SmallImageType.PHOTO).build()
        val data3 = SmallImageComplicationData.Builder(
            image3, "content description".complicationText
        ).setDataSource(dataSourceB).build()

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "SmallImageComplicationData(smallImage=SmallImage(image=Icon(" +
                "typ=URI uri=someuri), type=PHOTO, ambientImage=null), " +
                "contentDescription=ComplicationText{mSurroundingText=content description, " +
                "mTimeDependentText=null, mStringExpression=null}), " +
                "tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, persistencePolicy=0, displayPolicy=0)"
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @Test
    public fun smallImageComplicationData_with_BitmapIcon() {
        val bitmapIcon =
            Icon.createWithBitmap(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888))
        val image = SmallImage.Builder(bitmapIcon, SmallImageType.PHOTO).build()
        val data = SmallImageComplicationData.Builder(
            image, "content description".complicationText
        ).setDataSource(dataSourceA).build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_SMALL_IMAGE)
                    .setSmallImage(bitmapIcon)
                    .setSmallImageStyle(WireComplicationData.IMAGE_STYLE_PHOTO)
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as SmallImageComplicationData

        assertThat(deserialized.smallImage.image.type).isEqualTo(Icon.TYPE_BITMAP)
        val getBitmap = deserialized.smallImage.image.javaClass.getDeclaredMethod("getBitmap")

        @SuppressLint("BanUncheckedReflection")
        val bitmap = getBitmap.invoke(deserialized.smallImage.image) as Bitmap

        assertThat(bitmap.width).isEqualTo(100)
        assertThat(bitmap.height).isEqualTo(100)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @Test
    public fun backgroundImageComplicationData() {
        val photoImage = Icon.createWithContentUri("someuri")
        val data = PhotoImageComplicationData.Builder(
            photoImage, "content description".complicationText
        ).setDataSource(dataSourceA).build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_LARGE_IMAGE)
                    .setLargeImage(photoImage)
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setDataSource(dataSourceA)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as PhotoImageComplicationData
        assertThat(deserialized.photoImage.uri.toString())
            .isEqualTo("someuri")
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")

        val photoImage2 = Icon.createWithContentUri("someuri")
        val data2 = PhotoImageComplicationData.Builder(
            photoImage2, "content description".complicationText
        ).setDataSource(dataSourceA).build()

        val photoImage3 = Icon.createWithContentUri("someuri3")
        val data3 = PhotoImageComplicationData.Builder(
            photoImage3, "content description".complicationText
        ).setDataSource(dataSourceB).build()

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "PhotoImageComplicationData(photoImage=Icon(typ=URI uri=someuri), " +
                "contentDescription=ComplicationText{mSurroundingText=content description, " +
                "mTimeDependentText=null, mStringExpression=null}), " +
                "tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, persistencePolicy=0, displayPolicy=0)"
        )
    }

    @Test
    public fun noPermissionComplicationData() {
        val data = NoPermissionComplicationData.Builder()
            .setText("needs location".complicationText)
            .setDataSource(dataSourceA)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_PERMISSION)
                    .setShortText(WireComplicationText.plainText("needs location"))
                    .setDataSource(dataSourceA)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as NoPermissionComplicationData
        assertThat(deserialized.text!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("needs location")

        val data2 = NoPermissionComplicationData.Builder()
            .setText("needs location".complicationText)
            .setDataSource(dataSourceA)
            .build()

        val data3 = NoPermissionComplicationData.Builder()
            .setText("needs location3".complicationText)
            .setDataSource(dataSourceB)
            .build()

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "NoPermissionComplicationData(text=ComplicationText{mSurroundingText=needs location, " +
                "mTimeDependentText=null, mStringExpression=null}, title=null, " +
                "monochromaticImage=null, smallImage=null, tapActionLostDueToSerialization=false," +
                " tapAction=null, validTimeRange=TimeRange(startDateTimeMillis=" +
                "-1000000000-01-01T00:00:00Z, endDateTimeMillis=" +
                "+1000000000-12-31T23:59:59.999999999Z), dataSource=ComponentInfo{" +
                "com.pkg_a/com.a}, persistencePolicy=0, displayPolicy=0)"
        )
    }

    @Test
    public fun noPermissionComplicationData_withImages() {
        val data = NoPermissionComplicationData.Builder()
            .setText("needs location".complicationText)
            .setMonochromaticImage(monochromaticImage)
            .setSmallImage(smallImage)
            .setDataSource(dataSourceA)
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_PERMISSION)
                    .setShortText(WireComplicationText.plainText("needs location"))
                    .setIcon(monochromaticImageIcon)
                    .setSmallImage(smallImageIcon)
                    .setSmallImageStyle(WireComplicationData.IMAGE_STYLE_PHOTO)
                    .setDataSource(dataSourceA)
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as NoPermissionComplicationData
        assertThat(deserialized.text!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("needs location")

        val data2 = NoPermissionComplicationData.Builder()
            .setText("needs location".complicationText)
            .setMonochromaticImage(monochromaticImage)
            .setSmallImage(smallImage)
            .setDataSource(dataSourceA)
            .build()

        val data3 = NoPermissionComplicationData.Builder()
            .setText("needs location3".complicationText)
            .setDataSource(dataSourceB)
            .build()

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "NoPermissionComplicationData(text=ComplicationText{" +
                "mSurroundingText=needs location, mTimeDependentText=null, " +
                "mStringExpression=null}, title=null, monochromaticImage=MonochromaticImage(" +
                "image=Icon(typ=URI uri=someuri), ambientImage=null), smallImage=SmallImage(" +
                "image=Icon(typ=URI uri=someuri2), type=PHOTO, ambientImage=null), " +
                "tapActionLostDueToSerialization=false, tapAction=null, validTimeRange=TimeRange(" +
                "startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, persistencePolicy=0, displayPolicy=0)"
        )
    }

    @Test
    public fun noDataComplicationData_shortText() {
        val data = NoDataComplicationData(
            ShortTextComplicationData.Builder(
                ComplicationText.PLACEHOLDER,
                "content description".complicationText
            )
                .setTitle(ComplicationText.PLACEHOLDER)
                .setMonochromaticImage(MonochromaticImage.PLACEHOLDER)
                .setDataSource(dataSourceA)
                .build()
        )
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                    .setPlaceholder(
                        WireComplicationDataBuilder(WireComplicationData.TYPE_SHORT_TEXT)
                            .setShortText(ComplicationText.PLACEHOLDER.toWireComplicationText())
                            .setShortTitle(ComplicationText.PLACEHOLDER.toWireComplicationText())
                            .setIcon(createPlaceholderIcon())
                            .setContentDescription(
                                WireComplicationText.plainText("content description")
                            )
                            .setDataSource(dataSourceA)
                            .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                            .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                            .build()
                    )
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as NoDataComplicationData
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")

        val data2 = NoDataComplicationData(
            ShortTextComplicationData.Builder(
                ComplicationText.PLACEHOLDER,
                "content description".complicationText
            )
                .setTitle(ComplicationText.PLACEHOLDER)
                .setMonochromaticImage(MonochromaticImage.PLACEHOLDER)
                .setDataSource(dataSourceA)
                .build()
        )
        val data3 = NoDataComplicationData(
            ShortTextComplicationData.Builder(
                ComplicationText.PLACEHOLDER,
                "content description".complicationText
            )
                .setDataSource(dataSourceB)
                .build()
        )
        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "NoDataComplicationData(placeholder=ShortTextComplicationData(text=" +
                "ComplicationText{mSurroundingText=__placeholder__, mTimeDependentText=null, " +
                "mStringExpression=null}, title=ComplicationText{" +
                "mSurroundingText=__placeholder__, mTimeDependentText=null, " +
                "mStringExpression=null}, monochromaticImage=MonochromaticImage(" +
                "image=Icon(typ=RESOURCE pkg= id=0xffffffff), ambientImage=null), " +
                "smallImage=null, contentDescription=ComplicationText{" +
                "mSurroundingText=content description, mTimeDependentText=null, " +
                "mStringExpression=null}, tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, persistencePolicy=0, " +
                "displayPolicy=0), tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), persistencePolicy=0, " +
                "displayPolicy=0)"
        )
    }

    @Test
    public fun noDataComplicationData_longText() {
        val data = NoDataComplicationData(
            LongTextComplicationData.Builder(
                "text".complicationText,
                "content description".complicationText
            ).setDataSource(dataSourceA).build()
        )
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                    .setPlaceholder(
                        WireComplicationDataBuilder(WireComplicationData.TYPE_LONG_TEXT)
                            .setLongText(WireComplicationText.plainText("text"))
                            .setContentDescription(
                                WireComplicationText.plainText("content description")
                            )
                            .setDataSource(dataSourceA)
                            .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                            .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                            .build()
                    )
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as NoDataComplicationData
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")

        val data2 = NoDataComplicationData(
            LongTextComplicationData.Builder(
                "text".complicationText,
                "content description".complicationText
            ).setDataSource(dataSourceA).build()
        )
        val data3 = NoDataComplicationData(
            LongTextComplicationData.Builder(
                ComplicationText.PLACEHOLDER,
                "content description".complicationText
            ).setDataSource(dataSourceB).build()
        )

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "NoDataComplicationData(placeholder=LongTextComplicationData(" +
                "text=ComplicationText{mSurroundingText=text, mTimeDependentText=null, " +
                "mStringExpression=null}, title=null, monochromaticImage=null, smallImage=null, " +
                "contentDescription=ComplicationText{mSurroundingText=content description, " +
                "mTimeDependentText=null, mStringExpression=null}), " +
                "tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, persistencePolicy=0, " +
                "displayPolicy=0), tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), persistencePolicy=0, " +
                "displayPolicy=0)"
        )
    }

    @Test
    public fun noDataComplicationData_rangedValue() {
        val data = NoDataComplicationData(
            RangedValueComplicationData.Builder(
                value = RangedValueComplicationData.PLACEHOLDER,
                min = 0f,
                max = 100f,
                "content description".complicationText
            )
                .setText(ComplicationText.PLACEHOLDER)
                .setDataSource(dataSourceA)
                .build()
        )
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                    .setPlaceholder(
                        WireComplicationDataBuilder(WireComplicationData.TYPE_RANGED_VALUE)
                            .setRangedValue(RangedValueComplicationData.PLACEHOLDER)
                            .setRangedValueType(RangedValueComplicationData.TYPE_UNDEFINED)
                            .setRangedMinValue(0f)
                            .setRangedMaxValue(100f)
                            .setShortText(ComplicationText.PLACEHOLDER.toWireComplicationText())
                            .setContentDescription(
                                WireComplicationText.plainText("content description")
                            )
                            .setDataSource(dataSourceA)
                            .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                            .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                            .build()
                    )
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as NoDataComplicationData
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")

        val data2 = NoDataComplicationData(
            RangedValueComplicationData.Builder(
                value = RangedValueComplicationData.PLACEHOLDER,
                min = 0f,
                max = 100f,
                "content description".complicationText
            )
                .setText(ComplicationText.PLACEHOLDER)
                .setDataSource(dataSourceA)
                .build()
        )
        val data3 = NoDataComplicationData(
            RangedValueComplicationData.Builder(
                value = RangedValueComplicationData.PLACEHOLDER,
                min = 0f,
                max = 100f,
                "content description".complicationText
            )
                .setText(ComplicationText.PLACEHOLDER)
                .build()
        )

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "NoDataComplicationData(placeholder=RangedValueComplicationData(" +
                "value=3.4028235E38, dynamicValue=null, valueType=0, min=0.0, max=100.0, " +
                "monochromaticImage=null, smallImage=null, title=null, text=ComplicationText{" +
                "mSurroundingText=__placeholder__, mTimeDependentText=null, " +
                "mStringExpression=null}, contentDescription=ComplicationText{" +
                "mSurroundingText=content description, mTimeDependentText=null, " +
                "mStringExpression=null}), tapActionLostDueToSerialization=false, tapAction=null," +
                " validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, colorRamp=null, persistencePolicy=0, " +
                "displayPolicy=0), tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), persistencePolicy=0, " +
                "displayPolicy=0)"
        )
    }

    @Test
    public fun noDataComplicationData_goalProgress() {
        val data = NoDataComplicationData(
            GoalProgressComplicationData.Builder(
                value = GoalProgressComplicationData.PLACEHOLDER,
                targetValue = 10000f,
                contentDescription = "content description".complicationText
            )
                .setText(ComplicationText.PLACEHOLDER)
                .setDataSource(dataSourceA)
                .setColorRamp(ColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE), false))
                .build()
        )
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                    .setPlaceholder(
                        WireComplicationDataBuilder(WireComplicationData.TYPE_GOAL_PROGRESS)
                            .setRangedValue(GoalProgressComplicationData.PLACEHOLDER)
                            .setTargetValue(10000f)
                            .setShortText(ComplicationText.PLACEHOLDER.toWireComplicationText())
                            .setContentDescription(
                                WireComplicationText.plainText("content description")
                            )
                            .setDataSource(dataSourceA)
                            .setColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE))
                            .setColorRampIsSmoothShaded(false)
                            .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                            .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                            .build()
                    )
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as NoDataComplicationData
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")

        val data2 = NoDataComplicationData(
            GoalProgressComplicationData.Builder(
                value = GoalProgressComplicationData.PLACEHOLDER,
                targetValue = 10000f,
                contentDescription = "content description".complicationText
            )
                .setText(ComplicationText.PLACEHOLDER)
                .setDataSource(dataSourceA)
                .setColorRamp(ColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE), false))
                .build()
        )
        val data3 = NoDataComplicationData(
            GoalProgressComplicationData.Builder(
                value = GoalProgressComplicationData.PLACEHOLDER,
                targetValue = 10000f,
                contentDescription = "content description".complicationText
            )
                .setText(ComplicationText.PLACEHOLDER)
                .build()
        )

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "NoDataComplicationData(placeholder=GoalProgressComplicationData(" +
                "value=3.4028235E38, dynamicValue=null, targetValue=10000.0, " +
                "monochromaticImage=null, smallImage=null, title=null, text=ComplicationText{" +
                "mSurroundingText=__placeholder__, mTimeDependentText=null, " +
                "mStringExpression=null}, contentDescription=ComplicationText{" +
                "mSurroundingText=content description, mTimeDependentText=null, " +
                "mStringExpression=null}), tapActionLostDueToSerialization=false, tapAction=null," +
                " validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, " +
                "colorRamp=ColorRamp(colors=[-65536, -16711936, -16776961], interpolated=false), " +
                "persistencePolicy=0, displayPolicy=0), tapActionLostDueToSerialization=false, " +
                "tapAction=null, validTimeRange=TimeRange(startDateTimeMillis=" +
                "-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), persistencePolicy=0, " +
                "displayPolicy=0)"
        )
    }

    @Test
    public fun noDataComplicationData_weightedElements() {
        val data = NoDataComplicationData(
            WeightedElementsComplicationData.Builder(
                listOf(
                    WeightedElementsComplicationData.Element(0.5f, Color.RED),
                    WeightedElementsComplicationData.Element(1f, Color.GREEN),
                    WeightedElementsComplicationData.Element(2f, Color.BLUE),
                ),
                contentDescription = "content description".complicationText
            )
                .setTitle("calories".complicationText)
                .setElementBackgroundColor(Color.GRAY)
                .setDataSource(dataSourceA)
                .build()
        )
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                    .setPlaceholder(
                        WireComplicationDataBuilder(WireComplicationData.TYPE_WEIGHTED_ELEMENTS)
                            .setElementWeights(floatArrayOf(0.5f, 1f, 2f))
                            .setElementColors(intArrayOf(Color.RED, Color.GREEN, Color.BLUE))
                            .setElementBackgroundColor(Color.GRAY)
                            .setShortTitle(WireComplicationText.plainText("calories"))
                            .setContentDescription(
                                WireComplicationText.plainText("content description")
                            )
                            .setDataSource(dataSourceA)
                            .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                            .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                            .build()
                    )
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as NoDataComplicationData
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")

        val data2 = NoDataComplicationData(
            WeightedElementsComplicationData.Builder(
                listOf(
                    WeightedElementsComplicationData.Element(0.5f, Color.RED),
                    WeightedElementsComplicationData.Element(1f, Color.GREEN),
                    WeightedElementsComplicationData.Element(2f, Color.BLUE),
                ),
                contentDescription = "content description".complicationText
            )
                .setTitle("calories".complicationText)
                .setElementBackgroundColor(Color.GRAY)
                .setDataSource(dataSourceA)
                .build()
        )
        val data3 = NoDataComplicationData(
            WeightedElementsComplicationData.Builder(
                listOf(
                    WeightedElementsComplicationData.Element(0.5f, Color.RED),
                    WeightedElementsComplicationData.Element(1f, Color.GREEN),
                ),
                contentDescription = "content description".complicationText
            )
                .setTitle("calories".complicationText)
                .setDataSource(dataSourceA)
                .build()
        )

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "NoDataComplicationData(placeholder=WeightedElementsComplicationData(" +
                "elements=Element(color=-65536, weight=0.5), Element(color=-16711936, " +
                "weight=1.0), Element(color=-16776961, weight=2.0), " +
                "elementBackgroundColor=-7829368, monochromaticImage=null, smallImage=null, " +
                "title=ComplicationText{mSurroundingText=calories, mTimeDependentText=null, " +
                "mStringExpression=null}, text=null, contentDescription=ComplicationText{" +
                "mSurroundingText=content description, mTimeDependentText=null, " +
                "mStringExpression=null}), tapActionLostDueToSerialization=false, tapAction=null," +
                " validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, persistencePolicy=0, " +
                "displayPolicy=0), tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), persistencePolicy=0, " +
                "displayPolicy=0)"
        )
    }

    @Test
    public fun noDataComplicationData_rangedValue_with_ColorRange() {
        val data = NoDataComplicationData(
            RangedValueComplicationData.Builder(
                value = RangedValueComplicationData.PLACEHOLDER,
                min = 0f,
                max = 100f,
                "content description".complicationText
            )
                .setText(ComplicationText.PLACEHOLDER)
                .setDataSource(dataSourceA)
                .setColorRamp(ColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE), true))
                .setValueType(RangedValueComplicationData.TYPE_RATING)
                .build()
        )
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                    .setPlaceholder(
                        WireComplicationDataBuilder(WireComplicationData.TYPE_RANGED_VALUE)
                            .setRangedValue(RangedValueComplicationData.PLACEHOLDER)
                            .setRangedValueType(RangedValueComplicationData.TYPE_RATING)
                            .setRangedMinValue(0f)
                            .setRangedMaxValue(100f)
                            .setShortText(ComplicationText.PLACEHOLDER.toWireComplicationText())
                            .setContentDescription(
                                WireComplicationText.plainText("content description")
                            )
                            .setDataSource(dataSourceA)
                            .setColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE))
                            .setColorRampIsSmoothShaded(true)
                            .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                            .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                            .build()
                    )
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as NoDataComplicationData
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")

        val data2 = NoDataComplicationData(
            RangedValueComplicationData.Builder(
                value = RangedValueComplicationData.PLACEHOLDER,
                min = 0f,
                max = 100f,
                "content description".complicationText
            )
                .setText(ComplicationText.PLACEHOLDER)
                .setDataSource(dataSourceA)
                .setColorRamp(ColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE), true))
                .setValueType(RangedValueComplicationData.TYPE_RATING)
                .build()
        )
        val data3 = NoDataComplicationData(
            RangedValueComplicationData.Builder(
                value = RangedValueComplicationData.PLACEHOLDER,
                min = 0f,
                max = 100f,
                "content description".complicationText
            )
                .setText(ComplicationText.PLACEHOLDER)
                .build()
        )

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "NoDataComplicationData(placeholder=RangedValueComplicationData(" +
                "value=3.4028235E38, dynamicValue=null, valueType=1, min=0.0, max=100.0, " +
                "monochromaticImage=null, smallImage=null, title=null, text=ComplicationText{" +
                "mSurroundingText=__placeholder__, mTimeDependentText=null, " +
                "mStringExpression=null}, contentDescription=ComplicationText{" +
                "mSurroundingText=content description, mTimeDependentText=null, " +
                "mStringExpression=null}), tapActionLostDueToSerialization=false, tapAction=null," +
                " validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, colorRamp=ColorRamp(colors=[-65536, " +
                "-16711936, -16776961], interpolated=true), persistencePolicy=0, " +
                "displayPolicy=0), tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), persistencePolicy=0, " +
                "displayPolicy=0)"
        )
    }

    @Test
    public fun noDataComplicationData_monochromaticImage() {
        val data = NoDataComplicationData(
            MonochromaticImageComplicationData.Builder(
                MonochromaticImage.PLACEHOLDER,
                "content description".complicationText
            ).setDataSource(dataSourceA).build()
        )
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                    .setPlaceholder(
                        WireComplicationDataBuilder(WireComplicationData.TYPE_ICON)
                            .setIcon(createPlaceholderIcon())
                            .setContentDescription(
                                WireComplicationText.plainText("content description")
                            )
                            .setDataSource(dataSourceA)
                            .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                            .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                            .build()
                    )
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as NoDataComplicationData
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")

        val data2 = NoDataComplicationData(
            MonochromaticImageComplicationData.Builder(
                MonochromaticImage.PLACEHOLDER,
                "content description".complicationText
            ).setDataSource(dataSourceA).build()
        )

        val image = MonochromaticImage.Builder(icon).build()
        val data3 = NoDataComplicationData(
            MonochromaticImageComplicationData.Builder(
                image,
                "content description".complicationText
            ).setDataSource(dataSourceB).build()
        )

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "NoDataComplicationData(placeholder=MonochromaticImageComplicationData(" +
                "monochromaticImage=MonochromaticImage(image=Icon(typ=RESOURCE pkg= " +
                "id=0xffffffff), ambientImage=null), contentDescription=ComplicationText{" +
                "mSurroundingText=content description, mTimeDependentText=null, " +
                "mStringExpression=null}), tapActionLostDueToSerialization=false, tapAction=null," +
                " validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, persistencePolicy=0, " +
                "displayPolicy=0), tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), persistencePolicy=0, " +
                "displayPolicy=0)"
        )
    }

    @Test
    public fun noDataComplicationData_smallImage() {
        val data = NoDataComplicationData(
            SmallImageComplicationData.Builder(
                SmallImage.PLACEHOLDER,
                "content description".complicationText
            ).setDataSource(dataSourceA).build()
        )
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                    .setPlaceholder(
                        WireComplicationDataBuilder(WireComplicationData.TYPE_SMALL_IMAGE)
                            .setSmallImage(createPlaceholderIcon())
                            .setSmallImageStyle(WireComplicationData.IMAGE_STYLE_ICON)
                            .setContentDescription(
                                WireComplicationText.plainText("content description")
                            )
                            .setDataSource(dataSourceA)
                            .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                            .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                            .build()
                    )
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as NoDataComplicationData
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")

        val data2 = NoDataComplicationData(
            SmallImageComplicationData.Builder(
                SmallImage.PLACEHOLDER,
                "content description".complicationText
            ).setDataSource(dataSourceA).build()
        )

        val image = SmallImage.Builder(icon, SmallImageType.PHOTO).build()
        val data3 = NoDataComplicationData(
            SmallImageComplicationData.Builder(
                image,
                "content description".complicationText
            ).setDataSource(dataSourceA).build()
        )

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "NoDataComplicationData(placeholder=SmallImageComplicationData(smallImage=" +
                "SmallImage(image=Icon(typ=RESOURCE pkg= id=0xffffffff), type=ICON, " +
                "ambientImage=null), contentDescription=ComplicationText{mSurroundingText=" +
                "content description, mTimeDependentText=null, mStringExpression=null}), " +
                "tapActionLostDueToSerialization=false, tapAction=null, validTimeRange=TimeRange(" +
                "startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, persistencePolicy=0, " +
                "displayPolicy=0), tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), persistencePolicy=0, " +
                "displayPolicy=0)"
        )
    }

    @Test
    public fun noDataComplicationData_photoImage() {
        val data = NoDataComplicationData(
            PhotoImageComplicationData.Builder(
                PhotoImageComplicationData.PLACEHOLDER,
                "content description".complicationText
            ).setDataSource(dataSourceA).build()
        )
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                    .setPlaceholder(
                        WireComplicationDataBuilder(WireComplicationData.TYPE_LARGE_IMAGE)
                            .setLargeImage(createPlaceholderIcon())
                            .setContentDescription(
                                WireComplicationText.plainText("content description")
                            )
                            .setDataSource(dataSourceA)
                            .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                            .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                            .build()
                    )
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
        testRoundTripConversions(data)
        val deserialized = serializeAndDeserialize(data) as NoDataComplicationData
        assertThat(deserialized.contentDescription!!.getTextAt(resources, Instant.EPOCH))
            .isEqualTo("content description")

        val data2 = NoDataComplicationData(
            PhotoImageComplicationData.Builder(
                PhotoImageComplicationData.PLACEHOLDER,
                "content description".complicationText
            ).setDataSource(dataSourceA).build()
        )

        val data3 = NoDataComplicationData(
            PhotoImageComplicationData.Builder(
                icon,
                "content description".complicationText
            ).setDataSource(dataSourceA).build()
        )

        assertThat(data).isEqualTo(data2)
        assertThat(data).isNotEqualTo(data3)
        assertThat(data.hashCode()).isEqualTo(data2.hashCode())
        assertThat(data.hashCode()).isNotEqualTo(data3.hashCode())
        assertThat(data.toString()).isEqualTo(
            "NoDataComplicationData(placeholder=PhotoImageComplicationData(" +
                "photoImage=Icon(typ=RESOURCE pkg= id=0xffffffff), " +
                "contentDescription=ComplicationText{mSurroundingText=content description, " +
                "mTimeDependentText=null, mStringExpression=null}), " +
                "tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), " +
                "dataSource=ComponentInfo{com.pkg_a/com.a}, persistencePolicy=0, " +
                "displayPolicy=0), tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(startDateTimeMillis=-1000000000-01-01T00:00:00Z, " +
                "endDateTimeMillis=+1000000000-12-31T23:59:59.999999999Z), persistencePolicy=0, " +
                "displayPolicy=0)"
        )
    }

    private fun testRoundTripConversions(data: ComplicationData) {
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                data.asWireComplicationData().toApiComplicationData().asWireComplicationData()
            )
    }

    private fun serializeAndDeserialize(data: ComplicationData): ComplicationData {
        val stream = ByteArrayOutputStream()
        val objectOutputStream = ObjectOutputStream(stream)
        objectOutputStream.writeObject(data.asWireComplicationData())
        objectOutputStream.close()
        val byteArray = stream.toByteArray()

        val objectInputStream = ObjectInputStream(ByteArrayInputStream(byteArray))
        val wireData = objectInputStream.readObject() as WireComplicationData
        objectInputStream.close()
        return wireData.toApiComplicationData()
    }
}

@RunWith(SharedRobolectricTestRunner::class)
@Suppress("NewApi")
public class FromWireComplicationDataTest {
    @Test
    public fun noDataComplicationData() {
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                .setPlaceholder(null)
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                .build(),
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
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
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
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                .build(),
            ComplicationType.LONG_TEXT
        )
    }

    @Test
    public fun rangedValueComplicationData_withFixedValue() {
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_RANGED_VALUE)
                .setRangedValue(95f)
                .setRangedMinValue(0f)
                .setRangedMaxValue(100f)
                .setShortTitle(WireComplicationText.plainText("battery"))
                .setContentDescription(WireComplicationText.plainText("content description"))
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                .build(),
            ComplicationType.RANGED_VALUE
        )
    }

    @Test
    public fun rangedValueComplicationData_withDynamicValue() {
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_RANGED_VALUE)
                .setRangedDynamicValue(byteArrayOf(42, 107).toDynamicFloat())
                .setRangedMinValue(0f)
                .setRangedMaxValue(100f)
                .setShortTitle(WireComplicationText.plainText("battery"))
                .setContentDescription(WireComplicationText.plainText("content description"))
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                .build(),
            ComplicationType.RANGED_VALUE
        )
    }

    @Test
    public fun rangedValueComplicationData_drawSegmented() {
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_RANGED_VALUE)
                .setRangedValue(95f)
                .setRangedMinValue(0f)
                .setRangedMaxValue(100f)
                .setShortTitle(WireComplicationText.plainText("battery"))
                .setContentDescription(WireComplicationText.plainText("content description"))
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                .build(),
            ComplicationType.RANGED_VALUE
        )
    }

    @Test
    public fun goalProgressComplicationData_withFixedValue() {
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_GOAL_PROGRESS)
                .setRangedValue(1200f)
                .setTargetValue(10000f)
                .setShortTitle(WireComplicationText.plainText("steps"))
                .setContentDescription(WireComplicationText.plainText("content description"))
                .setColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE))
                .setColorRampIsSmoothShaded(false)
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                .build(),
            ComplicationType.GOAL_PROGRESS
        )
    }

    @Test
    public fun goalProgressComplicationData_withDynamicValue() {
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_GOAL_PROGRESS)
                .setRangedDynamicValue(byteArrayOf(42, 107).toDynamicFloat())
                .setTargetValue(10000f)
                .setShortTitle(WireComplicationText.plainText("steps"))
                .setContentDescription(WireComplicationText.plainText("content description"))
                .setColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE))
                .setColorRampIsSmoothShaded(false)
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                .build(),
            ComplicationType.GOAL_PROGRESS
        )
    }

    @Test
    public fun weightedElementsComplicationData() {
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_WEIGHTED_ELEMENTS)
                .setElementWeights(floatArrayOf(0.5f, 1f, 2f))
                .setElementColors(intArrayOf(Color.RED, Color.GREEN, Color.BLUE))
                .setElementBackgroundColor(Color.DKGRAY)
                .setShortTitle(WireComplicationText.plainText("calories"))
                .setContentDescription(WireComplicationText.plainText("content description"))
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                .build(),
            ComplicationType.WEIGHTED_ELEMENTS
        )
    }

    @Test
    public fun monochromaticImageComplicationData() {
        val icon = Icon.createWithContentUri("someuri")
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_ICON)
                .setIcon(icon)
                .setContentDescription(WireComplicationText.plainText("content description"))
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
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
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                .build(),
            ComplicationType.SMALL_IMAGE
        )
    }

    @Test
    public fun photoImageComplicationData() {
        val icon = Icon.createWithContentUri("someuri")
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_LARGE_IMAGE)
                .setLargeImage(icon)
                .setContentDescription(WireComplicationText.plainText("content description"))
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                .build(),
            ComplicationType.PHOTO_IMAGE
        )
    }

    @Test
    public fun noPermissionComplicationData() {
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_NO_PERMISSION)
                .setShortText(WireComplicationText.plainText("needs location"))
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                .build(),
            ComplicationType.NO_PERMISSION
        )
    }

    @Test
    public fun noDataComplicationData_shortText() {
        val icon = Icon.createWithContentUri("someuri")
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                .setPlaceholder(
                    WireComplicationDataBuilder(WireComplicationData.TYPE_SHORT_TEXT)
                        .setContentDescription(
                            WireComplicationText.plainText("content description")
                        )
                        .setShortText(WireComplicationText.plainText("text"))
                        .setShortTitle(WireComplicationText.plainText("title"))
                        .setIcon(icon)
                        .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                        .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                        .build()
                )
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                .build(),
            ComplicationType.NO_DATA
        )
    }

    @Test
    public fun noDataComplicationData_longText() {
        val icon = Icon.createWithContentUri("someuri")
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                .setPlaceholder(
                    WireComplicationDataBuilder(WireComplicationData.TYPE_LONG_TEXT)
                        .setContentDescription(
                            WireComplicationText.plainText("content description")
                        )
                        .setLongText(WireComplicationText.plainText("text"))
                        .setLongTitle(WireComplicationText.plainText("title"))
                        .setIcon(icon)
                        .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                        .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                        .build()
                )
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                .build(),
            ComplicationType.NO_DATA
        )
    }

    @Test
    public fun noDataComplicationData_rangedValue() {
        val icon = Icon.createWithContentUri("someuri")
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                .setPlaceholder(
                    WireComplicationDataBuilder(WireComplicationData.TYPE_RANGED_VALUE)
                        .setContentDescription(
                            WireComplicationText.plainText("content description")
                        )
                        .setRangedValue(75f)
                        .setRangedValueType(RangedValueComplicationData.TYPE_UNDEFINED)
                        .setRangedMinValue(0f)
                        .setRangedMaxValue(100f)
                        .setShortTitle(WireComplicationText.plainText("battery"))
                        .setIcon(icon)
                        .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                        .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                        .build()
                )
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                .build(),
            ComplicationType.NO_DATA
        )
    }

    @Test
    public fun noDataComplicationData_goalProgress() {
        val icon = Icon.createWithContentUri("someuri")
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                .setPlaceholder(
                    WireComplicationDataBuilder(WireComplicationData.TYPE_GOAL_PROGRESS)
                        .setRangedValue(1200f)
                        .setTargetValue(10000f)
                        .setShortTitle(WireComplicationText.plainText("steps"))
                        .setContentDescription(
                            WireComplicationText.plainText("content description")
                        )
                        .setColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE))
                        .setColorRampIsSmoothShaded(true)
                        .setIcon(icon)
                        .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                        .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                        .build()
                )
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                .build(),
            ComplicationType.NO_DATA
        )
    }

    @Test
    public fun noDataComplicationData_weightedElementsComplicationData() {
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                .setPlaceholder(
                    WireComplicationDataBuilder(WireComplicationData.TYPE_WEIGHTED_ELEMENTS)
                        .setElementWeights(floatArrayOf(0.5f, 1f, 2f))
                        .setElementColors(intArrayOf(Color.RED, Color.GREEN, Color.BLUE))
                        .setElementBackgroundColor(Color.DKGRAY)
                        .setShortTitle(WireComplicationText.plainText("calories"))
                        .setContentDescription(
                            WireComplicationText.plainText("content description")
                        )
                        .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                        .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                        .build()
                )
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                .build(),
            ComplicationType.NO_DATA
        )
    }

    @Test
    public fun noDataComplicationData_smallImage() {
        val icon = Icon.createWithContentUri("someuri")
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                .setPlaceholder(
                    WireComplicationDataBuilder(WireComplicationData.TYPE_SMALL_IMAGE)
                        .setSmallImage(icon)
                        .setSmallImageStyle(WireComplicationData.IMAGE_STYLE_PHOTO)
                        .setContentDescription(
                            WireComplicationText.plainText("content description")
                        )
                        .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                        .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                        .build()
                )
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                .build(),
            ComplicationType.NO_DATA
        )
    }

    @Test
    public fun noDataComplicationData_monochromaticImage() {
        val icon = Icon.createWithContentUri("someuri")
        assertRoundtrip(
            WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                .setPlaceholder(
                    WireComplicationDataBuilder(WireComplicationData.TYPE_ICON)
                        .setIcon(icon)
                        .setContentDescription(
                            WireComplicationText.plainText("content description")
                        )
                        .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                        .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                        .build()
                )
                .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                .build(),
            ComplicationType.NO_DATA
        )
    }

    private fun assertRoundtrip(wireData: WireComplicationData, type: ComplicationType) {
        val data = wireData.toApiComplicationData()
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
            ShortTextComplicationData.Builder("text".complicationText, ComplicationText.EMPTY)
                .setTapAction(mPendingIntent)
                .build().asWireComplicationData().tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun longTextComplicationData() {
        assertThat(
            LongTextComplicationData.Builder("text".complicationText, ComplicationText.EMPTY)
                .setTapAction(mPendingIntent)
                .build().asWireComplicationData().tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun rangedValueComplicationData() {
        assertThat(
            RangedValueComplicationData.Builder(
                value = 95f, min = 0f, max = 100f,
                contentDescription = ComplicationText.EMPTY
            )
                .setText("battery".complicationText)
                .setTapAction(mPendingIntent)
                .build().asWireComplicationData().tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun goalProgressComplicationData() {
        assertThat(
            @Suppress("NewApi")
            GoalProgressComplicationData.Builder(
                value = 1200f, targetValue = 10000f,
                contentDescription = "content description".complicationText
            )
                .setTitle("steps".complicationText)
                .setTapAction(mPendingIntent)
                .setColorRamp(ColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE), true))
                .build().asWireComplicationData().tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun weightedElementsComplicationData() {
        assertThat(
            @Suppress("NewApi")
            WeightedElementsComplicationData.Builder(
                listOf(
                    WeightedElementsComplicationData.Element(0.5f, Color.RED),
                    WeightedElementsComplicationData.Element(1f, Color.GREEN),
                    WeightedElementsComplicationData.Element(2f, Color.BLUE),
                ),
                contentDescription = "content description".complicationText
            )
                .setTitle("calories".complicationText)
                .setTapAction(mPendingIntent)
                .build().asWireComplicationData().tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun monochromaticImageComplicationData() {
        val icon = Icon.createWithContentUri("someuri")
        val image = MonochromaticImage.Builder(icon).build()
        assertThat(
            MonochromaticImageComplicationData.Builder(image, ComplicationText.EMPTY)
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
            SmallImageComplicationData.Builder(image, ComplicationText.EMPTY)
                .setTapAction(mPendingIntent).build()
                .asWireComplicationData()
                .tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun photoImageComplicationData() {
        val icon = Icon.createWithContentUri("someuri")
        assertThat(
            PhotoImageComplicationData.Builder(icon, ComplicationText.EMPTY)
                .setTapAction(mPendingIntent).build()
                .asWireComplicationData()
                .tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun noDataComplicationData() {
        assertThat(
            NoDataComplicationData(
                ShortTextComplicationData.Builder(
                    ComplicationText.PLACEHOLDER,
                    ComplicationText.EMPTY
                ).setTapAction(mPendingIntent).build()
            ).asWireComplicationData().placeholder?.tapAction
        ).isEqualTo(mPendingIntent)
    }
}

@RunWith(SharedRobolectricTestRunner::class)
public class RoundtripTapActionTest {
    private val mPendingIntent = PendingIntent.getBroadcast(
        ApplicationProvider.getApplicationContext(),
        0,
        Intent(),
        0
    )

    @Test
    public fun shortTextComplicationData() {
        assertThat(
            ShortTextComplicationData.Builder("text".complicationText, ComplicationText.EMPTY)
                .setTapAction(mPendingIntent)
                .build().asWireComplicationData().toApiComplicationData().tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun longTextComplicationData() {
        assertThat(
            LongTextComplicationData.Builder("text".complicationText, ComplicationText.EMPTY)
                .setTapAction(mPendingIntent)
                .build().asWireComplicationData().toApiComplicationData().tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun rangedValueComplicationData() {
        assertThat(
            RangedValueComplicationData.Builder(
                value = 95f, min = 0f, max = 100f,
                contentDescription = ComplicationText.EMPTY
            )
                .setText("battery".complicationText)
                .setTapAction(mPendingIntent)
                .build().asWireComplicationData().toApiComplicationData().tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun goalProgressComplicationData() {
        assertThat(
            @Suppress("NewApi")
            GoalProgressComplicationData.Builder(
                value = 1200f, targetValue = 10000f,
                contentDescription = "content description".complicationText
            )
                .setTitle("steps".complicationText)
                .setTapAction(mPendingIntent)
                .setColorRamp(ColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE), false))
                .build().asWireComplicationData().toApiComplicationData().tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun weightedElementsComplicationData() {
        assertThat(
            @Suppress("NewApi")
            WeightedElementsComplicationData.Builder(
                listOf(
                    WeightedElementsComplicationData.Element(0.5f, Color.RED),
                    WeightedElementsComplicationData.Element(1f, Color.GREEN),
                    WeightedElementsComplicationData.Element(2f, Color.BLUE),
                ),
                contentDescription = "content description".complicationText
            )
                .setTitle("calories".complicationText)
                .setTapAction(mPendingIntent)
                .build().asWireComplicationData().toApiComplicationData().tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun monochromaticImageComplicationData() {
        val icon = Icon.createWithContentUri("someuri")
        val image = MonochromaticImage.Builder(icon).build()
        assertThat(
            MonochromaticImageComplicationData.Builder(image, ComplicationText.EMPTY)
                .setTapAction(mPendingIntent)
                .build()
                .asWireComplicationData()
                .toApiComplicationData()
                .tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun smallImageComplicationData() {
        val icon = Icon.createWithContentUri("someuri")
        val image = SmallImage.Builder(icon, SmallImageType.PHOTO).build()
        assertThat(
            SmallImageComplicationData.Builder(image, ComplicationText.EMPTY)
                .setTapAction(mPendingIntent).build()
                .asWireComplicationData()
                .toApiComplicationData()
                .tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun photoImageComplicationData() {
        val icon = Icon.createWithContentUri("someuri")
        assertThat(
            PhotoImageComplicationData.Builder(icon, ComplicationText.EMPTY)
                .setTapAction(mPendingIntent).build()
                .asWireComplicationData()
                .toApiComplicationData()
                .tapAction
        ).isEqualTo(mPendingIntent)
    }

    @Test
    public fun noDataComplicationData() {
        assertThat(
            NoDataComplicationData(
                MonochromaticImageComplicationData.Builder(
                    MonochromaticImage.PLACEHOLDER,
                    ComplicationText.EMPTY
                ).setTapAction(mPendingIntent).build()
            ).asWireComplicationData().toApiComplicationData().tapAction
        ).isEqualTo(mPendingIntent)
    }
}

@RunWith(SharedRobolectricTestRunner::class)
@Suppress("NewApi")
public class ValidTimeRangeTest {
    private val testStartInstant = Instant.ofEpochMilli(1000L)
    private val testEndDateInstant = Instant.ofEpochMilli(2000L)

    @Test
    public fun shortTextComplicationData() {
        val data = ShortTextComplicationData.Builder(
            "text".complicationText, ComplicationText.EMPTY
        )
            .setValidTimeRange(TimeRange.between(testStartInstant, testEndDateInstant))
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(WireComplicationText.plainText("text"))
                    .setStartDateTimeMillis(testStartInstant.toEpochMilli())
                    .setEndDateTimeMillis(testEndDateInstant.toEpochMilli())
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
    }

    @Test
    public fun longTextComplicationData() {
        val data = LongTextComplicationData.Builder("text".complicationText, ComplicationText.EMPTY)
            .setValidTimeRange(TimeRange.between(testStartInstant, testEndDateInstant))
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_LONG_TEXT)
                    .setLongText(WireComplicationText.plainText("text"))
                    .setStartDateTimeMillis(testStartInstant.toEpochMilli())
                    .setEndDateTimeMillis(testEndDateInstant.toEpochMilli())
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
    }

    @Test
    public fun rangedValueComplicationData() {
        val data = RangedValueComplicationData.Builder(
            value = 95f, min = 0f, max = 100f,
            contentDescription = ComplicationText.EMPTY
        )
            .setText("battery".complicationText)
            .setValidTimeRange(TimeRange.between(testStartInstant, testEndDateInstant))
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_RANGED_VALUE)
                    .setRangedValue(95f)
                    .setRangedValueType(RangedValueComplicationData.TYPE_UNDEFINED)
                    .setRangedMinValue(0f)
                    .setRangedMaxValue(100f)
                    .setShortText(WireComplicationText.plainText("battery"))
                    .setStartDateTimeMillis(testStartInstant.toEpochMilli())
                    .setEndDateTimeMillis(testEndDateInstant.toEpochMilli())
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
    }

    @Test
    public fun goalProgressComplicationData() {
        val data = GoalProgressComplicationData.Builder(
            value = 1200f, targetValue = 10000f,
            contentDescription = "content description".complicationText
        )
            .setTitle("steps".complicationText)
            .setValidTimeRange(TimeRange.between(testStartInstant, testEndDateInstant))
            .setColorRamp(ColorRamp(intArrayOf(Color.YELLOW, Color.MAGENTA), true))
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_GOAL_PROGRESS)
                    .setRangedValue(1200f)
                    .setTargetValue(10000f)
                    .setShortTitle(WireComplicationText.plainText("steps"))
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setColorRamp(intArrayOf(Color.YELLOW, Color.MAGENTA))
                    .setColorRampIsSmoothShaded(true)
                    .setStartDateTimeMillis(testStartInstant.toEpochMilli())
                    .setEndDateTimeMillis(testEndDateInstant.toEpochMilli())
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
    }

    @Test
    public fun weightedElementsComplicationData() {
        val data = WeightedElementsComplicationData.Builder(
            listOf(
                WeightedElementsComplicationData.Element(0.5f, Color.RED),
                WeightedElementsComplicationData.Element(1f, Color.GREEN),
                WeightedElementsComplicationData.Element(2f, Color.BLUE),
            ),
            contentDescription = "content description".complicationText
        )
            .setTitle("calories".complicationText)
            .setValidTimeRange(TimeRange.between(testStartInstant, testEndDateInstant))
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_WEIGHTED_ELEMENTS)
                    .setElementWeights(floatArrayOf(0.5f, 1f, 2f))
                    .setElementColors(intArrayOf(Color.RED, Color.GREEN, Color.BLUE))
                    .setElementBackgroundColor(Color.TRANSPARENT)
                    .setShortTitle(WireComplicationText.plainText("calories"))
                    .setContentDescription(WireComplicationText.plainText("content description"))
                    .setStartDateTimeMillis(testStartInstant.toEpochMilli())
                    .setEndDateTimeMillis(testEndDateInstant.toEpochMilli())
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
    }

    @Test
    public fun monochromaticImageComplicationData() {
        val icon = Icon.createWithContentUri("someuri")
        val image = MonochromaticImage.Builder(icon).build()
        val data = MonochromaticImageComplicationData.Builder(image, ComplicationText.EMPTY)
            .setValidTimeRange(TimeRange.between(testStartInstant, testEndDateInstant))
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_ICON)
                    .setIcon(icon)
                    .setStartDateTimeMillis(testStartInstant.toEpochMilli())
                    .setEndDateTimeMillis(testEndDateInstant.toEpochMilli())
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
    }

    @Test
    public fun smallImageComplicationData() {
        val icon = Icon.createWithContentUri("someuri")
        val image = SmallImage.Builder(icon, SmallImageType.PHOTO).build()
        val data = SmallImageComplicationData.Builder(image, ComplicationText.EMPTY)
            .setValidTimeRange(TimeRange.between(testStartInstant, testEndDateInstant))
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_SMALL_IMAGE)
                    .setSmallImage(icon)
                    .setSmallImageStyle(WireComplicationData.IMAGE_STYLE_PHOTO)
                    .setStartDateTimeMillis(testStartInstant.toEpochMilli())
                    .setEndDateTimeMillis(testEndDateInstant.toEpochMilli())
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
    }

    @Test
    public fun photoImageComplicationData() {
        val photoImage = Icon.createWithContentUri("someuri")
        val data = PhotoImageComplicationData.Builder(photoImage, ComplicationText.EMPTY)
            .setValidTimeRange(TimeRange.between(testStartInstant, testEndDateInstant))
            .build()
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_LARGE_IMAGE)
                    .setLargeImage(photoImage)
                    .setStartDateTimeMillis(testStartInstant.toEpochMilli())
                    .setEndDateTimeMillis(testEndDateInstant.toEpochMilli())
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
    }

    @Test
    public fun noDataComplicationData_shortText() {
        val data = NoDataComplicationData(
            ShortTextComplicationData.Builder("text".complicationText, ComplicationText.EMPTY)
                .build(),
        )
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                    .setPlaceholder(
                        WireComplicationDataBuilder(WireComplicationData.TYPE_SHORT_TEXT)
                            .setShortText(WireComplicationText.plainText("text"))
                            .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                            .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                            .build()
                    )
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
    }

    @Test
    public fun noDataComplicationData_longText() {
        val data = NoDataComplicationData(
            LongTextComplicationData.Builder("text".complicationText, ComplicationText.EMPTY)
                .build()
        )
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                    .setPlaceholder(
                        WireComplicationDataBuilder(WireComplicationData.TYPE_LONG_TEXT)
                            .setLongText(WireComplicationText.plainText("text"))
                            .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                            .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                            .build()
                    )
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
    }

    @Test
    public fun noDataComplicationData_rangedValue() {
        val data = NoDataComplicationData(
            RangedValueComplicationData.Builder(
                value = 95f,
                min = 0f,
                max = 100f,
                ComplicationText.EMPTY
            )
                .setText("battery".complicationText)
                .build()
        )
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                    .setPlaceholder(
                        WireComplicationDataBuilder(WireComplicationData.TYPE_RANGED_VALUE)
                            .setRangedValue(95f)
                            .setRangedValueType(RangedValueComplicationData.TYPE_UNDEFINED)
                            .setRangedMinValue(0f)
                            .setRangedMaxValue(100f)
                            .setShortText(WireComplicationText.plainText("battery"))
                            .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                            .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                            .build()
                    )
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
    }

    @Test
    public fun noDataComplicationData_goalProgress() {
        val data = NoDataComplicationData(
            GoalProgressComplicationData.Builder(
                value = 1200f, targetValue = 10000f,
                contentDescription = "content description".complicationText
            )
                .setTitle("steps".complicationText)
                .setColorRamp(ColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE), false))
                .build()
        )
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                    .setPlaceholder(
                        WireComplicationDataBuilder(WireComplicationData.TYPE_GOAL_PROGRESS)
                            .setRangedValue(1200f)
                            .setTargetValue(10000f)
                            .setShortTitle(WireComplicationText.plainText("steps"))
                            .setContentDescription(
                                WireComplicationText.plainText("content description")
                            )
                            .setColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE))
                            .setColorRampIsSmoothShaded(false)
                            .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                            .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                            .build()
                    )
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
    }

    @Test
    public fun noDataComplicationData_weightedElements() {
        val data = NoDataComplicationData(
            WeightedElementsComplicationData.Builder(
                listOf(
                    WeightedElementsComplicationData.Element(0.5f, Color.RED),
                    WeightedElementsComplicationData.Element(1f, Color.GREEN),
                    WeightedElementsComplicationData.Element(2f, Color.BLUE),
                ),
                contentDescription = "content description".complicationText
            )
                .setTitle("calories".complicationText)
                .build()
        )
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                    .setPlaceholder(
                        WireComplicationDataBuilder(WireComplicationData.TYPE_WEIGHTED_ELEMENTS)
                            .setElementWeights(floatArrayOf(0.5f, 1f, 2f))
                            .setElementColors(intArrayOf(Color.RED, Color.GREEN, Color.BLUE))
                            .setElementBackgroundColor(Color.TRANSPARENT)
                            .setShortTitle(WireComplicationText.plainText("calories"))
                            .setContentDescription(
                                WireComplicationText.plainText("content description")
                            )
                            .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                            .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                            .build()
                    )
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
    }

    @Test
    public fun noDataComplicationData_monochromaticImage() {
        val icon = Icon.createWithContentUri("someuri")
        val image = MonochromaticImage.Builder(icon).build()
        val data = NoDataComplicationData(
            MonochromaticImageComplicationData.Builder(image, ComplicationText.EMPTY).build()
        )
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                    .setPlaceholder(
                        WireComplicationDataBuilder(WireComplicationData.TYPE_ICON)
                            .setIcon(icon)
                            .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                            .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                            .build()
                    )
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
    }

    @Test
    public fun noDataComplicationData_smallImage() {
        val icon = Icon.createWithContentUri("someuri")
        val image = SmallImage.Builder(icon, SmallImageType.PHOTO).build()
        val data = NoDataComplicationData(
            SmallImageComplicationData.Builder(image, ComplicationText.EMPTY).build()
        )
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                    .setPlaceholder(
                        WireComplicationDataBuilder(WireComplicationData.TYPE_SMALL_IMAGE)
                            .setSmallImage(icon)
                            .setSmallImageStyle(WireComplicationData.IMAGE_STYLE_PHOTO)
                            .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                            .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                            .build()
                    )
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
    }

    @Test
    public fun noDataComplicationData_photoImage() {
        val icon = Icon.createWithContentUri("someuri")
        val data = NoDataComplicationData(
            PhotoImageComplicationData.Builder(icon, ComplicationText.EMPTY).build()
        )
        ParcelableSubject.assertThat(data.asWireComplicationData())
            .hasSameSerializationAs(
                WireComplicationDataBuilder(WireComplicationData.TYPE_NO_DATA)
                    .setPlaceholder(
                        WireComplicationDataBuilder(WireComplicationData.TYPE_LARGE_IMAGE)
                            .setLargeImage(icon)
                            .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                            .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                            .build()
                    )
                    .setPersistencePolicy(ComplicationPersistencePolicies.CACHING_ALLOWED)
                    .setDisplayPolicy(ComplicationDisplayPolicies.ALWAYS_DISPLAY)
                    .build()
            )
    }
}

@RunWith(SharedRobolectricTestRunner::class)
public class RedactionTest {
    @Before
    fun setup() {
        ShadowLog.setLoggable("ComplicationData", Log.INFO)
    }

    @Test
    fun shouldRedact() {
        ShadowLog.setLoggable("ComplicationData", Log.DEBUG)
        assertThat(WireComplicationData.shouldRedact()).isFalse()

        ShadowLog.setLoggable("ComplicationData", Log.INFO)
        assertThat(WireComplicationData.shouldRedact()).isTrue()
    }

    @Test
    fun shortText() {
        val data = ShortTextComplicationData.Builder(
            "text".complicationText,
            "content description".complicationText
        )
            .setTitle("title".complicationText)
            .build()

        assertThat(data.toString()).isEqualTo(
            "ShortTextComplicationData(text=ComplicationText{mSurroundingText=REDACTED, " +
                "mTimeDependentText=null, mStringExpression=null}, title=ComplicationText{" +
                "mSurroundingText=REDACTED, mTimeDependentText=null, mStringExpression=null}, " +
                "monochromaticImage=null, smallImage=null, contentDescription=ComplicationText{" +
                "mSurroundingText=REDACTED, mTimeDependentText=null, mStringExpression=null}, " +
                "tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(REDACTED), dataSource=null, persistencePolicy=0, " +
                "displayPolicy=0)"
        )
        assertThat(data.asWireComplicationData().toString()).isEqualTo(
            "ComplicationData{mType=3, mFields=REDACTED}"
        )
    }

    @Test
    fun longText() {
        val data = LongTextComplicationData.Builder(
            "text".complicationText,
            "content description".complicationText
        )
            .setTitle("title".complicationText)
            .build()

        assertThat(data.toString()).isEqualTo(
            "LongTextComplicationData(text=ComplicationText{mSurroundingText=REDACTED, " +
                "mTimeDependentText=null, mStringExpression=null}, title=ComplicationText{" +
                "mSurroundingText=REDACTED, mTimeDependentText=null, mStringExpression=null}, " +
                "monochromaticImage=null, smallImage=null, contentDescription=ComplicationText{" +
                "mSurroundingText=REDACTED, mTimeDependentText=null, mStringExpression=null}), " +
                "tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(REDACTED), dataSource=null, persistencePolicy=0, " +
                "displayPolicy=0)"
        )
        assertThat(data.asWireComplicationData().toString()).isEqualTo(
            "ComplicationData{mType=4, mFields=REDACTED}"
        )
    }

    @Test
    fun rangedValue() {
        val data = RangedValueComplicationData.Builder(
            50f,
            0f,
            100f,
            "content description".complicationText
        )
            .setText("text".complicationText)
            .setTitle("title".complicationText)
            .build()

        assertThat(data.toString()).isEqualTo(
            "RangedValueComplicationData(value=REDACTED, dynamicValue=REDACTED, " +
                "valueType=0, min=0.0, max=100.0, monochromaticImage=null, smallImage=null, " +
                "title=ComplicationText{mSurroundingText=REDACTED, mTimeDependentText=null, " +
                "mStringExpression=null}, text=ComplicationText{mSurroundingText=REDACTED, " +
                "mTimeDependentText=null, mStringExpression=null}, contentDescription=" +
                "ComplicationText{mSurroundingText=REDACTED, mTimeDependentText=null, " +
                "mStringExpression=null}), tapActionLostDueToSerialization=false, tapAction=null," +
                " validTimeRange=TimeRange(REDACTED), dataSource=null, colorRamp=null, " +
                "persistencePolicy=0, displayPolicy=0)"
        )
        assertThat(data.asWireComplicationData().toString()).isEqualTo(
            "ComplicationData{mType=5, mFields=REDACTED}"
        )
    }

    @Test
    @Suppress("NewApi")
    fun goalProgress() {
        val data = GoalProgressComplicationData.Builder(
            value = 1200f, targetValue = 10000f,
            contentDescription = "content description".complicationText
        )
            .setTitle("steps".complicationText)
            .setColorRamp(ColorRamp(intArrayOf(Color.RED, Color.GREEN, Color.BLUE), true))
            .build()

        assertThat(data.toString()).isEqualTo(
            "GoalProgressComplicationData(value=REDACTED, dynamicValue=REDACTED, " +
                "targetValue=10000.0, monochromaticImage=null, smallImage=null, " +
                "title=ComplicationText{mSurroundingText=REDACTED, mTimeDependentText=null, " +
                "mStringExpression=null}, text=null, contentDescription=ComplicationText{" +
                "mSurroundingText=REDACTED, mTimeDependentText=null, mStringExpression=null}), " +
                "tapActionLostDueToSerialization=false, tapAction=null, validTimeRange=" +
                "TimeRange(REDACTED), dataSource=null, colorRamp=ColorRamp(colors=[-65536, " +
                "-16711936, -16776961], interpolated=true), persistencePolicy=0, displayPolicy=0)"
        )
        assertThat(data.asWireComplicationData().toString()).isEqualTo(
            "ComplicationData{mType=13, mFields=REDACTED}"
        )
    }

    @Test
    fun placeholder() {
        val data = NoDataComplicationData(
            LongTextComplicationData.Builder(
                ComplicationText.PLACEHOLDER,
                ComplicationText.EMPTY
            ).build()
        )

        assertThat(data.toString()).isEqualTo(
            "NoDataComplicationData(placeholder=LongTextComplicationData(" +
                "text=ComplicationText{mSurroundingText=__placeholder__, mTimeDependentText=null," +
                " mStringExpression=null}, title=null, monochromaticImage=null, smallImage=null, " +
                "contentDescription=ComplicationText{mSurroundingText=REDACTED, " +
                "mTimeDependentText=null, mStringExpression=null}), " +
                "tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(REDACTED), dataSource=null, persistencePolicy=0, " +
                "displayPolicy=0), tapActionLostDueToSerialization=false, tapAction=null, " +
                "validTimeRange=TimeRange(REDACTED), persistencePolicy=0, displayPolicy=0)"
        )
        assertThat(data.asWireComplicationData().toString()).isEqualTo(
            "ComplicationData{mType=10, mFields=REDACTED}"
        )
    }
}

val String.complicationText get() = PlainComplicationText.Builder(this).build()
