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

package androidx.compose.material.textfield

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldPadding
import androidx.compose.material.runOnIdleWithDensity
import androidx.compose.material.setMaterialContent
import androidx.compose.material.setMaterialContentForSizeAssertions
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.testutils.assertShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.isFocused
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.Ref
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performGesture
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.TextInputService
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class OutlinedTextFieldTest {
    private val ExpectedMinimumTextFieldHeight = 56.dp
    private val ExpectedDefaultTextFieldWidth = 280.dp
    private val ExpectedPadding = 16.dp
    private val IconPadding = 12.dp
    private val IconColorAlpha = 0.54f
    private val TextfieldTag = "textField"

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun testTextField_setSmallWidth() {
        rule.setMaterialContentForSizeAssertions {
            TextField(
                value = "input",
                onValueChange = {},
                label = {},
                modifier = Modifier.requiredWidth(40.dp)
            )
        }
            .assertWidthIsEqualTo(40.dp)
    }

    @Test
    fun testTextField_defaultWidth() {
        rule.setMaterialContentForSizeAssertions {
            TextField(
                value = "input",
                onValueChange = {},
                label = {}
            )
        }
            .assertWidthIsEqualTo(ExpectedDefaultTextFieldWidth)
    }

    @Test
    fun testOutlinedTextFields_singleFocus() {
        var textField1Focused = false
        val textField1Tag = "TextField1"

        var textField2Focused = false
        val textField2Tag = "TextField2"

        rule.setMaterialContent {
            Column {
                OutlinedTextField(
                    modifier = Modifier
                        .testTag(textField1Tag)
                        .onFocusChanged { textField1Focused = it.isFocused },
                    value = "input1",
                    onValueChange = {},
                    label = {}
                )
                OutlinedTextField(
                    modifier = Modifier
                        .testTag(textField2Tag)
                        .onFocusChanged { textField2Focused = it.isFocused },
                    value = "input2",
                    onValueChange = {},
                    label = {}
                )
            }
        }

        rule.onNodeWithTag(textField1Tag).performClick()

        rule.runOnIdle {
            assertThat(textField1Focused).isTrue()
            assertThat(textField2Focused).isFalse()
        }

        rule.onNodeWithTag(textField2Tag).performClick()

        rule.runOnIdle {
            assertThat(textField1Focused).isFalse()
            assertThat(textField2Focused).isTrue()
        }
    }

    @Test
    fun testOutlinedTextField_getFocus_whenClickedOnInternalArea() {
        var focused = false
        rule.setMaterialContent {
            Box {
                OutlinedTextField(
                    modifier = Modifier
                        .testTag(TextfieldTag)
                        .onFocusChanged { focused = it.isFocused },
                    value = "input",
                    onValueChange = {},
                    label = {}
                )
            }
        }

        // Click on (2, 2) which is a background area and outside input area
        rule.onNodeWithTag(TextfieldTag).performGesture {
            click(Offset(2f, 2f))
        }

        rule.runOnIdleWithDensity {
            assertThat(focused).isTrue()
        }
    }

    @Test
    fun testOutlinedTextField_labelPosition_initial_singlineLine() {
        val labelSize = Ref<IntSize>()
        val labelPosition = Ref<Offset>()
        rule.setMaterialContent {
            Box {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    singleLine = true,
                    label = {
                        Text(
                            text = "label",
                            modifier = Modifier.onGloballyPositioned {
                                labelPosition.value = it.positionInRoot()
                                labelSize.value = it.size
                            }
                        )
                    }
                )
            }
        }

        rule.runOnIdleWithDensity {
            // size
            assertThat(labelSize.value).isNotNull()
            assertThat(labelSize.value?.height).isGreaterThan(0)
            assertThat(labelSize.value?.width).isGreaterThan(0)
            assertThat(labelPosition.value?.x).isEqualTo(
                ExpectedPadding.roundToPx().toFloat()
            )
            // label is centered in 56.dp default container, plus additional 8.dp padding on top
            val minimumHeight = ExpectedMinimumTextFieldHeight.roundToPx()
            assertThat(labelPosition.value?.y).isEqualTo(
                ((minimumHeight - labelSize.value!!.height) / 2f).roundToInt() + 8.dp.roundToPx()
            )
        }
    }

    @Test
    fun testOutlinedTextField_labelPosition_initial_withDefaultHeight() {
        val labelSize = Ref<IntSize>()
        val labelPosition = Ref<Offset>()
        rule.setMaterialContent {
            Box {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    label = {
                        Text(
                            text = "label",
                            modifier = Modifier.onGloballyPositioned {
                                labelPosition.value = it.positionInRoot()
                                labelSize.value = it.size
                            }
                        )
                    }
                )
            }
        }

        rule.runOnIdleWithDensity {
            // size
            assertThat(labelSize.value).isNotNull()
            assertThat(labelSize.value?.height).isGreaterThan(0)
            assertThat(labelSize.value?.width).isGreaterThan(0)
            assertThat(labelPosition.value?.x).isEqualTo(
                ExpectedPadding.roundToPx().toFloat()
            )
            // label is aligned to the top with padding, plus additional 8.dp padding on top
            assertThat(labelPosition.value?.y).isEqualTo(
                TextFieldPadding.roundToPx() + 8.dp.roundToPx()
            )
        }
    }

    @Test
    fun testOutlinedTextField_labelPosition_whenFocused() {
        val labelSize = Ref<IntSize>()
        val labelPosition = Ref<Offset>()

        rule.setMaterialContent {
            OutlinedTextField(
                modifier = Modifier.testTag(TextfieldTag),
                value = "",
                onValueChange = {},
                label = {
                    Text(
                        text = "label",
                        modifier = Modifier.onGloballyPositioned {
                            labelPosition.value = it.positionInRoot()
                            labelSize.value = it.size
                        }
                    )
                }
            )
        }

        // click to focus
        rule.onNodeWithTag(TextfieldTag).performClick()

        rule.runOnIdleWithDensity {
            // size
            assertThat(labelSize.value).isNotNull()
            assertThat(labelSize.value?.height).isGreaterThan(0)
            assertThat(labelSize.value?.width).isGreaterThan(0)
            // label's top position
            assertThat(labelPosition.value?.x).isEqualTo(
                ExpectedPadding.roundToPx().toFloat()
            )
            assertThat(labelPosition.value?.y).isEqualTo(0)
        }
    }

    @Test
    fun testOutlinedTextField_labelPosition_whenInput() {
        val labelSize = Ref<IntSize>()
        val labelPosition = Ref<Offset>()
        rule.setMaterialContent {
            OutlinedTextField(
                value = "input",
                onValueChange = {},
                label = {
                    Text(
                        text = "label",
                        modifier = Modifier.onGloballyPositioned {
                            labelPosition.value = it.positionInRoot()
                            labelSize.value = it.size
                        }
                    )
                }
            )
        }

        rule.runOnIdleWithDensity {
            // size
            assertThat(labelSize.value).isNotNull()
            assertThat(labelSize.value?.height).isGreaterThan(0)
            assertThat(labelSize.value?.width).isGreaterThan(0)
            // label's top position
            assertThat(labelPosition.value?.x).isEqualTo(
                ExpectedPadding.roundToPx().toFloat()
            )
            assertThat(labelPosition.value?.y).isEqualTo(0)
        }
    }

    @Test
    fun testOutlinedTextField_placeholderPosition_withLabel() {
        val placeholderSize = Ref<IntSize>()
        val placeholderPosition = Ref<Offset>()
        rule.setMaterialContent {
            Box {
                OutlinedTextField(
                    modifier = Modifier.testTag(TextfieldTag),
                    value = "",
                    onValueChange = {},
                    label = { Text("label") },
                    placeholder = {
                        Text(
                            text = "placeholder",
                            modifier = Modifier.onGloballyPositioned {
                                placeholderPosition.value = it.positionInRoot()
                                placeholderSize.value = it.size
                            }
                        )
                    }
                )
            }
        }
        // click to focus
        rule.onNodeWithTag(TextfieldTag).performClick()

        rule.runOnIdleWithDensity {
            // size
            assertThat(placeholderSize.value).isNotNull()
            assertThat(placeholderSize.value?.height).isGreaterThan(0)
            assertThat(placeholderSize.value?.width).isGreaterThan(0)
            // placeholder's position
            assertThat(placeholderPosition.value?.x).isEqualTo(
                ExpectedPadding.roundToPx().toFloat()
            )
            // placeholder is centered in 56.dp default container,
            // plus additional 8.dp padding on top
            assertThat(placeholderPosition.value?.y).isEqualTo(
                TextFieldPadding.roundToPx() + 8.dp.roundToPx()
            )
        }
    }

    @Test
    fun testOutlinedTextField_placeholderPosition_whenNoLabel() {
        val placeholderSize = Ref<IntSize>()
        val placeholderPosition = Ref<Offset>()
        rule.setMaterialContent {
            Box {
                OutlinedTextField(
                    modifier = Modifier.testTag(TextfieldTag),
                    value = "",
                    onValueChange = {},
                    label = {},
                    placeholder = {
                        Text(
                            text = "placeholder",
                            modifier = Modifier.onGloballyPositioned {
                                placeholderPosition.value = it.positionInRoot()
                                placeholderSize.value = it.size
                            }
                        )
                    }
                )
            }
        }
        // click to focus
        rule.onNodeWithTag(TextfieldTag).performClick()

        rule.runOnIdleWithDensity {
            // size
            assertThat(placeholderSize.value).isNotNull()
            assertThat(placeholderSize.value?.height).isGreaterThan(0)
            assertThat(placeholderSize.value?.width).isGreaterThan(0)
            // centered position
            assertThat(placeholderPosition.value?.x).isEqualTo(
                ExpectedPadding.roundToPx().toFloat()
            )
            // placeholder is placed with fixed padding plus additional 8.dp padding on top
            assertThat(placeholderPosition.value?.y).isEqualTo(
                TextFieldPadding.roundToPx() + 8.dp.roundToPx()
            )
        }
    }

    @Test
    fun testOutlinedTextField_noPlaceholder_whenInputNotEmpty() {
        val placeholderSize = Ref<IntSize>()
        val placeholderPosition = Ref<Offset>()
        rule.setMaterialContent {
            Column {
                OutlinedTextField(
                    modifier = Modifier.testTag(TextfieldTag),
                    value = "input",
                    onValueChange = {},
                    label = {},
                    placeholder = {
                        Text(
                            text = "placeholder",
                            modifier = Modifier.onGloballyPositioned {
                                placeholderPosition.value = it.positionInRoot()
                                placeholderSize.value = it.size
                            }
                        )
                    }
                )
            }
        }

        // click to focus
        rule.onNodeWithTag(TextfieldTag).performClick()

        rule.runOnIdleWithDensity {
            assertThat(placeholderSize.value).isNull()
            assertThat(placeholderPosition.value).isNull()
        }
    }

    @Test
    fun testOutlinedTextField_placeholderColorAndTextStyle() {
        rule.setMaterialContent {
            OutlinedTextField(
                modifier = Modifier.testTag(TextfieldTag),
                value = "",
                onValueChange = {},
                label = {},
                placeholder = {
                    Text("placeholder")
                    assertThat(
                        LocalContentColor.current.copy(
                            alpha = LocalContentAlpha.current
                        )
                    )
                        .isEqualTo(
                            MaterialTheme.colors.onSurface.copy(
                                alpha = 0.6f
                            )
                        )
                    assertThat(LocalTextStyle.current)
                        .isEqualTo(MaterialTheme.typography.subtitle1)
                }
            )
        }

        // click to focus
        rule.onNodeWithTag(TextfieldTag).performClick()
    }

    @Test
    fun testOutlinedTextField_trailingAndLeading_sizeAndPosition() {
        val textFieldWidth = 300.dp
        val size = 30.dp
        val leadingPosition = Ref<Offset>()
        val leadingSize = Ref<IntSize>()
        val trailingPosition = Ref<Offset>()
        val trailingSize = Ref<IntSize>()

        rule.setMaterialContent {
            OutlinedTextField(
                value = "text",
                onValueChange = {},
                modifier = Modifier.width(textFieldWidth),
                label = {},
                leadingIcon = {
                    Box(
                        Modifier.size(size).onGloballyPositioned {
                            leadingPosition.value = it.positionInRoot()
                            leadingSize.value = it.size
                        }
                    )
                },
                trailingIcon = {
                    Box(
                        Modifier.size(size).onGloballyPositioned {
                            trailingPosition.value = it.positionInRoot()
                            trailingSize.value = it.size
                        }
                    )
                }
            )
        }

        rule.runOnIdleWithDensity {
            val minimumHeight = ExpectedMinimumTextFieldHeight.roundToPx()
            // leading
            assertThat(leadingSize.value).isEqualTo(IntSize(size.roundToPx(), size.roundToPx()))
            assertThat(leadingPosition.value?.x).isEqualTo(IconPadding.roundToPx().toFloat())
            assertThat(leadingPosition.value?.y).isEqualTo(
                ((minimumHeight - leadingSize.value!!.height) / 2f).roundToInt() + 8.dp.roundToPx()
            )
            // trailing
            assertThat(trailingSize.value).isEqualTo(IntSize(size.roundToPx(), size.roundToPx()))
            assertThat(trailingPosition.value?.x).isEqualTo(
                (textFieldWidth.roundToPx() - IconPadding.roundToPx() - trailingSize.value!!.width)
                    .toFloat()
            )
            assertThat(trailingPosition.value?.y).isEqualTo(
                ((minimumHeight - trailingSize.value!!.height) / 2f).roundToInt() + 8.dp.roundToPx()
            )
        }
    }

    @Test
    fun testOutlinedTextField_labelPositionX_initial_withTrailingAndLeading() {
        val iconSize = 30.dp
        val labelPosition = Ref<Offset>()
        rule.setMaterialContent {
            Box {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    label = {
                        Text(
                            text = "label",
                            modifier = Modifier.onGloballyPositioned {
                                labelPosition.value = it.positionInRoot()
                            }
                        )
                    },
                    trailingIcon = { Box(Modifier.size(iconSize)) },
                    leadingIcon = { Box(Modifier.size(iconSize)) }
                )
            }
        }

        rule.runOnIdleWithDensity {
            assertThat(labelPosition.value?.x).isEqualTo(
                (ExpectedPadding.roundToPx() + IconPadding.roundToPx() + iconSize.roundToPx())
                    .toFloat()
            )
        }
    }

    @Test
    fun testOutlinedTextField_labelPositionX_initial_withEmptyTrailingAndLeading() {
        val labelPosition = Ref<Offset>()
        rule.setMaterialContent {
            Box {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    label = {
                        Text(
                            text = "label",
                            modifier = Modifier.onGloballyPositioned {
                                labelPosition.value = it.positionInRoot()
                            }
                        )
                    },
                    trailingIcon = {},
                    leadingIcon = {}
                )
            }
        }

        rule.runOnIdleWithDensity {
            assertThat(labelPosition.value?.x).isEqualTo(
                ExpectedPadding.roundToPx().toFloat()
            )
        }
    }

    @Test
    fun testOutlinedTextField_colorInLeadingTrailing_whenValidInput() {
        rule.setMaterialContent {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = {},
                isError = false,
                leadingIcon = {
                    assertThat(LocalContentColor.current)
                        .isEqualTo(
                            MaterialTheme.colors.onSurface.copy(
                                IconColorAlpha
                            )
                        )
                },
                trailingIcon = {
                    assertThat(LocalContentColor.current)
                        .isEqualTo(
                            MaterialTheme.colors.onSurface.copy(
                                IconColorAlpha
                            )
                        )
                }
            )
        }
    }

    @Test
    fun testOutlinedTextField_colorInLeadingTrailing_whenInvalidInput() {
        rule.setMaterialContent {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = {},
                isError = true,
                leadingIcon = {
                    assertThat(LocalContentColor.current)
                        .isEqualTo(
                            MaterialTheme.colors.onSurface.copy(
                                IconColorAlpha
                            )
                        )
                },
                trailingIcon = {
                    assertThat(LocalContentColor.current).isEqualTo(MaterialTheme.colors.error)
                }
            )
        }
    }

    @Test
    fun testOutlinedTextField_imeActionAndKeyboardTypePropagatedDownstream() {
        val platformTextInputService = mock<PlatformTextInputService>()
        val textInputService = TextInputService(platformTextInputService)
        rule.setContent {
            CompositionLocalProvider(
                LocalTextInputService provides textInputService
            ) {
                var text = remember { mutableStateOf("") }
                OutlinedTextField(
                    modifier = Modifier.testTag(TextfieldTag),
                    value = text.value,
                    onValueChange = { text.value = it },
                    label = {},
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Go,
                        keyboardType = KeyboardType.Email
                    )
                )
            }
        }

        rule.onNodeWithTag(TextfieldTag).performClick()

        rule.runOnIdle {
            verify(platformTextInputService, atLeastOnce()).startInput(
                value = any(),
                imeOptions = eq(
                    ImeOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Go
                    )
                ),
                onEditCommand = any(),
                onImeActionPerformed = any()
            )
        }
    }

    @Test
    @LargeTest
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun testOutlinedTextField_visualTransformationPropagated() {
        rule.setMaterialContent {
            Box(Modifier.background(color = Color.White)) {
                OutlinedTextField(
                    modifier = Modifier.testTag(TextfieldTag),
                    value = "qwerty",
                    onValueChange = {},
                    label = {},
                    visualTransformation = PasswordVisualTransformation('\u0020')
                )
            }
        }

        rule.onNodeWithTag(TextfieldTag)
            .captureToImage()
            .assertShape(
                density = rule.density,
                backgroundColor = Color.White,
                shapeColor = Color.White,
                shape = RectangleShape,
                // avoid elevation artifacts
                shapeOverlapPixelCount = with(rule.density) { 3.dp.toPx() }
            )
    }
}
