/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.privacysandbox.sdkruntime.client.loader.impl

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Refers to the context of the SDK loaded locally.
 *
 * Supports Per-SDK storage by pointing storage related APIs to folders unique for each SDK.
 * Where possible maintains same folders hierarchy as for applications by creating folders
 * inside [getDataDir].
 * Folders with special permissions or additional logic (caches, etc) created as subfolders of same
 * application folders.
 *
 * SDK Folders hierarchy (from application [getDataDir]):
 * 1) /cache/RuntimeEnabledSdksData/<sdk_package_name> - cache
 * 2) /code_cache/RuntimeEnabledSdksData/<sdk_package_name> - code_cache
 * 3) /no_backup/RuntimeEnabledSdksData/<sdk_package_name> - no_backup
 * 4) /app_RuntimeEnabledSdksData/<sdk_package_name>/ - SDK Root (data dir)
 * 5) /app_RuntimeEnabledSdksData/<sdk_package_name>/files - [getFilesDir]
 * 6) /app_RuntimeEnabledSdksData/<sdk_package_name>/app_<folder_name> - [getDir]
 */
internal class SandboxedSdkContextCompat(
    base: Context,
    private val sdkPackageName: String,
    private val classLoader: ClassLoader?
) : ContextWrapper(base) {

    @RequiresApi(Build.VERSION_CODES.N)
    override fun createDeviceProtectedStorageContext(): Context {
        return SandboxedSdkContextCompat(
            Api24.createDeviceProtectedStorageContext(baseContext),
            sdkPackageName,
            classLoader
        )
    }

    /**
     *  Points to <app_data_dir>/app_RuntimeEnabledSdksData/<sdk_package_name>
     */
    override fun getDataDir(): File {
        val sdksDataRoot = baseContext.getDir(
            SDK_ROOT_FOLDER,
            Context.MODE_PRIVATE
        )
        return ensureDirExists(sdksDataRoot, sdkPackageName)
    }

    /**
     *  Points to <app_data_dir>/cache/RuntimeEnabledSdksData/<sdk_package_name>
     */
    override fun getCacheDir(): File {
        val sdksCacheRoot = ensureDirExists(baseContext.cacheDir, SDK_ROOT_FOLDER)
        return ensureDirExists(sdksCacheRoot, sdkPackageName)
    }

    /**
     *  Points to <app_data_dir>/code_cache/RuntimeEnabledSdksData/<sdk_package_name>
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun getCodeCacheDir(): File {
        val sdksCodeCacheRoot = ensureDirExists(
            Api21.codeCacheDir(baseContext),
            SDK_ROOT_FOLDER
        )
        return ensureDirExists(sdksCodeCacheRoot, sdkPackageName)
    }

    /**
     *  Points to <app_data_dir>/no_backup/RuntimeEnabledSdksData/<sdk_package_name>
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun getNoBackupFilesDir(): File {
        val sdksNoBackupRoot = ensureDirExists(
            Api21.noBackupFilesDir(baseContext),
            SDK_ROOT_FOLDER
        )
        return ensureDirExists(sdksNoBackupRoot, sdkPackageName)
    }

    /**
     *  Points to <app_data_dir>/app_RuntimeEnabledSdksData/<sdk_package_name>/app_<folder_name>
     *  Prefix required to maintain same hierarchy as for applications - when dir could be
     *  accessed by both [getDir] and [getDir]/app_<folder_name>.
     */
    override fun getDir(name: String, mode: Int): File {
        val dirName = "app_$name"
        return ensureDirExists(dataDir, dirName)
    }

    /**
     *  Points to <app_data_dir>/app_RuntimeEnabledSdksData/<sdk_package_name>/files
     */
    override fun getFilesDir(): File {
        return ensureDirExists(dataDir, "files")
    }

    override fun openFileInput(name: String): FileInputStream {
        val file = makeFilename(filesDir, name)
        return FileInputStream(file)
    }

    override fun openFileOutput(name: String, mode: Int): FileOutputStream {
        val file = makeFilename(filesDir, name)
        val append = (mode and MODE_APPEND) != 0
        return FileOutputStream(file, append)
    }

    override fun deleteFile(name: String): Boolean {
        val file = makeFilename(filesDir, name)
        return file.delete()
    }

    override fun getFileStreamPath(name: String): File {
        return makeFilename(filesDir, name)
    }

    override fun fileList(): Array<String> {
        return listOrEmpty(filesDir)
    }

    override fun getClassLoader(): ClassLoader? {
        return classLoader
    }

    private fun listOrEmpty(dir: File?): Array<String> {
        return dir?.list() ?: emptyArray()
    }

    private fun makeFilename(parent: File, name: String): File {
        if (name.indexOf(File.separatorChar) >= 0) {
            throw IllegalArgumentException(
                "File $name contains a path separator"
            )
        }
        return File(parent, name)
    }

    private fun ensureDirExists(parent: File, dirName: String): File {
        val dir = File(parent, dirName)
        if (!dir.exists()) {
            dir.mkdir()
        }
        return dir
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private object Api21 {
        @DoNotInline
        fun codeCacheDir(context: Context): File = context.codeCacheDir

        @DoNotInline
        fun noBackupFilesDir(context: Context): File = context.noBackupFilesDir
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private object Api24 {
        @DoNotInline
        fun createDeviceProtectedStorageContext(context: Context): Context =
            context.createDeviceProtectedStorageContext()
    }

    private companion object {
        private const val SDK_ROOT_FOLDER = "RuntimeEnabledSdksData"
    }
}