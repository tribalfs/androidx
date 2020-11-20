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

package androidx.compose.runtime.mock

import androidx.compose.runtime.Composable

@Suppress("ComposableNaming")
@Composable
fun MockComposeScope.reportsTo(report: Report) {
    text(report.from)
    text("reports to")
    text(report.to)
}

fun MockViewValidator.reportsTo(report: Report) {
    text(report.from)
    text("reports to")
    text(report.to)
}

@Suppress("ComposableNaming")
@Composable
fun MockComposeScope.reportsReport(reports: Iterable<Report>) {
    linear {
        repeat(of = reports) { report ->
            reportsTo(report)
        }
    }
}

fun MockViewValidator.reportsReport(reports: Iterable<Report>) {
    linear {
        repeat(of = reports) { report ->
            reportsTo(report)
        }
    }
}
