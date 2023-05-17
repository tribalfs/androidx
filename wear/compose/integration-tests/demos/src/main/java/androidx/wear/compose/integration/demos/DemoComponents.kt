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

package androidx.wear.compose.integration.demos

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.LocalContentAlpha
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.SwipeToDismissBoxState
import androidx.wear.compose.material.Text
import kotlin.reflect.KClass

/**
 * Generic demo with a [title] that will be displayed in the list of demos.
 */
sealed class Demo(val title: String, val description: String? = null) {
    override fun toString() = title
}

/**
 * Demo that launches an [Activity] when selected.
 *
 * This should only be used for demos that need to customize the activity, the large majority of
 * demos should just use [ComposableDemo] instead.
 *
 * @property activityClass the KClass (Foo::class) of the activity that will be launched when
 * this demo is selected.
 */
class ActivityDemo<T : ComponentActivity>(title: String, val activityClass: KClass<T>) : Demo(title)

/**
 * A category of [Demo]s, that will display a list of [demos] when selected.
 */
class DemoCategory(
    title: String,
    val demos: List<Demo>
) : Demo(title)

/**
 * Parameters which are used by [Demo] screens.
 */
class DemoParameters(
    val navigateBack: () -> Unit,
    val swipeToDismissBoxState: SwipeToDismissBoxState
)

/**
 * Demo that displays [Composable] [content] when selected,
 * with a method to navigate back to the parent.
 */
class ComposableDemo(
    title: String,
    description: String? = null,
    val content: @Composable (params: DemoParameters) -> Unit,
) : Demo(title, description)

/**
 * A simple [Icon] with default size
 */
@Composable
fun DemoIcon(
    resourceId: Int,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(id = resourceId),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .wrapContentSize(align = Alignment.Center),
    )
}

@Composable
fun DemoImage(
    resourceId: Int,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Image(
        painter = painterResource(id = resourceId),
        contentDescription = null,
        modifier = modifier.size(size),
        contentScale = ContentScale.Crop,
        alpha = LocalContentAlpha.current
    )
}

@Composable
fun TextIcon(
    text: String,
    size: Dp = 24.dp,
    style: TextStyle = MaterialTheme.typography.title2
) {
    Button(
        modifier = Modifier
            .padding(0.dp)
            .requiredSize(32.dp),
        onClick = {},
        colors = ButtonDefaults.buttonColors(
            disabledBackgroundColor = MaterialTheme.colors.primary.copy(
                alpha = LocalContentAlpha.current
            ),
            disabledContentColor = MaterialTheme.colors.onPrimary.copy(
                alpha = LocalContentAlpha.current
            )
        ),
        enabled = false
    ) {
        Box(
            modifier = Modifier
                .padding(all = 0.dp)
                .requiredSize(size)
                .wrapContentSize(align = Alignment.Center)
        ) {
            Text(
                text = text,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onPrimary.copy(alpha = LocalContentAlpha.current),
                style = style,
            )
        }
    }
}

@Composable
fun Centralize(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

public val DemoListTag = "DemoListTag"

public val AlternatePrimaryColor1 = Color(0x7F, 0xCF, 0xFF)
public val AlternatePrimaryColor2 = Color(0xD0, 0xBC, 0xFF)
public val AlternatePrimaryColor3 = Color(0x6D, 0xD5, 0x8C)
