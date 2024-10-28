/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.wear.compose.material3.macrobenchmark.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults

object IconButtonBenchmark : ButtonBenchmarkBase {
    override val content: @Composable() (BoxScope.() -> Unit)
        get() = {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceAround,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(4) {
                    IconButton(
                        modifier =
                            Modifier.semantics {
                                contentDescription = numberedContentDescription(it)
                            },
                        colors = IconButtonDefaults.filledIconButtonColors(),
                        onClick = {}
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_favorite_rounded),
                            contentDescription = null,
                            modifier = Modifier.size(IconButtonDefaults.DefaultIconSize)
                        )
                    }
                }
            }
        }
}
