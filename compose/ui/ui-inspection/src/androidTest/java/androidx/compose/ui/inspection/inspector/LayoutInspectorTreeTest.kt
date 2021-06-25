/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.ui.inspection.inspector

import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalDrawer
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.LocalInspectionTables
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.inspection.testdata.TestActivity
import androidx.compose.ui.layout.GraphicLayerInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.data.Group
import androidx.compose.ui.tooling.data.UiToolingDataApi
import androidx.compose.ui.tooling.data.asTree
import androidx.compose.ui.tooling.data.position
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

private const val DEBUG = true
private const val ROOT_ID = 3L
private const val MAX_RECURSIONS = 2
private const val MAX_ITERABLE_SIZE = 5

@LargeTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29) // Render id is not returned for api < 29
@OptIn(UiToolingDataApi::class)
class LayoutInspectorTreeTest {
    private lateinit var density: Density

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    @Before
    fun before() {
        composeTestRule.activityRule.scenario.onActivity {
            density = Density(it)
        }
        isDebugInspectorInfoEnabled = true
    }

    private fun findAndroidComposeView(): View {
        return findAllAndroidComposeViews().single()
    }

    private fun findAllAndroidComposeViews(): List<View> {
        val composeViews = mutableListOf<View>()
        WindowInspector.getGlobalWindowViews().forEach {
            collectAllAndroidComposeView(it.rootView, composeViews)
        }
        return composeViews
    }

    private fun collectAllAndroidComposeView(view: View, composeViews: MutableList<View>) {
        if (view.javaClass.simpleName == "AndroidComposeView") {
            composeViews.add(view)
        }
        if (view !is ViewGroup) {
            return
        }
        for (i in 0 until view.childCount) {
            collectAllAndroidComposeView(view.getChildAt(i), composeViews)
        }
    }

    @After
    fun after() {
        isDebugInspectorInfoEnabled = false
    }

    @Test
    fun buildTree() {
        val slotTableRecord = CompositionDataRecord.create()

        show {
            Inspectable(slotTableRecord) {
                Column {
                    Text(text = "Hello World", color = Color.Green)
                    Icon(Icons.Filled.FavoriteBorder, null)
                    Surface {
                        Button(onClick = {}) { Text(text = "OK") }
                    }
                }
            }
        }

        // TODO: Find out if we can set "settings put global debug_view_attributes 1" in tests
        val view = findAndroidComposeView()
        view.setTag(R.id.inspection_slot_table_set, slotTableRecord.store)
        val builder = LayoutInspectorTree()
        val nodes = builder.convert(view)
        dumpNodes(nodes, view, builder)

        validate(nodes, builder) {
            node(
                name = "Column",
                fileName = "LayoutInspectorTreeTest.kt",
                left = 0.0.dp, top = 0.0.dp, width = 72.0.dp, height = 78.9.dp,
                children = listOf("Text", "Icon", "Surface")
            )
            node(
                name = "Text",
                isRenderNode = true,
                fileName = "LayoutInspectorTreeTest.kt",
                left = 0.0.dp, top = 0.0.dp, width = 72.0.dp, height = 18.9.dp,
            )
            node(
                name = "Icon",
                isRenderNode = true,
                fileName = "LayoutInspectorTreeTest.kt",
                left = 0.0.dp, top = 18.9.dp, width = 24.0.dp, height = 24.0.dp,
            )
            node(
                name = "Surface",
                fileName = "LayoutInspectorTreeTest.kt",
                isRenderNode = true,
                left = 0.0.dp, top = 42.9.dp, width = 64.0.dp, height = 36.0.dp,
                children = listOf("Button")
            )
            node(
                name = "Button",
                fileName = "LayoutInspectorTreeTest.kt",
                isRenderNode = true,
                left = 0.0.dp, top = 42.9.dp, width = 64.0.dp, height = 36.0.dp,
                children = listOf("Text")
            )
            node(
                name = "Text",
                isRenderNode = true,
                fileName = "LayoutInspectorTreeTest.kt",
                left = 21.7.dp, top = 51.5.dp, width = 20.9.dp, height = 18.9.dp,
            )
        }
    }

