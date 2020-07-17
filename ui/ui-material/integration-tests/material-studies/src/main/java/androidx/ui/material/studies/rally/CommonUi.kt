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

package androidx.ui.material.studies.rally

import androidx.compose.Composable
import androidx.ui.core.Alignment
import androidx.ui.core.Modifier
import androidx.compose.foundation.Box
import androidx.compose.foundation.Text
import androidx.compose.foundation.background
import androidx.ui.graphics.Color
import androidx.ui.layout.Arrangement
import androidx.ui.layout.Column
import androidx.ui.layout.Row
import androidx.ui.layout.Spacer
import androidx.ui.layout.preferredHeight
import androidx.ui.layout.preferredSize
import androidx.ui.layout.preferredWidth
import androidx.ui.material.Divider
import androidx.ui.material.MaterialTheme
import androidx.ui.material.icons.Icons
import androidx.ui.unit.dp
import java.text.DecimalFormat

/**
 * A row representing the basic information of an Account.
 */
@Composable
fun AccountRow(name: String, number: Int, amount: Float, color: Color) {
    BaseRow(
        color = color,
        title = name,
        subtitle = "• • • • • " + accountDecimalFormat.format(number),
        amount = amount,
        negative = false
    )
}

/**
 * A row representing the basic information of a Bill.
 */
@Composable
fun BillRow(name: String, due: String, amount: Float, color: Color) {
    BaseRow(
        color = color,
        title = name,
        subtitle = "Due $due",
        amount = amount,
        negative = true
    )
}

@Composable
private fun BaseRow(
    color: Color,
    title: String,
    subtitle: String,
    amount: Float,
    negative: Boolean
) {
    Row(Modifier.preferredHeight(68.dp)) {
        val typography = MaterialTheme.typography
        AccountIndicator(color = color, modifier = Modifier.gravity(Alignment.CenterVertically))
        Spacer(Modifier.preferredWidth(8.dp))
        Column(Modifier.gravity(Alignment.CenterVertically)) {
            Text(text = title, style = typography.body1)
            Text(text = subtitle, style = typography.subtitle1)
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.gravity(Alignment.CenterVertically).preferredWidth(113.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (negative) "–$ " else "$ ",
                style = typography.h6,
                modifier = Modifier.gravity(Alignment.CenterVertically)
            )
            Text(
                text = formatAmount(amount),
                style = typography.h6,
                modifier = Modifier.gravity(Alignment.CenterVertically)
            )
        }
        Spacer(Modifier.preferredWidth(16.dp))
        Icon(
            Icons.Filled.ArrowForwardIos,
            tintColor = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.gravity(Alignment.CenterVertically),
            size = 12.dp
        )
    }
    RallyDivider()
}

/**
 * A vertical colored line that is used in a [BaseRow] to differentiate accounts.
 */
@Composable
private fun AccountIndicator(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.preferredSize(4.dp, 36.dp).background(color = color))
}

@Composable
fun RallyDivider(modifier: Modifier = Modifier) {
    Divider(color = MaterialTheme.colors.background, thickness = 1.dp, modifier = modifier)
}

fun formatAmount(amount: Float): String {
    return amountDecimalFormat.format(amount)
}

private val accountDecimalFormat = DecimalFormat("####")
private val amountDecimalFormat = DecimalFormat("#,###.##")

/**
 * Used with accounts and bills to create the animated circle.
 */
fun <E> List<E>.extractProportions(selector: (E) -> Float): List<Float> {
    val total = this.sumByDouble { selector(it).toDouble() }
    return this.map { (selector(it) / total).toFloat() }
}