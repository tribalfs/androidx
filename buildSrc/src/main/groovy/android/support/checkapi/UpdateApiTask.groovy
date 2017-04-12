/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.support.checkapi;

import org.gradle.api.DefaultTask
import org.gradle.api.Nullable
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.WorkResult

public class UpdateApiTask extends DefaultTask {
    @InputFile
    File newApiFile
    @InputFile
    File newRemovedApiFile

    @Input
    @Optional
    Set whitelistErrors = []

    @OutputFile
    File oldApiFile
    @OutputFile
    File oldRemovedApiFile

    @OutputFile
    @Optional
    @Nullable
    File whitelistErrorsFile

    private WorkResult copyFromToFile(File src, File dest) {
        return project.copy {
            from src
            into dest.parent
            rename { dest.name }
        }
    }

    @TaskAction
    public void doUpdate() {
        copyFromToFile(getNewApiFile(), getOldApiFile())
        copyFromToFile(getNewRemovedApiFile(), getOldRemovedApiFile())

        if (whitelistErrorsFile && !whitelistErrors.empty) {
            if (whitelistErrorsFile.exists()) {
                whitelistErrors.removeAll(whitelistErrorsFile.readLines())
            }
            whitelistErrors.each { whitelistErrorsFile << "$it\n" }
            logger.lifecycle "Whitelisted ${whitelistErrors.size()} error(s)..."
        }

        logger.lifecycle "Wrote public API definition to ${oldApiFile.name}"
    }
}