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

package androidx.window.embedding

import android.app.Activity
import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The unit tests for [ActivityEmbeddingController].
 */
class ActivityEmbeddingControllerTest {

    private lateinit var mockEmbeddingBackend: EmbeddingBackend
    private lateinit var mockContext: Context
    private lateinit var mockActivity: Activity
    private lateinit var activityEmbeddingController: ActivityEmbeddingController

    @Before
    fun setUp() {
        mockEmbeddingBackend = mock()
        activityEmbeddingController = ActivityEmbeddingController(mockEmbeddingBackend)

        mockContext = mock()
        mockActivity = mock()
        whenever(mockActivity.applicationContext).doReturn(mockContext)
    }

    @Test
    fun testIsActivityEmbedded() {
        whenever(mockEmbeddingBackend.isActivityEmbedded(mockActivity)).thenReturn(true)

        assertTrue(activityEmbeddingController.isActivityEmbedded(mockActivity))

        whenever(mockEmbeddingBackend.isActivityEmbedded(mockActivity)).thenReturn(false)

        assertFalse(activityEmbeddingController.isActivityEmbedded(mockActivity))
    }
}