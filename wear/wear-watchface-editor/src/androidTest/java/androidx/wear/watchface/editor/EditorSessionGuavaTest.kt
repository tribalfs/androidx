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

package androidx.wear.watchface.editor

import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.wear.complications.ComplicationBounds
import androidx.wear.complications.DefaultComplicationProviderPolicy
import androidx.wear.complications.SystemProviders
import androidx.wear.complications.data.ComplicationType
import androidx.wear.complications.data.LongTextComplicationData
import androidx.wear.complications.data.ShortTextComplicationData
import androidx.wear.watchface.CanvasComplication
import androidx.wear.watchface.Complication
import androidx.wear.watchface.ComplicationsManager
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.style.UserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema
import androidx.wear.watchface.style.UserStyleSetting
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import java.util.concurrent.TimeUnit

private const val TIMEOUT_MS = 500L

@RunWith(AndroidJUnit4::class)
@MediumTest
public class EditorSessionGuavaTest {
    private val testComponentName = ComponentName("test.package", "test.class")
    private val testEditorPackageName = "test.package"
    private val testInstanceId = "TEST_INSTANCE_ID"
    private var editorDelegate = Mockito.mock(WatchFace.EditorDelegate::class.java)
    private val screenBounds = Rect(0, 0, 400, 400)

    private val mockLeftCanvasComplication = Mockito.mock(CanvasComplication::class.java)
    private val leftComplication =
        Complication.createRoundRectComplicationBuilder(
            LEFT_COMPLICATION_ID,
            mockLeftCanvasComplication,
            listOf(
                ComplicationType.RANGED_VALUE,
                ComplicationType.LONG_TEXT,
                ComplicationType.SHORT_TEXT,
                ComplicationType.MONOCHROMATIC_IMAGE,
                ComplicationType.SMALL_IMAGE
            ),
            DefaultComplicationProviderPolicy(SystemProviders.SUNRISE_SUNSET),
            ComplicationBounds(RectF(0.2f, 0.4f, 0.4f, 0.6f))
        ).setDefaultProviderType(ComplicationType.SHORT_TEXT)
            .build()

    private val mockRightCanvasComplication = Mockito.mock(CanvasComplication::class.java)
    private val rightComplication =
        Complication.createRoundRectComplicationBuilder(
            RIGHT_COMPLICATION_ID,
            mockRightCanvasComplication,
            listOf(
                ComplicationType.RANGED_VALUE,
                ComplicationType.LONG_TEXT,
                ComplicationType.SHORT_TEXT,
                ComplicationType.MONOCHROMATIC_IMAGE,
                ComplicationType.SMALL_IMAGE
            ),
            DefaultComplicationProviderPolicy(SystemProviders.DAY_OF_WEEK),
            ComplicationBounds(RectF(0.6f, 0.4f, 0.8f, 0.6f))
        ).setDefaultProviderType(ComplicationType.SHORT_TEXT)
            .build()

    private fun createOnWatchFaceEditingTestActivity(
        userStyleSettings: List<UserStyleSetting>,
        complications: List<Complication>,
        instanceId: String? = testInstanceId,
        previewReferenceTimeMillis: Long = 12345
    ): ActivityScenario<OnWatchFaceEditingTestActivity> {
        val userStyleRepository = UserStyleRepository(UserStyleSchema(userStyleSettings))
        val complicationsManager = ComplicationsManager(complications, userStyleRepository)

        WatchFace.registerEditorDelegate(testComponentName, editorDelegate)
        Mockito.`when`(editorDelegate.complicationsManager).thenReturn(complicationsManager)
        Mockito.`when`(editorDelegate.userStyleSchema).thenReturn(userStyleRepository.schema)
        Mockito.`when`(editorDelegate.userStyle).thenReturn(userStyleRepository.userStyle)
        Mockito.`when`(editorDelegate.screenBounds).thenReturn(screenBounds)
        Mockito.`when`(editorDelegate.previewReferenceTimeMillis)
            .thenReturn(previewReferenceTimeMillis)

        return ActivityScenario.launch(
            WatchFaceEditorContractForTest().createIntent(
                ApplicationProvider.getApplicationContext<Context>(),
                EditorRequest(testComponentName, testEditorPackageName, instanceId, null)
            ).apply {
                component = ComponentName(
                    ApplicationProvider.getApplicationContext<Context>(),
                    OnWatchFaceEditingTestActivity::class.java
                )
            }
        )
    }

    @Test
    public fun getListenableComplicationPreviewData() {
        val scenario = createOnWatchFaceEditingTestActivity(
            emptyList(),
            listOf(leftComplication, rightComplication)
        )

        lateinit var listenableEditorSession: ListenableEditorSession
        scenario.onActivity { activity ->
            listenableEditorSession = activity.listenableEditorSession
        }

        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        val future = listenableEditorSession.getListenableComplicationPreviewData()
        val previewData = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)

        val leftComplicationData = previewData[LEFT_COMPLICATION_ID] as
            ShortTextComplicationData
        Truth.assertThat(
            leftComplicationData.text.getTextAt(resources, 0)
        ).isEqualTo("Left")

        val rightComplicationData = previewData[RIGHT_COMPLICATION_ID] as
            LongTextComplicationData
        Truth.assertThat(
            rightComplicationData.text.getTextAt(resources, 0)
        ).isEqualTo("Right")
    }

    @Test
    public fun listenableLaunchComplicationProviderChooser() {
        ComplicationProviderChooserContract.useTestComplicationHelperActivity = true
        val scenario = createOnWatchFaceEditingTestActivity(
            emptyList(),
            listOf(leftComplication, rightComplication)
        )

        lateinit var listenableEditorSession: ListenableEditorSession
        scenario.onActivity { activity ->
            listenableEditorSession = activity.listenableEditorSession
        }

        /**
         * Invoke [TestComplicationHelperActivity] which will change the provider (and hence
         * the preview data) for [LEFT_COMPLICATION_ID].
         */
        assertTrue(
            listenableEditorSession.listenableLaunchComplicationProviderChooser(
                LEFT_COMPLICATION_ID
            ).get(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        )

        // This should update the preview data to point to the updated provider3 data.
        val previewComplication =
            listenableEditorSession.getListenableComplicationPreviewData()
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS)[LEFT_COMPLICATION_ID]
                as LongTextComplicationData

        assertThat(
            previewComplication.text.getTextAt(
                ApplicationProvider.getApplicationContext<Context>().resources,
                0
            )
        ).isEqualTo("Provider3")
    }
}