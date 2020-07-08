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

package androidx.ui.material.textfield

import android.os.Build
import androidx.compose.Providers
import androidx.compose.state
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.ui.core.Modifier
import androidx.ui.core.Ref
import androidx.ui.core.TextInputServiceAmbient
import androidx.ui.core.onPositioned
import androidx.ui.core.positionInRoot
import androidx.ui.core.testTag
import androidx.ui.foundation.Box
import androidx.ui.foundation.Text
import androidx.ui.foundation.contentColor
import androidx.ui.foundation.currentTextStyle
import androidx.ui.foundation.drawBackground
import androidx.ui.geometry.Offset
import androidx.ui.graphics.Color
import androidx.ui.graphics.RectangleShape
import androidx.ui.input.ImeAction
import androidx.ui.input.KeyboardType
import androidx.ui.input.PasswordVisualTransformation
import androidx.ui.input.TextFieldValue
import androidx.ui.input.TextInputService
import androidx.ui.layout.Column
import androidx.ui.layout.preferredSize
import androidx.ui.layout.preferredWidth
import androidx.ui.material.MaterialTheme
import androidx.ui.material.OutlinedTextField
import androidx.ui.material.runOnIdleComposeWithDensity
import androidx.ui.material.setMaterialContent
import androidx.ui.test.assertShape
import androidx.ui.test.captureToBitmap
import androidx.ui.test.createComposeRule
import androidx.ui.test.doClick
import androidx.ui.test.doGesture
import androidx.ui.test.doSendImeAction
import androidx.ui.test.findByTag
import androidx.ui.test.runOnIdleCompose
import androidx.ui.test.sendClick
import androidx.ui.test.waitForIdle
import androidx.ui.text.SoftwareKeyboardController
import androidx.ui.unit.IntSize
import androidx.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.math.roundToInt

@MediumTest
@RunWith(JUnit4::class)
class OutlinedTextFieldTest {

    private val ExpectedMinimumTextFieldHeight = 56.dp
    private val ExpectedPadding = 16.dp
    private val IconPadding = 12.dp
    private val IconColorAlpha = 0.54f
    private val TextfieldTag = "textField"

    @get:Rule
    val testRule = createComposeRule()

    @Test
    fun testOutlinedTextFields_singleFocus() {
        var textField1Focused = false
        val textField1Tag = "TextField1"

        var textField2Focused = false
        val textField2Tag = "TextField2"

        testRule.setMaterialContent {
            Column {
                OutlinedTextField(
                    modifier = Modifier.testTag(textField1Tag),
                    value = "input1",
                    onValueChange = {},
                    label = {},
                    onFocusChange = { textField1Focused = it }
                )
                OutlinedTextField(
                    modifier = Modifier.testTag(textField2Tag),
                    value = "input2",
                    onValueChange = {},
                    label = {},
                    onFocusChange = { textField2Focused = it }
                )
            }
        }

        findByTag(textField1Tag).doClick()

        runOnIdleCompose {
            assertThat(textField1Focused).isTrue()
            assertThat(textField2Focused).isFalse()
        }

        findByTag(textField2Tag).doClick()

        runOnIdleCompose {
            assertThat(textField1Focused).isFalse()
            assertThat(textField2Focused).isTrue()
        }
    }

    @Test
    fun testOutlinedTextField_getFocus_whenClickedOnInternalArea() {
        var focused = false
        testRule.setMaterialContent {
            Box {
                OutlinedTextField(
                    modifier = Modifier.testTag(TextfieldTag),
                    value = "input",
                    onValueChange = {},
                    label = {},
                    onFocusChange = { focused = it }
                )
            }
        }

        // Click on (2, 2) which is a background area and outside input area
        findByTag(TextfieldTag).doGesture {
            sendClick(Offset(2f, 2f))
        }

        testRule.runOnIdleComposeWithDensity {
            assertThat(focused).isTrue()
        }
    }

    @Test
    fun testOutlinedTextField_labelPosition_initial_withDefaultHeight() {
        val labelSize = Ref<IntSize>()
        val labelPosition = Ref<Offset>()
        testRule.setMaterialContent {
            Box {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    label = {
                        Text(text = "label", modifier = Modifier.onPositioned {
                            labelPosition.value = it.positionInRoot
                            labelSize.value = it.size
                        })
                    }
                )
            }
        }

