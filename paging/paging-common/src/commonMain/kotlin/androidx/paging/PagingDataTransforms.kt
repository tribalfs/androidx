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

@file:JvmName("PagingDataTransforms")
@file:JvmMultifileClass

package androidx.paging

import androidx.annotation.CheckResult
import androidx.paging.TerminalSeparatorType.FULLY_COMPLETE
import kotlinx.coroutines.flow.map

internal inline fun <T : Any, R : Any> PagingData<T>.transform(
    crossinline transform: suspend (PageEvent<T>) -> PageEvent<R>
) = PagingData(
    flow = flow.map { transform(it) },
    uiReceiver = uiReceiver,
    hintReceiver = hintReceiver,
)

/**
 * Returns a [PagingData] containing the result of applying the given [transform] to each
 * element, as it is loaded.
 */
@CheckResult
@JvmSynthetic
public fun <T : Any, R : Any> PagingData<T>.map(
    transform: suspend (T) -> R
): PagingData<R> = transform { it.map(transform) }


/**
 * Returns a [PagingData] of all elements returned from applying the given [transform]
 * to each element, as it is loaded.
 */
@CheckResult
@JvmSynthetic
public fun <T : Any, R : Any> PagingData<T>.flatMap(
    transform: suspend (T) -> Iterable<R>
): PagingData<R> = transform { it.flatMap(transform) }

/**
 * Returns a [PagingData] containing only elements matching the given [predicate]
 */
@CheckResult
@JvmSynthetic
public fun <T : Any> PagingData<T>.filter(
    predicate: suspend (T) -> Boolean
): PagingData<T> = transform { it.filter(predicate) }

/**
 * Returns a [PagingData] containing each original element, with an optional separator
 * generated by [generator], given the elements before and after (or null, in boundary
 * conditions).
 *
 * Note that this transform is applied asynchronously, as pages are loaded. Potential
 * separators between pages are only computed once both pages are loaded.
 *
 * @param terminalSeparatorType [TerminalSeparatorType] used to configure when the header and
 * footer are added.
 *
 * @param generator Generator function used to construct a separator item given the item before
 * and the item after. For terminal separators (header and footer), the arguments passed to the
 * generator, `before` and `after`, will be `null` respectively. In cases where the fully paginated
 * list is empty, a single separator will be added where both `before` and `after` items are `null`.
 *
 * @sample androidx.paging.samples.insertSeparatorsSample
 * @sample androidx.paging.samples.insertSeparatorsUiModelSample
 */
@CheckResult
@JvmSynthetic
public fun <T : R, R : Any> PagingData<T>.insertSeparators(
    terminalSeparatorType: TerminalSeparatorType = FULLY_COMPLETE,
    generator: suspend (T?, T?) -> R?,
): PagingData<R> {
    // This function must be an extension method, as it indirectly imposes a constraint on
    // the type of T (because T extends R). Ideally it would be declared not be an
    // extension, to make this method discoverable for Java callers, but we need to support
    // the common UI model pattern for separators:
    //     class UiModel
    //     class ItemModel: UiModel
    //     class SeparatorModel: UiModel
    return PagingData(
        flow = flow.insertEventSeparators(terminalSeparatorType, generator),
        uiReceiver = uiReceiver,
        hintReceiver = hintReceiver
    )
}

/**
 * Returns a [PagingData] containing each original element, with the passed header [item] added
 * to the start of the list.
 *
 * The header [item] is added to a loaded page which marks the end of the data stream in the
 * [LoadType.PREPEND] direction by returning null in [PagingSource.LoadResult.Page.prevKey]. It
 * will be removed if the first page in the list is dropped, which can happen in the case of loaded
 * pages exceeding [PagingConfig.maxSize].
 *
 * Note: This operation is not idempotent, calling it multiple times will continually add
 * more headers to the start of the list, which can be useful if multiple header items are
 * required.
 *
 * @param terminalSeparatorType [TerminalSeparatorType] used to configure when the header and
 * footer are added.
 *
 * @param item The header to add to the front of the list once it is fully loaded in the
 * [LoadType.PREPEND] direction.
 *
 * @see [insertFooterItem]
 */
@CheckResult
@JvmOverloads
public fun <T : Any> PagingData<T>.insertHeaderItem(
    terminalSeparatorType: TerminalSeparatorType = FULLY_COMPLETE,
    item: T,
): PagingData<T> = insertSeparators(terminalSeparatorType) { before, _ ->
    if (before == null) item else null
}

/**
 * Returns a [PagingData] containing each original element, with the passed footer [item] added
 * to the end of the list.
 *
 * The footer [item] is added to a loaded page which marks the end of the data stream in the
 * [LoadType.APPEND] direction, either by returning null in [PagingSource.LoadResult.Page.nextKey].
 * It will be removed if the last page in the list is dropped, which can happen in the case of
 * loaded pages exceeding [PagingConfig.maxSize].
 *
 * Note: This operation is not idempotent, calling it multiple times will continually add
 * more footers to the end of the list, which can be useful if multiple footer items are
 * required.
 *
 * @param terminalSeparatorType [TerminalSeparatorType] used to configure when the header and
 * footer are added.
 *
 * @param item The footer to add to the end of the list once it is fully loaded in the
 * [LoadType.APPEND] direction.
 *
 * @see [insertHeaderItem]
 */
@CheckResult
@JvmOverloads
public fun <T : Any> PagingData<T>.insertFooterItem(
    terminalSeparatorType: TerminalSeparatorType = FULLY_COMPLETE,
    item: T,
): PagingData<T> = insertSeparators(terminalSeparatorType) { _, after ->
    if (after == null) item else null
}
