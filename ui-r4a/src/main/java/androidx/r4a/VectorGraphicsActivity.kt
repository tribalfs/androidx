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

package androidx.r4a

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import androidx.ui.vectorgraphics.PathBuilder
import androidx.ui.vectorgraphics.PathDelegate
import com.google.r4a.Composable
import com.google.r4a.composer
import com.google.r4a.registerAdapter
import com.google.r4a.setContent

class VectorGraphicsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val res = getResources()
        setContent {
            composer.registerAdapter { parent, child ->
                adoptVectorGraphic(parent, child)
            }

            <LinearLayout orientation=LinearLayout.VERTICAL>
                <vectorResource res resId=androidx.ui.port.R.drawable.ic_crane_logo_text />
                <vectorShape />
            </LinearLayout>
        }
    }

    @Composable
    fun vectorShape() {
        val viewportWidth = 300.0f
        val viewportHeight = 300.0f
        <vector name="vectorShape"
                defaultWidth=300.0f
                defaultHeight=300.0f
                viewportWidth
                viewportHeight>
            <group
                scaleX=0.75f
                scaleY=0.75f
                rotate=45.0f
                pivotX=(viewportWidth / 2)
                pivotY=(viewportHeight / 2)>
                <backgroundPath vectorWidth=viewportWidth vectorHeight=viewportHeight />
                <stripePath vectorWidth=viewportWidth vectorHeight=viewportHeight />
                <group
                    translateX=50.0f
                    translateY=50.0f
                    pivotX=(viewportWidth / 2)
                    pivotY=(viewportHeight / 2)
                    rotate=25.0f>
                    val pathData = PathDelegate {
                        moveTo(viewportWidth / 2 - 100, viewportHeight / 2 - 100)
                        horizontalLineToRelative(200.0f)
                        verticalLineToRelative(200.0f)
                        horizontalLineToRelative(-200.0f)
                        close()
                    }
                    <path fill=Color.MAGENTA pathData />
                </group>
            </group>
        </vector>
    }

    @Composable
    fun backgroundPath(vectorWidth: Float, vectorHeight: Float) {
        val background = PathDelegate {
            horizontalLineTo(vectorWidth)
            verticalLineTo(vectorWidth)
            horizontalLineTo(0.0f)
            close()
        }

        <path fill=Color.CYAN pathData=background />
    }

    @Composable
    fun stripePath(vectorWidth: Float, vectorHeight: Float) {
        val stripeDelegate = PathDelegate {
            stripe(vectorWidth, vectorHeight, 10)
        }

        <path fill=Color.BLUE pathData=stripeDelegate />
    }

    private fun PathBuilder.stripe(vectorWidth: Float, vectorHeight: Float, numLines: Int) {
        val stepSize = vectorWidth / numLines
        var currentStep = stepSize
        for (i in 1..numLines) {
            moveTo(currentStep, 0.0f)
            verticalLineTo(vectorHeight)
            currentStep += stepSize
        }
    }
}