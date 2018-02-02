/*
 * Copyright 2018 The Android Open Source Project
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

package android.arch.navigation.safe.args.generator

import android.arch.navigation.safe.args.generator.models.Destination
import java.io.File

fun generateSafeArgs(rFilePackage: String, applicationId: String,
        navigationXml: File, outputDir: File) {
    val rawDestination = parseNavigationFile(navigationXml, rFilePackage)
    val resolvedDestination = resolveArguments(rawDestination)

    fun writeJavaFile(destination: Destination) {
        if (destination.actions.isNotEmpty()) {
            generateDirectionsJavaFile(applicationId, destination).writeTo(outputDir)
        }
        destination.nested.forEach(::writeJavaFile)
    }
    writeJavaFile(resolvedDestination)
}