    @Test
    fun buildTreeWithTransformedText() {
        val slotTableRecord = CompositionDataRecord.create()

        show {
            Inspectable(slotTableRecord) {
                MaterialTheme {
                    Text(
                        text = "Hello World",
                        modifier = Modifier.graphicsLayer(rotationZ = 225f)
                    )
                }
            }
        }

        // TODO: Find out if we can set "settings put global debug_view_attributes 1" in tests
        val view = findAndroidComposeView()
        view.setTag(R.id.inspection_slot_table_set, slotTableRecord.store)
        val builder = LayoutInspectorTree()
        val nodes = builder.convert(view)
        dumpNodes(nodes, view, builder)

        validate(nodes, builder) {
            node(
                name = "MaterialTheme",
                hasTransformations = true,
                fileName = "LayoutInspectorTreeTest.kt",
                left = 68.0.dp, top = 49.8.dp, width = 88.6.dp, height = 21.7.dp,
                children = listOf("Text")
            )
            node(
                name = "Text",
                isRenderNode = true,
                hasTransformations = true,
                fileName = "LayoutInspectorTreeTest.kt",
                left = 68.0.dp, top = 49.8.dp, width = 88.6.dp, height = 21.7.dp,
            )
        }
    }

    @Test
    fun testStitchTreeFromModelDrawerLayout() {
        val slotTableRecord = CompositionDataRecord.create()

        show {
            Inspectable(slotTableRecord) {
                ModalDrawer(
                    drawerContent = { Text("Something") },
                    content = {
                        Column {
                            Text(text = "Hello World", color = Color.Green)
                            Button(onClick = {}) { Text(text = "OK") }
                        }
                    }
                )
            }
        }
        val view = findAndroidComposeView()
        view.setTag(R.id.inspection_slot_table_set, slotTableRecord.store)
        dumpSlotTableSet(slotTableRecord)
        val builder = LayoutInspectorTree()
        val nodes = builder.convert(view)
        dumpNodes(nodes, view, builder)

        if (DEBUG) {
            validate(nodes, builder) {
                node("Box", children = listOf("ModalDrawer"))
                node("ModalDrawer", children = listOf("Column", "Text"))
                node("Column", children = listOf("Text", "Button"))
                node("Text")
                node("Button", children = listOf("Text"))
                node("Text")
                node("Text")
            }
        }
        assertThat(nodes.size).isEqualTo(1)
    }

    @Test
    fun testStitchTreeFromModelDrawerLayoutWithSystemNodes() {
        val slotTableRecord = CompositionDataRecord.create()

        show {
            Inspectable(slotTableRecord) {
                ModalDrawer(
                    drawerContent = { Text("Something") },
                    content = {
                        Column {
                            Text(text = "Hello World", color = Color.Green)
                            Button(onClick = {}) { Text(text = "OK") }
                        }
                    }
                )
            }
        }
        val view = findAndroidComposeView()
        view.setTag(R.id.inspection_slot_table_set, slotTableRecord.store)
        dumpSlotTableSet(slotTableRecord)
        val builder = LayoutInspectorTree()
        builder.hideSystemNodes = false
        val nodes = builder.convert(view)
        dumpNodes(nodes, view, builder)

        if (DEBUG) {
            validate(nodes, builder) {
                node("Box", children = listOf("ModalDrawer"))
                node("ModalDrawer", children = listOf("WithConstraints"))
                node("WithConstraints", children = listOf("SubcomposeLayout"))
                node("SubcomposeLayout", children = listOf("Box"))
                node("Box", children = listOf("Box", "Canvas", "Surface"))
                node("Box", children = listOf("Column"))
                node("Column", children = listOf("Text", "Button"))
                node("Text", children = listOf("Text"))
                node("Text", children = listOf("CoreText"))
                node("CoreText", children = listOf())
                node("Button", children = listOf("Surface"))
                node("Surface", children = listOf("ProvideTextStyle"))
                node("ProvideTextStyle", children = listOf("Row"))
                node("Row", children = listOf("Text"))
                node("Text", children = listOf("Text"))
                node("Text", children = listOf("CoreText"))
                node("CoreText", children = listOf())
                node("Canvas", children = listOf("Spacer"))
                node("Spacer", children = listOf())
                node("Surface", children = listOf("Column"))
                node("Column", children = listOf("Text"))
                node("Text", children = listOf("Text"))
                node("Text", children = listOf("CoreText"))
                node("CoreText", children = listOf())
            }
        }
        assertThat(nodes.size).isEqualTo(1)
    }

