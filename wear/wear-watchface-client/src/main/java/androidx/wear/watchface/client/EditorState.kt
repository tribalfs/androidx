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

package androidx.wear.watchface.client

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.wear.complications.data.ComplicationData
import androidx.wear.complications.data.asApiComplicationData
import androidx.wear.watchface.editor.data.EditorStateWireFormat
import androidx.wear.watchface.style.UserStyle

/**
 * The system is responsible for the management and generation of these ids and they have no
 * context outside of an instance of an EditorState and should not be stored or saved for later
 * use by the WatchFace provider.
 *
 * @param id The system's id for a watch face being edited. This is passed in from
 *     [androidx.wear.watchface.EditorRequest.watchFaceId].
 */
public class WatchFaceId(public val id: String)

/**
 * The state of the editing session. See [androidx.wear.watchface.editor.EditorSession].
 *
 * @param watchFaceId Unique ID for the instance of the watch face being edited (see
 *     [androidx.wear.watchface.editor.EditorRequest.watchFaceId]), only defined for
 *     Android R and beyond.
 * @param userStyle The current [UserStyle] encoded as a Map<String, String>.
 * @param previewComplicationData Preview [ComplicationData] needed for taking screenshots without
 *     live complication data.
 * @param commitChanges Whether or not this state should be committed (i.e. the user aborted the
 *     session). If it's not committed then any changes (E.g. complication provider changes)
 *     should be abandoned. There's no need to resend the style to the watchface because the
 *     library will have restored the previous style.
 */
public class EditorState internal constructor(
    @RequiresApi(Build.VERSION_CODES.R)
    public val watchFaceId: WatchFaceId,
    public val userStyle: Map<String, String>,
    public val previewComplicationData: Map<Int, ComplicationData>,
    @get:JvmName("hasCommitChanges")
    public val commitChanges: Boolean
) {
    override fun toString(): String =
        "{watchFaceId: ${watchFaceId.id}, userStyle: $userStyle" +
            ", previewComplicationData: [" +
            previewComplicationData.map { "${it.key} -> ${it.value}" }.joinToString() +
            "], commitChanges: $commitChanges}"
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun EditorStateWireFormat.asApiEditorState(): EditorState {
    return EditorState(
        WatchFaceId(watchFaceInstanceId ?: ""),
        userStyle.mUserStyle,
        previewComplicationData.associateBy(
            { it.id },
            { it.complicationData.asApiComplicationData() }
        ),
        commitChanges
    )
}