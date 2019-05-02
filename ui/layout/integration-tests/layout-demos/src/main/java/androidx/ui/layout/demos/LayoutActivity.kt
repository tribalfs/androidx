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

package androidx.ui.layout.demos

import android.app.Activity
import android.os.Bundle
import androidx.ui.core.CraneWrapper
import androidx.ui.core.Dp
import androidx.ui.core.Text
import androidx.ui.core.dp
import androidx.ui.layout.Column
import androidx.ui.layout.Container
import androidx.ui.layout.CrossAxisAlignment
import androidx.ui.layout.HeightSpacer
import androidx.ui.layout.MainAxisAlignment
import androidx.ui.layout.Row
import androidx.ui.layout.WidthSpacer
import androidx.ui.layout.Wrap
import androidx.ui.painting.Color
import androidx.ui.painting.TextSpan
import androidx.ui.painting.TextStyle
import androidx.compose.Children
import androidx.compose.Composable
import androidx.compose.composer
import androidx.compose.setContent

class LayoutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            <CraneWrapper>
                <LayoutDemo />
            </CraneWrapper>
        }
    }
}

@Composable
fun ContainerWithBackground(
    width: Dp? = null,
    height: Dp? = null,
    color: Color,
    @Children children: @Composable() () -> Unit
) {
    <Wrap>
        <DrawRectangle color />
        <Container width height>
            <children />
        </Container>
    </Wrap>
}

@Composable
fun LayoutDemo() {
    val lightGrey = Color(0xFFCFD8DC.toInt())
    <Column mainAxisAlignment=MainAxisAlignment.Start crossAxisAlignment=CrossAxisAlignment.Start>
        <Text text=TextSpan(text = "Row", style = TextStyle(fontSize = 48f)) />
        <ContainerWithBackground width=ExampleSize color=lightGrey>
            <Row>
                <PurpleSquare />
                <CyanSquare />
            </Row>
        </ContainerWithBackground>
        <HeightSpacer height=24.dp />
        <ContainerWithBackground width=ExampleSize color=lightGrey>
            <Row mainAxisAlignment=MainAxisAlignment.Center>
                <PurpleSquare />
                <CyanSquare />
            </Row>
        </ContainerWithBackground>
        <HeightSpacer height=24.dp />
        <ContainerWithBackground width=ExampleSize color=lightGrey>
            <Row mainAxisAlignment=MainAxisAlignment.End>
                <PurpleSquare />
                <CyanSquare />
            </Row>
        </ContainerWithBackground>
        <HeightSpacer height=24.dp />
        <ContainerWithBackground width=ExampleSize color=lightGrey>
            <Row crossAxisAlignment=CrossAxisAlignment.Start>
                <PurpleSquare />
                <CyanSquare />
            </Row>
        </ContainerWithBackground>
        <HeightSpacer height=24.dp />
        <ContainerWithBackground width=ExampleSize color=lightGrey>
            <Row crossAxisAlignment=CrossAxisAlignment.End>
                <PurpleSquare />
                <CyanSquare />
            </Row>
        </ContainerWithBackground>
        <HeightSpacer height=24.dp />
        <Text text=TextSpan(text = "Column", style = TextStyle(fontSize = 48f)) />
        <Row>
            <ContainerWithBackground height=ExampleSize color=lightGrey>
                <Column>
                    <PurpleSquare />
                    <CyanSquare />
                </Column>
            </ContainerWithBackground>
            <WidthSpacer width=24.dp />
            <ContainerWithBackground height=ExampleSize color=lightGrey>
                <Column mainAxisAlignment=MainAxisAlignment.Center>
                    <PurpleSquare />
                    <CyanSquare />
                </Column>
            </ContainerWithBackground>
            <WidthSpacer width=24.dp />
            <ContainerWithBackground height=ExampleSize color=lightGrey>
                <Column mainAxisAlignment=MainAxisAlignment.End>
                    <PurpleSquare />
                    <CyanSquare />
                </Column>
            </ContainerWithBackground>
            <WidthSpacer width=24.dp />
            <ContainerWithBackground height=ExampleSize color=lightGrey>
                <Column crossAxisAlignment=CrossAxisAlignment.Start>
                    <PurpleSquare />
                    <CyanSquare />
                </Column>
            </ContainerWithBackground>
            <WidthSpacer width=24.dp />
            <ContainerWithBackground height=ExampleSize color=lightGrey>
                <Column crossAxisAlignment=CrossAxisAlignment.End>
                    <PurpleSquare />
                    <CyanSquare />
                </Column>
            </ContainerWithBackground>
        </Row>
    </Column>
}

@Composable
fun PurpleSquare() {
    <Container width=BigSize height=BigSize>
        <DrawRectangle color=Color(0xFF6200EE.toInt()) />
    </Container>
}

@Composable
fun CyanSquare() {
    <Container width=SmallSize height=SmallSize>
        <DrawRectangle color=Color(0xFF03DAC6.toInt()) />
    </Container>
}

private val SmallSize = 24.dp
private val BigSize = 48.dp
private val ExampleSize = 140.dp