    @Test
    fun testSpacer() {
        val slotTableRecord = CompositionDataRecord.create()

        show {
            Inspectable(slotTableRecord) {
                Column {
                    Text(text = "Hello World", color = Color.Green)
                    Spacer(Modifier.height(16.dp))
                    Image(Icons.Filled.Call, null)
                }
            }
        }

        val view = findAndroidComposeView()
        view.setTag(R.id.inspection_slot_table_set, slotTableRecord.store)
        val builder = LayoutInspectorTree()
        val node = builder.convert(view)
            .flatMap { flatten(it) }
            .firstOrNull { it.name == "Spacer" }

        // Spacer should show up in the Compose tree:
        assertThat(node).isNotNull()
    }

    @Test // regression test b/174855322
    fun testBasicText() {
        val slotTableRecord = CompositionDataRecord.create()

        show {
            Inspectable(slotTableRecord) {
                Column {
                    BasicText(
                        text = "Some text",
                        style = TextStyle(textDecoration = TextDecoration.Underline)
                    )
                }
            }
        }

        val view = findAndroidComposeView()
        view.setTag(R.id.inspection_slot_table_set, slotTableRecord.store)
        val builder = LayoutInspectorTree()
        val node = builder.convert(view)
            .flatMap { flatten(it) }
            .firstOrNull { it.name == "BasicText" }

        assertThat(node).isNotNull()

        assertThat(node?.parameters).isNotEmpty()
    }

    @Test
    fun testTextId() {
        val slotTableRecord = CompositionDataRecord.create()

        show {
            Inspectable(slotTableRecord) {
                Text(text = "Hello World")
            }
        }

        val view = findAndroidComposeView()
        view.setTag(R.id.inspection_slot_table_set, slotTableRecord.store)
        val builder = LayoutInspectorTree()
        val node = builder.convert(view)
            .flatMap { flatten(it) }
            .firstOrNull { it.name == "Text" }

        // LayoutNode id should be captured by the Text node:
        assertThat(node?.id).isGreaterThan(0)
    }

    @Test
    fun testSemantics() {
        val slotTableRecord = CompositionDataRecord.create()

        show {
            Inspectable(slotTableRecord) {
                Column {
                    Text(text = "Studio")
                    Row(modifier = Modifier.semantics(true) {}) {
                        Text(text = "Hello")
                        Text(text = "World")
                    }
                    Row(modifier = Modifier.clearAndSetSemantics { text = AnnotatedString("to") }) {
                        Text(text = "Hello")
                        Text(text = "World")
                    }
                }
            }
        }

        val androidComposeView = findAndroidComposeView()
        androidComposeView.setTag(R.id.inspection_slot_table_set, slotTableRecord.store)
        val builder = LayoutInspectorTree()
        val nodes = builder.convert(androidComposeView)
        validate(nodes, builder, checkSemantics = true) {
            node("Column", children = listOf("Text", "Row", "Row"))
            node(
                name = "Text",
                isRenderNode = true,
                mergedSemantics = "[Studio]",
                unmergedSemantics = "[Studio]"
            )
            node(
                name = "Row",
                children = listOf("Text", "Text"),
                mergedSemantics = "[Hello, World]"
            )
            node("Text", isRenderNode = true, unmergedSemantics = "[Hello]")
            node("Text", isRenderNode = true, unmergedSemantics = "[World]")
            node(
                name = "Row",
                children = listOf("Text", "Text"),
                mergedSemantics = "[to]",
                unmergedSemantics = "[to]"
            )
            node("Text", isRenderNode = true, unmergedSemantics = "[Hello]")
            node("Text", isRenderNode = true, unmergedSemantics = "[World]")
        }
    }

