/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package android.support.tools.jetifier.plugin.gradle

import android.support.tools.jetifier.core.Processor
import android.support.tools.jetifier.core.TransformationResult
import android.support.tools.jetifier.core.config.Config
import android.support.tools.jetifier.core.utils.Log
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import java.io.File
import java.nio.file.Path

class TasksCommon {

    companion object {

        var configFilePath: Path? = null

        fun processFiles(config: Config,
                         filesToProcess: Set<File>,
                         logger: Logger,
                         outputDir: File): TransformationResult {
            outputDir.mkdirs()

            logger.log(LogLevel.DEBUG, "Jetifier will now process the following files:")
            filesToProcess.forEach {
                logger.log(LogLevel.DEBUG, it.absolutePath)
            }

            // Hook to the gradle logger
            Log.logConsumer = JetifierLoggerAdapter(logger)

            val processor = Processor.createProcessor(config)
            return processor.transform(filesToProcess, outputDir.toPath(), true)
        }

        fun shouldSkipArtifact(artifactId: String, groupId: String?, config: Config): Boolean {
            return config.pomRewriteRules.any {
                it.from.artifactId == artifactId && it.from.groupId == groupId
            }
        }
    }
}