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

package androidx.compose.foundation.lazy

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalFoundationApi::class)
@MediumTest
@RunWith(AndroidJUnit4::class)
class LazyCustomKeysTest {

    @get:Rule
    val rule = createComposeRule()

    val itemSize = with(rule.density) {
        100.toDp()
    }

    @Test
    fun itemsWithKeysAreLaidOutCorrectly() {
        val list = listOf(MyClass(0), MyClass(1), MyClass(2))

        rule.setContent {
            LazyColumn {
                items(list, key = { it.id }) {
                    Item("${it.id}")
                }
            }
        }

        assertItems("0", "1", "2")
    }

    @Test
    fun removing_statesAreMoved() {
        var list by mutableStateOf(listOf(MyClass(0), MyClass(1), MyClass(2)))

        rule.setContent {
            LazyColumn {
                items(list, key = { it.id }) {
                    Item(remember { "${it.id}" })
                }
            }
        }

        rule.runOnIdle {
            list = listOf(list[0], list[2])
        }

        assertItems("0", "2")
    }

    @Test
    fun reordering_statesAreMoved_list() {
        testReordering { list ->
            items(list, key = { it.id }) {
                Item(remember { "${it.id}" })
            }
        }
    }

    @Test
    fun reordering_statesAreMoved_list_indexed() {
        testReordering { list ->
            itemsIndexed(list, key = { _, item -> item.id }) { _, item ->
                Item(remember { "${item.id}" })
            }
        }
    }

    @Test
    fun reordering_statesAreMoved_array() {
        testReordering { list ->
            val array = list.toTypedArray()
            items(array, key = { it.id }) {
                Item(remember { "${it.id}" })
            }
        }
    }

    @Test
    fun reordering_statesAreMoved_array_indexed() {
        testReordering { list ->
            val array = list.toTypedArray()
            itemsIndexed(array, key = { _, item -> item.id }) { _, item ->
                Item(remember { "${item.id}" })
            }
        }
    }

    @Test
    fun reordering_statesAreMoved_itemsWithCount() {
        testReordering { list ->
            items(list.size, key = { list[it].id }) {
                Item(remember { "${list[it].id}" })
            }
        }
    }

    @Test
    fun fullyReplacingTheList() {
        var list by mutableStateOf(listOf(MyClass(0), MyClass(1), MyClass(2)))
        var counter = 0

        rule.setContent {
            LazyColumn {
                items(list, key = { it.id }) {
                    Item(remember { counter++ }.toString())
                }
            }
        }

        rule.runOnIdle {
            list = listOf(MyClass(3), MyClass(4), MyClass(5), MyClass(6))
        }

        assertItems("3", "4", "5", "6")
    }

    @Test
    fun keepingOneItem() {
        var list by mutableStateOf(listOf(MyClass(0), MyClass(1), MyClass(2)))
        var counter = 0

        rule.setContent {
            LazyColumn {
                items(list, key = { it.id }) {
                    Item(remember { counter++ }.toString())
                }
            }
        }

        rule.runOnIdle {
            list = listOf(MyClass(1), MyClass(3))
        }

        assertItems("1", "3")
    }

    @Test
    fun mixingKeyedItemsAndNot() {
        testReordering { list ->
            item {
                Item("${list.first().id}")
            }
            items(list.subList(fromIndex = 1, toIndex = list.size), key = { it.id }) {
                Item(remember { "${it.id}" })
            }
        }
    }

    @Test
    fun reordering_usingMutableStateListOf() {
        val list = mutableStateListOf(MyClass(0), MyClass(1), MyClass(2))

        rule.setContent {
            LazyColumn {
                items(list, key = { it.id }) {
                    Item(remember { "${it.id}" })
                }
            }
        }

        rule.runOnIdle {
            list.add(list.removeAt(1))
        }

        assertItems("0", "2", "1")
    }

    @Test
    fun keysInLazyListItemInfoAreCorrect() {
        val list = listOf(MyClass(0), MyClass(1), MyClass(2))
        lateinit var state: LazyListState

        rule.setContent {
            state = rememberLazyListState()
            LazyColumn(state = state) {
                items(list, key = { it.id }) {
                    Item(remember { "${it.id}" })
                }
            }
        }

        rule.runOnIdle {
            assertThat(
                state.layoutInfo.visibleItemsInfo.map { it.key }
            ).isEqualTo(listOf(0, 1, 2))
        }
    }

    @Test
    fun keysInLazyListItemInfoAreCorrectAfterReordering() {
        var list by mutableStateOf(listOf(MyClass(0), MyClass(1), MyClass(2)))
        lateinit var state: LazyListState

        rule.setContent {
            state = rememberLazyListState()
            LazyColumn(state = state) {
                items(list, key = { it.id }) {
                    Item(remember { "${it.id}" })
                }
            }
        }

        rule.runOnIdle {
            list = listOf(list[0], list[2], list[1])
        }

        rule.runOnIdle {
            assertThat(
                state.layoutInfo.visibleItemsInfo.map { it.key }
            ).isEqualTo(listOf(0, 2, 1))
        }
    }

    private fun testReordering(content: LazyListScope.(List<MyClass>) -> Unit) {
        var list by mutableStateOf(listOf(MyClass(0), MyClass(1), MyClass(2)))

        rule.setContent {
            LazyColumn {
                content(list)
            }
        }

        rule.runOnIdle {
            list = listOf(list[0], list[2], list[1])
        }

        assertItems("0", "2", "1")
    }

    private fun assertItems(vararg tags: String) {
        var currentTop = 0.dp
        tags.forEach {
            rule.onNodeWithTag(it)
                .assertTopPositionInRootIsEqualTo(currentTop)
                .assertHeightIsEqualTo(itemSize)
            currentTop += itemSize
        }
    }

    @Composable
    private fun Item(tag: String) {
        Spacer(
            Modifier.testTag(tag).size(itemSize)
        )
    }

    private class MyClass(val id: Int)
}