    @Test
    fun testDialog() {
        val slotTableRecord = CompositionDataRecord.create()

        show {
            Inspectable(slotTableRecord) {
                Column {
                    Text("Hello World!")
                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = {
                            Button({}) {
                                Text("This is the Confirm Button")
                            }
                        }
                    )
                }
            }
        }
        val composeViews = findAllAndroidComposeViews()
        val appView = composeViews[0]
        val dialogView = composeViews[1]
        appView.setTag(R.id.inspection_slot_table_set, slotTableRecord.store)
        dialogView.setTag(R.id.inspection_slot_table_set, slotTableRecord.store)

        val builder = LayoutInspectorTree()

        val appNodes = builder.convert(appView)
        dumpNodes(appNodes, appView, builder)

        // Verify that the main app does not contain the Popup
        validate(appNodes, builder) {
            node(
                name = "Column",
                fileName = "LayoutInspectorTreeTest.kt",
                children = listOf("Text")
            )
            node(
                name = "Text",
                isRenderNode = true,
                fileName = "LayoutInspectorTreeTest.kt",
            )
        }

        val dialogContentNodes = builder.convert(dialogView)
        val dialogNodes = builder.addSubCompositionRoots(dialogView, dialogContentNodes)
        dumpNodes(dialogNodes, dialogView, builder)

