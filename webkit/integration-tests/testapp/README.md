# WebView Demo App

The WebView/Webkit demo app serves as both a practical demonstration how to use
the latest AndroidX Webkit APIs and as a means to exercise those APIs for manual
testing.

## Building the demo app

```shell
cd frameworks/support/

# Optional: you can use Android Studio as your editor
./studiow -y

# Build the app
./gradlew :webkit:integration-tests:testapp:assembleDebug

# Install the app
./gradlew :webkit:integration-tests:testapp:installDebug

# Check for Lint warnings
./gradlew :webkit:integration-tests:testapp:lintDebug

# Optional: launch the app via adb
adb shell am start -n com.example.androidx.webkit/.MainActivity
adb shell am start -n com.example.androidx.webkit/.ForceDarkActivity # or, any exported Activity
```

## Extending the demo app

1. Add a new Activity under
   [src/main/java/com/example/androidx/webkit/](src/main/java/com/example/androidx/webkit/).
1. Add this Activity to the end of the `MenuListView.MenuItem[]` array in
   [MainActivity.java](src/main/java/com/example/androidx/webkit/MainActivity.java),
   or in a suitable lower-level Activity, like
   [SafeBrowsingActivity.java](src/main/java/com/example/androidx/webkit/SafeBrowsingActivity.java).
1. Export this Activity in AndroidManifest.xml. Try to organize your Activity
   with other related Activities, or leave space if things are unrelated.
1. Before uploading a change, please take a [screen
   recording](https://developer.android.com/studio/command-line/adb#screenrecord)
   using the new Activity in the testapp.

## Downloading prebuilt APKs

We **do not** publicly distribute prebuilt APKs. Googlers can download prebuilt
APKs by following [these
instructions](https://docs.google.com/document/d/1K_uOjyTn_UldZP1YxmvCEYXibDn2YB-S_76r3Y-z0bg/edit?usp=sharing).
