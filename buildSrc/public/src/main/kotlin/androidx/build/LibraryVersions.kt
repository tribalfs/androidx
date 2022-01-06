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

package androidx.build

/**
 * The list of versions codes of all the libraries in this project.
 */
object LibraryVersions {
    val ACTIVITY = Version("1.5.0-alpha01")
    val ADS_IDENTIFIER = Version("1.0.0-alpha05")
    val ANNOTATION = Version("1.4.0-alpha02")
    val ANNOTATION_EXPERIMENTAL = Version("1.3.0-alpha01")
    val APPCOMPAT = Version("1.5.0-alpha01")
    val APPSEARCH = Version("1.0.0-alpha05")
    val ARCH_CORE = Version("2.2.0-alpha01")
    val ASYNCLAYOUTINFLATER = Version("1.1.0-alpha01")
    val AUTOFILL = Version("1.2.0-beta02")
    val BENCHMARK = Version("1.1.0-beta01")
    val BIOMETRIC = Version("1.2.0-alpha05")
    val BROWSER = Version("1.5.0-alpha01")
    val BUILDSRC_TESTS = Version("1.0.0-alpha01")
    val CAMERA = Version("1.1.0-beta01")
    val CAMERA_PIPE = Version("1.0.0-alpha01")
    val CARDVIEW = Version("1.1.0-alpha01")
    val CAR_APP = Version("1.2.0-beta01")
    val COLLECTION = Version("1.3.0-alpha01")
    val COLLECTION2 = Version("1.3.0-alpha01")
    val CONTENTPAGER = Version("1.1.0-alpha01")
    val COMPOSE_MATERIAL3 = Version(System.getenv("COMPOSE_CUSTOM_VERSION") ?: "1.0.0-alpha03")
    val COMPOSE = Version(System.getenv("COMPOSE_CUSTOM_VERSION") ?: "1.2.0-alpha01")
    val COORDINATORLAYOUT = Version("1.3.0-alpha01")
    val CORE = Version("1.8.0-alpha03")
    val CORE_ANIMATION = Version("1.0.0-alpha03")
    val CORE_ANIMATION_TESTING = Version("1.0.0-alpha03")
    val CORE_APPDIGEST = Version("1.0.0-alpha01")
    val CORE_GOOGLE_SHORTCUTS = Version("1.1.0-alpha02")
    val CORE_PERFORMANCE = Version("1.0.0-alpha02")
    val CORE_REMOTEVIEWS = Version("1.0.0-alpha02")
    val CORE_ROLE = Version("1.2.0-alpha01")
    val CORE_SPLASHSCREEN = Version("1.0.0-beta01")
    val CURSORADAPTER = Version("1.1.0-alpha01")
    val CUSTOMVIEW = Version("1.2.0-alpha01")
    val DATASTORE = Version("1.1.0-alpha01")
    val DOCUMENTFILE = Version("1.1.0-alpha02")
    val DRAGANDDROP = Version("1.0.0-alpha03")
    val DRAWERLAYOUT = Version("1.2.0-alpha01")
    val DYNAMICANIMATION = Version("1.1.0-alpha04")
    val DYNAMICANIMATION_KTX = Version("1.0.0-alpha04")
    val EMOJI = Version("1.2.0-alpha03")
    val EMOJI2 = Version("1.1.0-alpha02")
    val ENTERPRISE = Version("1.1.0-rc01")
    val EXIFINTERFACE = Version("1.4.0-alpha01")
    val FRAGMENT = Version("1.5.0-alpha01")
    val FUTURES = Version("1.2.0-alpha01")
    val GLANCE = Version("1.0.0-alpha02")
    val GRAPHICS = Version("1.0.0-alpha01")
    val GRIDLAYOUT = Version("1.1.0-alpha01")
    val HEALTH_SERVICES_CLIENT = Version("1.0.0-alpha04")
    val HEIFWRITER = Version("1.1.0-alpha02")
    val HILT = Version("1.1.0-alpha01")
    val HILT_NAVIGATION_COMPOSE = Version("1.1.0-alpha01")
    val INSPECTION = Version("1.0.0")
    val INTERPOLATOR = Version("1.1.0-alpha01")
    val JETIFIER = Version("1.0.0-beta11")
    val LEANBACK = Version("1.2.0-alpha03")
    val LEANBACK_PAGING = Version("1.1.0-alpha10")
    val LEANBACK_PREFERENCE = Version("1.2.0-alpha03")
    val LEANBACK_TAB = Version("1.1.0-beta01")
    val LEANBACK_GRID = Version("1.0.0-alpha02")
    val LEGACY = Version("1.1.0-alpha01")
    val LOCALBROADCASTMANAGER = Version("1.2.0-alpha01")
    val LIBYUV = Version("0.1.0-dev01")
    val LIFECYCLE = Version("2.5.0-alpha01")
    val LIFECYCLE_VIEWMODEL_COMPOSE = Version("2.5.0-alpha01")
    val LIFECYCLE_EXTENSIONS = Version("2.2.0")
    val LOADER = Version("1.2.0-alpha01")
    val MEDIA = Version("1.5.0-beta02")
    val MEDIA2 = Version("1.3.0-alpha01")
    val MEDIAROUTER = Version("1.3.0-alpha02")
    val METRICS = Version("1.0.0-alpha01")
    val NAVIGATION = Version("2.5.0-alpha01")
    val PAGING = Version("3.2.0-alpha01")
    val PAGING_COMPOSE = Version("1.0.0-alpha15")
    val PALETTE = Version("1.1.0-alpha01")
    val PRINT = Version("1.1.0-beta01")
    val PERCENTLAYOUT = Version("1.1.0-alpha01")
    val PREFERENCE = Version("1.3.0-alpha01")
    val PROFILEINSTALLER = Version("1.2.0-alpha01")
    val RECOMMENDATION = Version("1.1.0-alpha01")
    val RECYCLERVIEW = Version("1.3.0-alpha02")
    val RECYCLERVIEW_SELECTION = Version("1.2.0-alpha02")
    val REMOTECALLBACK = Version("1.0.0-alpha02")
    val RESOURCEINSPECTION = Version("1.1.0-alpha01")
    val ROOM = Version("2.5.0-alpha01")
    val SAVEDSTATE = Version("1.2.0-alpha01")
    val SECURITY = Version("1.1.0-alpha04")
    val SECURITY_APP_AUTHENTICATOR = Version("1.0.0-alpha03")
    val SECURITY_APP_AUTHENTICATOR_TESTING = Version("1.0.0-alpha02")
    val SECURITY_BIOMETRIC = Version("1.0.0-alpha01")
    val SECURITY_IDENTITY_CREDENTIAL = Version("1.0.0-alpha04")
    val SHARETARGET = Version("1.3.0-alpha01")
    val SLICE = Version("1.1.0-alpha02")
    val SLICE_BENCHMARK = Version("1.1.0-alpha02")
    val SLICE_BUILDERS_KTX = Version("1.0.0-alpha08")
    val SLICE_REMOTECALLBACK = Version("1.0.0-alpha01")
    val SLIDINGPANELAYOUT = Version("1.3.0-alpha01")
    val STARTUP = Version("1.2.0-alpha01")
    val SQLITE = Version("2.3.0-alpha01")
    val SQLITE_INSPECTOR = Version("2.1.0-alpha01")
    val SWIPEREFRESHLAYOUT = Version("1.2.0-alpha01")
    val TESTSCREENSHOT = Version("1.0.0-alpha01")
    val TEXT = Version("1.0.0-alpha01")
    val TEXTCLASSIFIER = Version("1.0.0-alpha03")
    val TRACING = Version("1.1.0-beta03")
    val TRANSITION = Version("1.5.0-alpha01")
    val TVPROVIDER = Version("1.1.0-alpha02")
    val VECTORDRAWABLE = Version("1.2.0-alpha03")
    val VECTORDRAWABLE_ANIMATED = Version("1.2.0-alpha01")
    val VECTORDRAWABLE_SEEKABLE = Version("1.0.0-alpha03")
    val VERSIONED_PARCELABLE = Version("1.2.0-alpha01")
    val VIEWPAGER = Version("1.1.0-alpha02")
    val VIEWPAGER2 = Version("1.1.0-beta02")
    val WEAR = Version("1.3.0-alpha02")
    val WEAR_COMPOSE = Version("1.0.0-alpha14")
    val WEAR_INPUT = Version("1.2.0-alpha03")
    val WEAR_INPUT_TESTING = WEAR_INPUT
    val WEAR_ONGOING = Version("1.1.0-alpha01")
    val WEAR_PHONE_INTERACTIONS = Version("1.1.0-alpha03")
    val WEAR_REMOTE_INTERACTIONS = Version("1.1.0-alpha01")
    val WEAR_TILES = Version("1.1.0-alpha01")
    val WEAR_WATCHFACE = Version("1.1.0-alpha02")
    val WEBKIT = Version("1.5.0-alpha01")
    val WINDOW = Version("1.1.0-alpha01")
    val WINDOW_EXTENSIONS = Version("1.1.0-alpha01")
    val WINDOW_SIDECAR = Version("1.0.0-rc01")
    val WORK = Version("2.8.0-alpha01")
}
