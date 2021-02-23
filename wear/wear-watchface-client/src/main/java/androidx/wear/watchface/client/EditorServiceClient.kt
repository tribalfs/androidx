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

import androidx.annotation.RestrictTo
import androidx.wear.watchface.editor.IEditorObserver
import androidx.wear.watchface.editor.IEditorService
import androidx.wear.watchface.editor.data.EditorStateWireFormat
import java.util.concurrent.Executor

/** Client for the watchface editor service. */
public interface EditorServiceClient {
    /**
     * Starts listening to [androidx.wear.watchface.editor.EditorSession] events, with the callback
     * executed by an immediate executor on an undefined thread.
     */
    public fun registerObserver(
        editorObserverCallback: EditorObserverCallback
    )

    /**
     * Starts listening to [androidx.wear.watchface.editor.EditorSession] events with the callback
     * run on the specified [Executor] by an immediate executor, on an undefined thread if `null`.
     */
    public fun registerObserver(
        observerCallbackExecutor: Executor? = null,
        editorObserverCallback: EditorObserverCallback
    )

    /** Unregisters an [EditorObserverCallback] previously registered via [registerObserver].  */
    public fun unregisterObserver(editorObserverCallback: EditorObserverCallback)

    /** Instructs any open editor to close. */
    public fun closeEditor()
}

/** Observes state changes in [androidx.wear.watchface.editor.EditorSession]. */
public interface EditorObserverCallback {
    /** Called in response to [androidx.wear.watchface.editor.EditorSession.close] .*/
    public fun onEditorStateChange(editorState: EditorState)
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class EditorServiceClientImpl(
    private val iEditorService: IEditorService
) : EditorServiceClient {
    private val lock = Any()
    private val editorMap = HashMap<EditorObserverCallback, Int>()

    override fun registerObserver(editorObserverCallback: EditorObserverCallback) {
        registerObserver(null, editorObserverCallback)
    }

    override fun registerObserver(
        observerCallbackExecutor: Executor?,
        editorObserverCallback: EditorObserverCallback
    ) {
        val executor = observerCallbackExecutor ?: object : Executor {
            override fun execute(runnable: Runnable) {
                runnable.run()
            }
        }

        val observer = object : IEditorObserver.Stub() {
            override fun getApiVersion() = IEditorObserver.API_VERSION

            override fun onEditorStateChange(editorStateWireFormat: EditorStateWireFormat) {
                executor.execute {
                    editorObserverCallback.onEditorStateChange(
                        editorStateWireFormat.asApiEditorState()
                    )
                }
            }
        }

        synchronized(lock) {
            editorMap[editorObserverCallback] = iEditorService.registerObserver(observer)
        }
    }

    override fun unregisterObserver(editorObserverCallback: EditorObserverCallback) {
        synchronized(lock) {
            editorMap[editorObserverCallback]?.let {
                iEditorService.unregisterObserver(it)
                editorMap.remove(editorObserverCallback)
            }
        }
    }

    override fun closeEditor() {
        iEditorService.closeEditor()
    }
}
