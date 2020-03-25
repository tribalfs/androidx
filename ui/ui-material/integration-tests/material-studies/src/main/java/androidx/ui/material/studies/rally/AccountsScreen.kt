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

package androidx.ui.material.studies.rally

import androidx.compose.Composable
import androidx.ui.core.Alignment
import androidx.ui.core.Modifier
import androidx.ui.foundation.Text
import androidx.ui.foundation.VerticalScroller
import androidx.ui.graphics.Color
import androidx.ui.layout.Column
import androidx.ui.layout.ColumnAlign
import androidx.ui.layout.Spacer
import androidx.ui.layout.Stack
import androidx.ui.layout.fillMaxWidth
import androidx.ui.layout.padding
import androidx.ui.layout.preferredHeight
import androidx.ui.material.Card
import androidx.ui.material.MaterialTheme
import androidx.ui.unit.dp

/**
 * The Accounts screen.
 */
@Composable
fun AccountsBody() {
    VerticalScroller {
        Column {
            Stack(Modifier.padding(16.dp)) {
                val accountsProportion = listOf(0.595f, 0.045f, 0.095f, 0.195f, 0.045f)
                val colors = listOf(0xFF1EB980, 0xFF005D57, 0xFF04B97F, 0xFF37EFBA, 0xFFFAFFBF)
                    .map { Color(it) }
                AnimatedCircle(
                    Modifier.preferredHeight(300.dp).gravity(Alignment.Center).fillMaxWidth(),
                    accountsProportion,
                    colors
                )
                Column(modifier = Modifier.gravity(Alignment.Center)) {
                    Text(
                        text = "Total",
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.gravity(ColumnAlign.Center)
                    )
                    Text(
                        text = "$12,132.49",
                        style = MaterialTheme.typography.h2,
                        modifier = Modifier.gravity(ColumnAlign.Center)
                    )
                }
            }
            Spacer(Modifier.preferredHeight(10.dp))
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    UserData.accounts.forEach { account ->
                        AccountRow(
                            name = account.name,
                            number = account.number,
                            amount = account.balance,
                            color = account.color
                        )
                    }
                }
            }
        }
    }
}