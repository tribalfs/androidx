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
package androidx.wear.complications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.wear.complications.data.ComplicationType
import androidx.wear.complications.data.ComplicationType.LONG_TEXT
import androidx.wear.complications.data.ComplicationType.MONOCHROMATIC_IMAGE
import androidx.wear.complications.data.ComplicationType.SHORT_TEXT
import com.google.common.truth.Truth.assertThat
import org.junit.Test

public class ComplicationHelperActivityTest {

    @Test
    public fun createProviderChooserHelperIntent_action() {
        assertThat(createIntent().action)
            .isEqualTo(ComplicationHelperActivity.ACTION_START_PROVIDER_CHOOSER)
    }

    @Test
    public fun createProviderChooserHelperIntent_component() {
        assertThat(createIntent().component)
            .isEqualTo(ComponentName(context, ComplicationHelperActivity::class.java))
    }

    @Test
    public fun createProviderChooserHelperIntent_watchFaceComponentName() {
        ComponentName("package-name", "watch-face-service-name").let {
            assertThat(createIntent(watchFaceComponentName = it).watchFaceComponentName)
                .isEqualTo(it)
        }
        ComponentName(context, "service-name").let {
            assertThat(createIntent(watchFaceComponentName = it).watchFaceComponentName)
                .isEqualTo(it)
        }
    }

    @Test
    public fun createProviderChooserHelperIntent_complicationId() {
        assertThat(createIntent(complicationId = -1).complicationId).isEqualTo(-1)
        assertThat(createIntent(complicationId = 1234).complicationId).isEqualTo(1234)
        assertThat(createIntent(complicationId = 30000).complicationId).isEqualTo(30000)
    }

    @Test
    public fun createProviderChooserHelperIntent_supportedTypes() {
        arrayOf<ComplicationType>().let {
            assertThat(createIntent(supportedTypes = it).supportedTypes).isEqualTo(it)
        }
        arrayOf(LONG_TEXT).let {
            assertThat(createIntent(supportedTypes = it).supportedTypes).isEqualTo(it)
        }
        arrayOf(SHORT_TEXT, LONG_TEXT, MONOCHROMATIC_IMAGE).let {
            assertThat(createIntent(supportedTypes = it).supportedTypes).isEqualTo(it)
        }
    }

    @Test
    public fun instanceId() {
        assertThat(
            createIntent(instanceId = "ID-1")
                .getStringExtra(ProviderChooserIntent.EXTRA_WATCHFACE_INSTANCE_ID)
        ).isEqualTo("ID-1")
    }

    /** Creates an intent with default values for unspecified parameters. */
    private fun createIntent(
        watchFaceComponentName: ComponentName = defaultWatchFaceComponentName,
        complicationId: Int = defaultComplicationId,
        instanceId: String? = null,
        vararg supportedTypes: ComplicationType = defaultSupportedTypes
    ) = ComplicationHelperActivity.createProviderChooserHelperIntent(
        context,
        watchFaceComponentName,
        complicationId,
        supportedTypes.asList(),
        instanceId
    )

    private companion object {
        /** The context to be used in the various tests. */
        private val context = ApplicationProvider.getApplicationContext<Context>()

        /** The default watch face component name used in the test. */
        private val defaultWatchFaceComponentName = ComponentName("test.package", "test.class")

        /** The default complication ID used in the test. */
        private const val defaultComplicationId = 1234

        /** The default supported types used in the test. */
        private val defaultSupportedTypes = arrayOf(SHORT_TEXT, LONG_TEXT)
    }
}

/** The watch face component name encoded in the intent. */
private val Intent.watchFaceComponentName
    get() = getParcelableExtra<ComponentName>(ProviderChooserIntent.EXTRA_WATCH_FACE_COMPONENT_NAME)

/** The complication ID encoded in the intent. */
private val Intent.complicationId
    get() = getIntExtra(ProviderChooserIntent.EXTRA_COMPLICATION_ID, -1)

/** The support types encoded in the intent. */
private val Intent.supportedTypes
    get() = ComplicationType.fromWireTypes(
        getIntArrayExtra(ProviderChooserIntent.EXTRA_SUPPORTED_TYPES)!!
    )