        testRule.runOnIdleComposeWithDensity {
            // size
            assertThat(labelSize.value).isNotNull()
            assertThat(labelSize.value?.height).isGreaterThan(0)
            assertThat(labelSize.value?.width).isGreaterThan(0)
            assertThat(labelPosition.value?.x).isEqualTo(
                ExpectedPadding.toIntPx().toFloat()
            )
            // label is centered in 56.dp default container, plus additional 8.dp padding on top
            val minimumHeight = ExpectedMinimumTextFieldHeight.toIntPx()
            assertThat(labelPosition.value?.y).isEqualTo(
                ((minimumHeight - labelSize.value!!.height) / 2f).roundToInt() + 8.dp.toIntPx()
            )
        }
    }

    @Test
    fun testOutlinedTextField_labelPosition_whenFocused() {
        val labelSize = Ref<IntSize>()
        val labelPosition = Ref<Offset>()

        testRule.setMaterialContent {
            OutlinedTextField(
                modifier = Modifier.testTag(TextfieldTag),
                value = "",
                onValueChange = {},
                label = {
                    Text(text = "label", modifier = Modifier.onPositioned {
                        labelPosition.value = it.positionInRoot
                        labelSize.value = it.size
                    })
                }
            )
        }

        // click to focus
        clickAndAdvanceClock(TextfieldTag, 200)

        testRule.runOnIdleComposeWithDensity {
            // size
            assertThat(labelSize.value).isNotNull()
            assertThat(labelSize.value?.height).isGreaterThan(0)
            assertThat(labelSize.value?.width).isGreaterThan(0)
            // label's top position
            assertThat(labelPosition.value?.x).isEqualTo(
                ExpectedPadding.toIntPx().toFloat()
            )
            assertThat(labelPosition.value?.y).isEqualTo(0)
        }
    }

    @Test
    fun testOutlinedTextField_labelPosition_whenInput() {
        val labelSize = Ref<IntSize>()
        val labelPosition = Ref<Offset>()
        testRule.setMaterialContent {
            OutlinedTextField(
                value = "input",
                onValueChange = {},
                label = {
                    Text(text = "label", modifier = Modifier.onPositioned {
                        labelPosition.value = it.positionInRoot
                        labelSize.value = it.size
                    })
                }
            )
        }

        testRule.runOnIdleComposeWithDensity {
            // size
            assertThat(labelSize.value).isNotNull()
            assertThat(labelSize.value?.height).isGreaterThan(0)
            assertThat(labelSize.value?.width).isGreaterThan(0)
            // label's top position
            assertThat(labelPosition.value?.x).isEqualTo(
                ExpectedPadding.toIntPx().toFloat()
            )
            assertThat(labelPosition.value?.y).isEqualTo(0)
        }
    }

    @Test
    fun testOutlinedTextField_placeholderPosition_withLabel() {
        val placeholderSize = Ref<IntSize>()
        val placeholderPosition = Ref<Offset>()
        testRule.setMaterialContent {
            Box {
                OutlinedTextField(
                    modifier = Modifier.testTag(TextfieldTag),
                    value = "",
                    onValueChange = {},
                    label = { Text("label") },
                    placeholder = {
                        Text(text = "placeholder", modifier = Modifier.onPositioned {
                            placeholderPosition.value = it.positionInRoot
                            placeholderSize.value = it.size
                        })
                    }
                )
            }
        }
        // click to focus
        clickAndAdvanceClock(TextfieldTag, 200)

        testRule.runOnIdleComposeWithDensity {
            // size
            assertThat(placeholderSize.value).isNotNull()
            assertThat(placeholderSize.value?.height).isGreaterThan(0)
            assertThat(placeholderSize.value?.width).isGreaterThan(0)
            // placeholder's position
            assertThat(placeholderPosition.value?.x).isEqualTo(
                ExpectedPadding.toIntPx().toFloat()
            )
            // placeholder is centered in 56.dp default container,
            // plus additional 8.dp padding on top
            assertThat(placeholderPosition.value?.y).isEqualTo(
                ((ExpectedMinimumTextFieldHeight.toIntPx() - placeholderSize.value!!.height) /
                        2f).roundToInt() +
                        8.dp.toIntPx()
            )
        }
    }

    @Test
    fun testOutlinedTextField_placeholderPosition_whenNoLabel() {
        val placeholderSize = Ref<IntSize>()
        val placeholderPosition = Ref<Offset>()
        testRule.setMaterialContent {
            Box {
                OutlinedTextField(
                    modifier = Modifier.testTag(TextfieldTag),
                    value = "",
                    onValueChange = {},
                    label = {},
                    placeholder = {
                        Text(text = "placeholder", modifier = Modifier.onPositioned {
                            placeholderPosition.value = it.positionInRoot
                            placeholderSize.value = it.size
                        })
                    }
                )
            }
        }
        // click to focus
        clickAndAdvanceClock(TextfieldTag, 200)

        testRule.runOnIdleComposeWithDensity {
            // size
            assertThat(placeholderSize.value).isNotNull()
            assertThat(placeholderSize.value?.height).isGreaterThan(0)
            assertThat(placeholderSize.value?.width).isGreaterThan(0)
            // centered position
            assertThat(placeholderPosition.value?.x).isEqualTo(
                ExpectedPadding.toIntPx().toFloat()
            )
            // placeholder is centered in 56.dp default container,
            // plus additional 8.dp padding on top
            assertThat(placeholderPosition.value?.y).isEqualTo(
                ((ExpectedMinimumTextFieldHeight.toIntPx() - placeholderSize.value!!.height) /
                        2f).roundToInt() +
                        8.dp.toIntPx()
            )
        }
    }

    @Test
    fun testOutlinedTextField_noPlaceholder_whenInputNotEmpty() {
        val placeholderSize = Ref<IntSize>()
        val placeholderPosition = Ref<Offset>()
        testRule.setMaterialContent {
            Column {
                OutlinedTextField(
                    modifier = Modifier.testTag(TextfieldTag),
                    value = "input",
                    onValueChange = {},
                    label = {},
                    placeholder = {
                        Text(text = "placeholder", modifier = Modifier.onPositioned {
                            placeholderPosition.value = it.positionInRoot
                            placeholderSize.value = it.size
                        })
                    }
                )
            }
        }

        // click to focus
        clickAndAdvanceClock(TextfieldTag, 200)

        testRule.runOnIdleComposeWithDensity {
            assertThat(placeholderSize.value).isNull()
            assertThat(placeholderPosition.value).isNull()
        }
    }

    @Test
    fun testOutlinedTextField_placeholderColorAndTextStyle() {
        testRule.setMaterialContent {
            OutlinedTextField(
                modifier = Modifier.testTag(TextfieldTag),
                value = "",
                onValueChange = {},
                label = {},
                placeholder = {
                    Text("placeholder")
                    assertThat(contentColor())
                        .isEqualTo(
                            MaterialTheme.colors.onSurface.copy(
                                0.6f
                            )
                        )
                    assertThat(currentTextStyle()).isEqualTo(MaterialTheme.typography.subtitle1)
                }
            )
        }

        // click to focus
        findByTag(TextfieldTag).doClick()
    }

    @Test
    fun testOutlinedTextField_trailingAndLeading_sizeAndPosition() {
        val textFieldWidth = 300.dp
        val size = 30.dp
        val leadingPosition = Ref<Offset>()
        val leadingSize = Ref<IntSize>()
        val trailingPosition = Ref<Offset>()
        val trailingSize = Ref<IntSize>()

        testRule.setMaterialContent {
            OutlinedTextField(
                value = "text",
                onValueChange = {},
                modifier = Modifier.preferredWidth(textFieldWidth),
                label = {},
                leadingIcon = {
                    Box(Modifier.preferredSize(size).onPositioned {
                        leadingPosition.value = it.positionInRoot
                        leadingSize.value = it.size
                    })
                },
                trailingIcon = {
                    Box(Modifier.preferredSize(size).onPositioned {
                        trailingPosition.value = it.positionInRoot
                        trailingSize.value = it.size
                    })
                }
            )
        }

        testRule.runOnIdleComposeWithDensity {
            val minimumHeight = ExpectedMinimumTextFieldHeight.toIntPx()
            // leading
            assertThat(leadingSize.value).isEqualTo(IntSize(size.toIntPx(), size.toIntPx()))
            assertThat(leadingPosition.value?.x).isEqualTo(IconPadding.toIntPx().toFloat())
            assertThat(leadingPosition.value?.y).isEqualTo(
                ((minimumHeight - leadingSize.value!!.height) / 2f).roundToInt() + 8.dp.toIntPx()
            )
            // trailing
            assertThat(trailingSize.value).isEqualTo(IntSize(size.toIntPx(), size.toIntPx()))
            assertThat(trailingPosition.value?.x).isEqualTo(
                (textFieldWidth.toIntPx() - IconPadding.toIntPx() - trailingSize.value!!.width)
                    .toFloat()
            )
            assertThat(trailingPosition.value?.y).isEqualTo(
                ((minimumHeight - trailingSize.value!!.height) / 2f).roundToInt() + 8.dp.toIntPx()
            )
        }
    }

    @Test
    fun testOutlinedTextField_labelPositionX_initial_withTrailingAndLeading() {
        val iconSize = 30.dp
        val labelPosition = Ref<Offset>()
        testRule.setMaterialContent {
            Box {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    label = {
                        Text(text = "label", modifier = Modifier.onPositioned {
                            labelPosition.value = it.positionInRoot
                        })
                    },
                    trailingIcon = { Box(Modifier.preferredSize(iconSize)) },
                    leadingIcon = { Box(Modifier.preferredSize(iconSize)) }
                )
            }
        }

        testRule.runOnIdleComposeWithDensity {
            assertThat(labelPosition.value?.x).isEqualTo(
                (ExpectedPadding.toIntPx() + IconPadding.toIntPx() + iconSize.toIntPx())
                    .toFloat()
            )
        }
    }

    @Test
    fun testOutlinedTextField_labelPositionX_initial_withEmptyTrailingAndLeading() {
        val labelPosition = Ref<Offset>()
        testRule.setMaterialContent {
            Box {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    label = {
                        Text(text = "label", modifier = Modifier.onPositioned {
                            labelPosition.value = it.positionInRoot
                        })
                    },
                    trailingIcon = {},
                    leadingIcon = {}
                )
            }
        }

        testRule.runOnIdleComposeWithDensity {
            assertThat(labelPosition.value?.x).isEqualTo(
                ExpectedPadding.toIntPx().toFloat()
            )
        }
    }

    @Test
    fun testOutlinedTextField_colorInLeadingTrailing_whenValidInput() {
        testRule.setMaterialContent {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = {},
                isErrorValue = false,
                leadingIcon = {
                    assertThat(contentColor())
                        .isEqualTo(
                            MaterialTheme.colors.onSurface.copy(
                                IconColorAlpha
                            )
                        )
                },
                trailingIcon = {
                    assertThat(contentColor())
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
        testRule.setMaterialContent {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = {},
                isErrorValue = true,
                leadingIcon = {
                    assertThat(contentColor())
                        .isEqualTo(
                            MaterialTheme.colors.onSurface.copy(
                                IconColorAlpha
                            )
                        )
                },
                trailingIcon = {
                    assertThat(contentColor()).isEqualTo(MaterialTheme.colors.error)
                }
            )
        }
    }

    @Test
    fun testOutlinedTextField_imeActionAndKeyboardTypePropagatedDownstream() {
        val textInputService = mock<TextInputService>()
        testRule.setContent {
            Providers(
                TextInputServiceAmbient provides textInputService
            ) {
                var text = state { TextFieldValue("") }
                OutlinedTextField(
                    modifier = Modifier.testTag(TextfieldTag),
                    value = text.value,
                    onValueChange = { text.value = it },
                    label = {},
                    imeAction = ImeAction.Go,
                    keyboardType = KeyboardType.Email
                )
            }
        }

        clickAndAdvanceClock(TextfieldTag, 200)

        runOnIdleCompose {
            verify(textInputService, atLeastOnce()).startInput(
                value = any(),
                keyboardType = eq(KeyboardType.Email),
                imeAction = eq(ImeAction.Go),
                onEditCommand = any(),
                onImeActionPerformed = any()
            )
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun testOutlinedTextField_visualTransformationPropagated() {
        testRule.setMaterialContent {
            Box(Modifier.drawBackground(Color.White)) {
                OutlinedTextField(
                    modifier = Modifier.testTag(TextfieldTag),
                    value = "qwerty",
                    onValueChange = {},
                    label = {},
                    visualTransformation = PasswordVisualTransformation('\u0020')
                )
            }
        }

        findByTag(TextfieldTag)
            .captureToBitmap()
            .assertShape(
                density = testRule.density,
                backgroundColor = Color.White,
                shapeColor = Color.White,
                shape = RectangleShape,
                // avoid elevation artifacts
                shapeOverlapPixelCount = with(testRule.density) { 3.dp.toPx() }
            )
    }

    @Test
    fun testOutlinedTextField_onTextInputStartedCallback() {
        var controller: SoftwareKeyboardController? = null

        testRule.setMaterialContent {
            OutlinedTextField(
                modifier = Modifier.testTag(TextfieldTag),
                value = "",
                onValueChange = {},
                label = {},
                onTextInputStarted = {
                    controller = it
                }
            )
        }
        assertThat(controller).isNull()

        findByTag(TextfieldTag)
            .doClick()

        runOnIdleCompose {
            assertThat(controller).isNotNull()
        }
    }

    @Test
    fun testOutlinedTextField_imeActionCallback_withSoftwareKeyboardController() {
        var controller: SoftwareKeyboardController? = null

        testRule.setMaterialContent {
            OutlinedTextField(
                modifier = Modifier.testTag(TextfieldTag),
                value = "",
                onValueChange = {},
                label = {},
                imeAction = ImeAction.Go,
                onImeActionPerformed = { _, softwareKeyboardController ->
                    controller = softwareKeyboardController
                }
            )
        }
        assertThat(controller).isNull()

        findByTag(TextfieldTag)
            .doSendImeAction()

        runOnIdleCompose {
            assertThat(controller).isNotNull()
        }
    }

    private fun clickAndAdvanceClock(tag: String, time: Long) {
        findByTag(tag).doClick()
        waitForIdle()
        testRule.clockTestRule.pauseClock()
        testRule.clockTestRule.advanceClock(time)
    }
}