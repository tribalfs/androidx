/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.ui.tooling

import androidx.compose.Composable
import androidx.compose.InternalComposeApi
import androidx.compose.Providers
import androidx.compose.SlotTable
import androidx.compose.currentComposer
import java.util.Collections
import java.util.WeakHashMap

/**
 * Storage for the preview generated [SlotTable]s.
 */
internal interface SlotTableRecord {
    val store: Set<SlotTable>

    companion object {
        fun create(): SlotTableRecord = SlotTableRecordImpl()
    }
}

private class SlotTableRecordImpl : SlotTableRecord {
    override val store: MutableSet<SlotTable> =
        Collections.newSetFromMap(WeakHashMap<SlotTable, Boolean>())
}

/**
 * A wrapper for compositions in inspection mode. The composition inside the Inspectable component
 * is in inspection mode.
 *
 * @param slotTableRecord [SlotTableRecord] to record the SlotTable used in the composition of [children]
 *
 * @suppress
 */
@Composable
internal fun Inspectable(
    slotTableRecord: SlotTableRecord,
    children: @Composable () -> Unit
) {
    @OptIn(InternalComposeApi::class)
    currentComposer.collectKeySourceInformation()
    (slotTableRecord as SlotTableRecordImpl).store.add(currentComposer.slotTable)
    Providers(InspectionMode provides true, children = children)
}

/**
 * A wrapper for inspection-mode-only behavior. The children of this component will only be included
 * in the composition when the composition is in inspection mode.
 */
@Composable
fun InInspectionModeOnly(children: @Composable () -> Unit) {
    if (InspectionMode.current) {
        children()
    }
}
