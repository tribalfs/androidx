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

package androidx.compose.benchmark

import androidx.compose.Composable
import androidx.compose.Model
import androidx.compose.Observe
import androidx.compose.benchmark.realworld4.RealWorld4_FancyWidget_000
import androidx.compose.composer
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.ui.core.dp
import androidx.ui.foundation.ColoredRect
import androidx.ui.graphics.Color
import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@LargeTest
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ComposeBenchmark: ComposeBenchmarkBase() {

    @UiThreadTest
    @Test
    fun benchmark_01_Compose_OneRect() {
        val model = ColorModel()
        measureCompose {
            OneRect(model)
        }
    }

    @UiThreadTest
    @Test
    fun benchmark_02_Compose_TenRects() {
        val model = ColorModel()
        measureCompose {
            TenRects(model)
        }
    }

    @UiThreadTest
    @Test
    fun benchmark_03_Compose_100Rects() {
        val model = ColorModel()
        measureCompose {
            HundredRects(model = model)
        }
    }

    @UiThreadTest
    @Test
    fun benchmark_04_Recompose_OneRect() {
        val model = ColorModel()
        measureRecompose {
            compose {
                OneRect(model)
            }
            update {
                model.toggle()
            }
        }
    }

    @UiThreadTest
    @Test
    fun benchmark_05_Recompose_TenRect_Wide() {
        val model = ColorModel()
        measureRecompose {
            compose {
                TenRects(model, narrow = false)
            }
            update {
                model.toggle()
            }
        }
    }

    @UiThreadTest
    @Test
    fun benchmark_06_Recompose_TenRect_Narrow() {
        val model = ColorModel()
        measureRecompose {
            compose {
                TenRects(model, narrow = true)
            }
            update {
                model.toggle()
            }
        }
    }

    @UiThreadTest
    @Test
    fun benchmark_07_Recompose_100Rect_Wide() {
        val model = ColorModel()
        measureRecompose {
            compose {
                HundredRects(model, narrow = false)
            }
            update {
                model.toggle()
            }
        }
    }

    @UiThreadTest
    @Test
    fun benchmark_08_Recompose_100Rect_Narrow() {
        val model = ColorModel()
        measureRecompose {
            compose {
                HundredRects(model, narrow = true)
            }
            update {
                model.toggle()
            }
        }
    }

    @UiThreadTest
    @Test
    @Ignore("Disabled as it appears to not do anything")
    fun benchmark_realworld4_mid_recompose() {
        val model = androidx.compose.benchmark.realworld4.createSampleData()
        measureRecompose {
            compose {
                RealWorld4_FancyWidget_000(model)
            }
            update {
                model.f2.f15.f1.f1.f1_modified = !model.f2.f15.f1.f1.f1_modified
            }
        }
    }

}

private val color = Color.Yellow

@Model
class ColorModel(var color: Color = Color.Black) {
    fun toggle() {
        color = if (color == Color.Black) Color.Red else Color.Black
    }
}

@Composable
fun OneRect(model: ColorModel) {
    ColoredRect(color = model.color, width = 10.dp, height = 10.dp)
}

@Composable
fun TenRects(model: ColorModel, narrow: Boolean = false) {
    if (narrow) {
        Observe {
            ColoredRect(color = model.color, width = 10.dp, height = 10.dp)
        }
    } else {
        ColoredRect(color = model.color, width = 10.dp, height = 10.dp)
    }
    repeat(9) {
        ColoredRect(color = color, width = 10.dp, height = 10.dp)
    }
}

@Composable
fun HundredRects(model: ColorModel, narrow: Boolean = false) {
    repeat(100) {
        if (it % 10 == 0)
            if (narrow) {
                Observe {
                    ColoredRect(color = model.color, width = 10.dp, height = 10.dp)
                }
            } else {
                ColoredRect(color = model.color, width = 10.dp, height = 10.dp)
            }
        else
            ColoredRect(color = color, width = 10.dp, height = 10.dp)
    }
}
