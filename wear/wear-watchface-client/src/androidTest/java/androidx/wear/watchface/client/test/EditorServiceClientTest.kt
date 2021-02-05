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

package androidx.wear.watchface.client.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.wear.watchface.client.EditorObserverCallback
import androidx.wear.watchface.client.EditorServiceClientImpl
import androidx.wear.watchface.client.EditorState
import androidx.wear.watchface.editor.EditorService
import androidx.wear.watchface.editor.data.EditorStateWireFormat
import androidx.wear.watchface.style.data.UserStyleWireFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class EditorServiceClientTest {
    private val editorServiceClient = EditorServiceClientImpl(EditorService.globalEditorService)

    @Test
    fun registerObserver() {
        lateinit var observedEditorState: EditorState
        val observer = object : EditorObserverCallback {
            override fun onEditorStateChange(editorState: EditorState) {
                observedEditorState = editorState
            }
        }
        editorServiceClient.registerObserver(observer)

        val watchFaceInstanceId = "id-1"
        EditorService.globalEditorService.broadcastEditorState(
            EditorStateWireFormat(
                watchFaceInstanceId,
                UserStyleWireFormat(mapOf("color" to "red", "size" to "small")),
                emptyList(),
                true
            )
        )

        editorServiceClient.unregisterObserver(observer)

        assertThat(observedEditorState.watchFaceInstanceId).isEqualTo(watchFaceInstanceId)
        assertThat(observedEditorState.userStyle.toString()).isEqualTo("{color=red, size=small}")
        assertTrue(observedEditorState.commitChanges)
    }
}