        // Verify that the AlertDialog is captured with content
        validate(dialogNodes, builder, ignoreElementsFromShow = false) {
            node(
                name = "AlertDialog",
                fileName = "LayoutInspectorTreeTest.kt",
                children = listOf("Button")
            )
            node(
                name = "Button",
                fileName = "LayoutInspectorTreeTest.kt",
                isRenderNode = true,
                children = listOf("Text")
            )
            node(
                name = "Text",
                isRenderNode = true,
                fileName = "LayoutInspectorTreeTest.kt",
            )
        }
    }

    @Test
    fun testPopup() {
        val slotTableRecord = CompositionDataRecord.create()

        show {
            Inspectable(slotTableRecord) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text("Compose Text")
                    Popup(alignment = Alignment.Center) {
                        Text("This is a popup")
                    }
                }
            }
        }
        val composeViews = findAllAndroidComposeViews()
        val appView = composeViews[0]
        val popupView = composeViews[1]
        appView.setTag(R.id.inspection_slot_table_set, slotTableRecord.store)
        popupView.setTag(R.id.inspection_slot_table_set, slotTableRecord.store)
        val builder = LayoutInspectorTree()

        val appNodes = builder.convert(appView)
        dumpNodes(appNodes, appView, builder)

        // Verify that the main app does not contain the Popup
        validate(appNodes, builder) {
            node(
                name = "Column",
                fileName = "LayoutInspectorTreeTest.kt",
                children = listOf("Text")
            )
            node(
                name = "Text",
                isRenderNode = true,
                fileName = "LayoutInspectorTreeTest.kt",
            )
        }

        val popupContentNodes = builder.convert(popupView)
        val popupNodes = builder.addSubCompositionRoots(popupView, popupContentNodes)
        dumpNodes(popupNodes, popupView, builder)

        // Verify that the Popup is captured with content
        validate(popupNodes, builder, ignoreElementsFromShow = false) {
            node(
                name = "Popup",
                fileName = "LayoutInspectorTreeTest.kt",
                children = listOf("Text")
            )
            node(
                name = "Text",
                isRenderNode = true,
                fileName = "LayoutInspectorTreeTest.kt",
            )
        }
    }

    @Test
    fun testAndroidView() {
        val slotTableRecord = CompositionDataRecord.create()

        show {
            Inspectable(slotTableRecord) {
                Column {
                    Text("Compose Text")
                    AndroidView({ context ->
                        TextView(context).apply {
                            text = "AndroidView"
                        }
                    })
                }
            }
        }
        val composeView = findAndroidComposeView() as ViewGroup
        composeView.setTag(R.id.inspection_slot_table_set, slotTableRecord.store)
        val builder = LayoutInspectorTree()
        builder.hideSystemNodes = false
        val nodes = builder.convert(composeView)
        dumpNodes(nodes, composeView, builder)
        val androidView = nodes.flatMap { flatten(it) }.single { it.name == "AndroidView" }

        validate(listOf(androidView), builder, ignoreElementsFromShow = false) {
            node(
                name = "AndroidView",
                fileName = "LayoutInspectorTreeTest.kt",
                children = listOf("ComposeNode")
            )
            node(
                name = "ComposeNode",
                fileName = "AndroidView.android.kt",
                hasViewIdUnder = composeView,
            )
        }
    }

    // WARNING: The formatting of the lines below here affect test results.
    val titleLine = Throwable().stackTrace[0].lineNumber + 3

    @Composable
    private fun Title() {
        val maxOffset = with(LocalDensity.current) { 80.dp.toPx() }
        val minOffset = with(LocalDensity.current) { 80.dp.toPx() }
        val offset = maxOffset.coerceAtLeast(minOffset)
        Column(
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .heightIn(min = 128.dp)
                .graphicsLayer { translationY = offset }
                .background(color = MaterialTheme.colors.background)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Snack",
                style = MaterialTheme.typography.h4,
                color = MaterialTheme.colors.secondary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = "Tagline",
                style = MaterialTheme.typography.subtitle2,
                fontSize = 20.sp,
                color = MaterialTheme.colors.secondary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$2.95",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
    }
    // WARNING: End formatted section

    @Test
    fun testLineNumbers() {
        // WARNING: The formatting of the lines below here affect test results.
        val testLine = Throwable().stackTrace[0].lineNumber
        val slotTableRecord = CompositionDataRecord.create()

        show {
            Inspectable(slotTableRecord) {
                Column {
                    Title()
                }
            }
        }
        // WARNING: End formatted section

        val androidComposeView = findAndroidComposeView()
        androidComposeView.setTag(R.id.inspection_slot_table_set, slotTableRecord.store)
        val builder = LayoutInspectorTree()
        val nodes = builder.convert(androidComposeView)
        dumpNodes(nodes, androidComposeView, builder)

        validate(nodes, builder, checkLineNumbers = true, checkRenderNodes = false) {
            node("Column", lineNumber = testLine + 5, children = listOf("Title"))
            node("Title", lineNumber = testLine + 6, children = listOf("Column"))
            node(
                name = "Column",
                lineNumber = titleLine + 4,
                children = listOf("Spacer", "Text", "Text", "Spacer", "Text", "Spacer")
            )
            node("Spacer", lineNumber = titleLine + 11)
            node("Text", lineNumber = titleLine + 12)
            node("Text", lineNumber = titleLine + 18)
            node("Spacer", lineNumber = titleLine + 25)
            node("Text", lineNumber = titleLine + 26)
            node("Spacer", lineNumber = titleLine + 32)
        }
    }

    @Composable
    fun First() {
        Text("First")
    }

    @Composable
    fun Second() {
        Text("Second")
    }

    @Test
    fun testCrossfade() {
        val slotTableRecord = CompositionDataRecord.create()

        show {
            Inspectable(slotTableRecord) {
                val showFirst by remember { mutableStateOf(true) }
                Crossfade(showFirst) {
                    when (it) {
                        true -> First()
                        false -> Second()
                    }
                }
            }
        }
        val androidComposeView = findAndroidComposeView()
        androidComposeView.setTag(R.id.inspection_slot_table_set, slotTableRecord.store)
        val builder = LayoutInspectorTree()
        builder.hideSystemNodes = false
        val first = builder.convert(androidComposeView)
            .flatMap { flatten(it) }
            .first { it.name == "First" }
        val hash = packageNameHash(this.javaClass.name.substringBeforeLast('.'))
        assertThat(first.fileName).isEqualTo("LayoutInspectorTreeTest.kt")
        assertThat(first.packageHash).isEqualTo(hash)
    }

    @Composable
    fun InlineParameters(size: Dp, fontSize: TextUnit) {
        Text("$size $fontSize")
    }

    @Test
    fun testInlineParameterTypes() {
        val slotTableRecord = CompositionDataRecord.create()

        show {
            Inspectable(slotTableRecord) {
                InlineParameters(20.5.dp, 30.sp)
            }
        }
        val androidComposeView = findAndroidComposeView()
        androidComposeView.setTag(R.id.inspection_slot_table_set, slotTableRecord.store)
        val builder = LayoutInspectorTree()
        builder.hideSystemNodes = false
        val inlineParameters = builder.convert(androidComposeView)
            .flatMap { flatten(it) }
            .first { it.name == "InlineParameters" }
        assertThat(inlineParameters.parameters[0].name).isEqualTo("size")
        assertThat(inlineParameters.parameters[0].value?.javaClass).isEqualTo(Dp::class.java)
        assertThat(inlineParameters.parameters[1].name).isEqualTo("fontSize")
        assertThat(inlineParameters.parameters[1].value?.javaClass).isEqualTo(TextUnit::class.java)
    }

    @Suppress("SameParameterValue")
    private fun validate(
        result: List<InspectorNode>,
        builder: LayoutInspectorTree,
        checkParameters: Boolean = false,
        checkSemantics: Boolean = false,
        checkLineNumbers: Boolean = false,
        checkRenderNodes: Boolean = true,
        ignoreElementsFromShow: Boolean = true,
        block: TreeValidationReceiver.() -> Unit = {}
    ) {
        if (DEBUG) {
            return
        }
        val nodes = result.flatMap { flatten(it) }.listIterator()
        if (ignoreElementsFromShow) {
            ignoreStart(nodes, "Box")
        }
        val tree = TreeValidationReceiver(
            nodes,
            density,
            checkParameters,
            checkSemantics,
            checkLineNumbers,
            checkRenderNodes,
            builder
        )
        tree.block()
    }

    private fun ignoreStart(nodes: ListIterator<InspectorNode>, vararg names: String) {
        for (name in names) {
            assertThat(nodes.next().name).isEqualTo(name)
        }
    }

    private class TreeValidationReceiver(
        val nodeIterator: Iterator<InspectorNode>,
        val density: Density,
        val checkParameters: Boolean,
        val checkSemantics: Boolean,
        val checkLineNumbers: Boolean,
        val checkRenderNodes: Boolean,
        val builder: LayoutInspectorTree
    ) {
        fun node(
            name: String,
            fileName: String? = null,
            lineNumber: Int = -1,
            isRenderNode: Boolean = false,
            hasViewIdUnder: View? = null,
            hasTransformations: Boolean = false,
            mergedSemantics: String = "",
            unmergedSemantics: String = "",
            left: Dp = Dp.Unspecified,
            top: Dp = Dp.Unspecified,
            width: Dp = Dp.Unspecified,
            height: Dp = Dp.Unspecified,
            children: List<String> = listOf(),
            block: ParameterValidationReceiver.() -> Unit = {}
        ) {
            assertWithMessage("No such node found: $name").that(nodeIterator.hasNext()).isTrue()
            val node = nodeIterator.next()
            assertThat(node.name).isEqualTo(name)
            val message = "Node: $name"
            assertWithMessage(message).that(node.children.map { it.name })
                .containsExactlyElementsIn(children).inOrder()
            fileName?.let { assertWithMessage(message).that(node.fileName).isEqualTo(fileName) }
            if (lineNumber != -1) {
                assertWithMessage(message).that(node.lineNumber).isEqualTo(lineNumber)
            }
            if (checkRenderNodes) {
                if (isRenderNode) {
                    assertWithMessage(message).that(node.id).isGreaterThan(0L)
                } else {
                    assertWithMessage(message).that(node.id).isLessThan(0L)
                }
            }
            if (hasViewIdUnder != null) {
                assertWithMessage(message).that(node.viewId).isGreaterThan(0L)
                assertWithMessage(message).that(hasViewIdUnder.hasChild(node.viewId)).isTrue()
            } else {
                assertWithMessage(message).that(node.viewId).isEqualTo(0L)
            }
            if (hasTransformations) {
                assertWithMessage(message).that(node.bounds).isNotNull()
            } else {
                assertWithMessage(message).that(node.bounds).isNull()
            }
            if (left != Dp.Unspecified) {
                with(density) {
                    assertWithMessage(message).that(node.left.toDp().value)
                        .isWithin(2.0f).of(left.value)
                    assertWithMessage(message).that(node.top.toDp().value)
                        .isWithin(2.0f).of(top.value)
                    assertWithMessage(message).that(node.width.toDp().value)
                        .isWithin(2.0f).of(width.value)
                    assertWithMessage(message).that(node.height.toDp().value)
                        .isWithin(2.0f).of(height.value)
                }
            }

            if (checkSemantics) {
                val merged = node.mergedSemantics.singleOrNull { it.name == "Text" }?.value
                assertWithMessage(message).that(merged?.toString() ?: "").isEqualTo(mergedSemantics)
                val unmerged = node.unmergedSemantics.singleOrNull { it.name == "Text" }?.value
                assertWithMessage(message).that(unmerged?.toString() ?: "")
                    .isEqualTo(unmergedSemantics)
            }

            if (checkLineNumbers) {
                assertThat(node.lineNumber).isEqualTo(lineNumber)
            }

            if (checkParameters) {
                val params = builder.convertParameters(
                    ROOT_ID, node, ParameterKind.Normal, MAX_RECURSIONS, MAX_ITERABLE_SIZE
                )
                val receiver = ParameterValidationReceiver(params.listIterator())
                receiver.block()
                receiver.checkFinished(name)
            }
        }

        private fun View.hasChild(id: Long): Boolean {
            if (this !is ViewGroup) {
                return false
            }
            for (index in 0..childCount) {
                if (getChildAt(index).uniqueDrawingId == id) {
                    return true
                }
            }
            return false
        }
    }

    private fun flatten(node: InspectorNode): List<InspectorNode> =
        listOf(node).plus(node.children.flatMap { flatten(it) })

    fun show(composable: @Composable () -> Unit) = composeTestRule.setContent(composable)

    // region DEBUG print methods
    private fun dumpNodes(nodes: List<InspectorNode>, view: View, builder: LayoutInspectorTree) {
        @Suppress("ConstantConditionIf")
        if (!DEBUG) {
            return
        }
        println()
        println("=================== Nodes ==========================")
        nodes.forEach { dumpNode(it, indent = 0) }
        println()
        println("=================== validate statements ==========================")
        nodes.forEach { generateValidate(it, view, builder) }
    }

    private fun dumpNode(node: InspectorNode, indent: Int) {
        println(
            "\"${"  ".repeat(indent * 2)}\", \"${node.name}\", \"${node.fileName}\", " +
                "${node.lineNumber}, ${node.left}, ${node.top}, " +
                "${node.width}, ${node.height}"
        )
        node.children.forEach { dumpNode(it, indent + 1) }
    }

    private fun generateValidate(
        node: InspectorNode,
        view: View,
        builder: LayoutInspectorTree,
        generateParameters: Boolean = false
    ) {
        with(density) {
            val left = round(node.left.toDp())
            val top = round(node.top.toDp())
            val width = if (node.width == view.width) "viewWidth" else round(node.width.toDp())
            val height = if (node.height == view.height) "viewHeight" else round(node.height.toDp())

            print(
                """
                  validate(
                      name = "${node.name}",
                      fileName = "${node.fileName}",
                      left = $left, top = $top, width = $width, height = $height
                """.trimIndent()
            )
        }
        if (node.id > 0L) {
            println(",")
            print("    isRenderNode = true")
        }
        if (node.children.isNotEmpty()) {
            println(",")
            val children = node.children.joinToString { "\"${it.name}\"" }
            print("    children = listOf($children)")
        }
        println()
        print(")")
        if (generateParameters && node.parameters.isNotEmpty()) {
            generateParameters(
                builder.convertParameters(
                    ROOT_ID, node, ParameterKind.Normal, MAX_RECURSIONS, MAX_ITERABLE_SIZE
                ),
                0
            )
        }
        println()
        node.children.forEach { generateValidate(it, view, builder) }
    }

    private fun generateParameters(parameters: List<NodeParameter>, indent: Int) {
        val indentation = " ".repeat(indent * 2)
        println(" {")
        for (param in parameters) {
            val name = param.name
            val type = param.type
            val value = toDisplayValue(type, param.value)
            print("$indentation  parameter(name = \"$name\", type = $type, value = $value)")
            if (param.elements.isNotEmpty()) {
                generateParameters(param.elements, indent + 1)
            }
            println()
        }
        print("$indentation}")
    }

    private fun toDisplayValue(type: ParameterType, value: Any?): String =
        when (type) {
            ParameterType.Boolean -> value.toString()
            ParameterType.Color ->
                "0x${Integer.toHexString(value as Int)}${if (value < 0) ".toInt()" else ""}"
            ParameterType.DimensionSp,
            ParameterType.DimensionDp -> "${value}f"
            ParameterType.Int32 -> value.toString()
            ParameterType.String -> "\"$value\""
            else -> value?.toString() ?: "null"
        }

    private fun dumpSlotTableSet(slotTableRecord: CompositionDataRecord) {
        @Suppress("ConstantConditionIf")
        if (!DEBUG) {
            return
        }
        println()
        println("=================== Groups ==========================")
        slotTableRecord.store.forEach { dumpGroup(it.asTree(), indent = 0) }
    }

    private fun dumpGroup(group: Group, indent: Int) {
        val position = group.position?.let { "\"$it\"" } ?: "null"
        val box = group.box
        val id = group.modifierInfo.mapNotNull { (it.extra as? GraphicLayerInfo)?.layerId }
            .singleOrNull() ?: 0
        println(
            "\"${"  ".repeat(indent)}\", ${group.javaClass.simpleName}, \"${group.name}\", " +
                "params: ${group.parameters.size}, children: ${group.children.size}, " +
                "$id, $position, " +
                "${box.left}, ${box.right}, ${box.right - box.left}, ${box.bottom - box.top}"
        )
        for (parameter in group.parameters) {
            println("\"${"  ".repeat(indent + 4)}\"- ${parameter.name}")
        }
        group.children.forEach { dumpGroup(it, indent + 1) }
    }

    private fun round(dp: Dp): Dp = Dp((dp.value * 10.0f).roundToInt() / 10.0f)

    //endregion
}

/**
 * Storage for the preview generated [CompositionData]s.
 */
internal interface CompositionDataRecord {
    val store: Set<CompositionData>

    companion object {
        fun create(): CompositionDataRecord = CompositionDataRecordImpl()
    }
}

private class CompositionDataRecordImpl : CompositionDataRecord {
    @OptIn(InternalComposeApi::class)
    override val store: MutableSet<CompositionData> =
        Collections.newSetFromMap(WeakHashMap())
}

/**
 * A wrapper for compositions in inspection mode. The composition inside the Inspectable component
 * is in inspection mode.
 *
 * @param compositionDataRecord [CompositionDataRecord] to record the SlotTable used in the
 * composition of [content]
 *
 * @suppress
 */
@Composable
@OptIn(InternalComposeApi::class)
internal fun Inspectable(
    compositionDataRecord: CompositionDataRecord,
    content: @Composable () -> Unit
) {
    currentComposer.collectParameterInformation()
    val store = (compositionDataRecord as CompositionDataRecordImpl).store
    store.add(currentComposer.compositionData)
    CompositionLocalProvider(
        LocalInspectionMode provides true,
        LocalInspectionTables provides store,
        content = content
    )
}
