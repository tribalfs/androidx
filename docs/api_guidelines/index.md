# Library API guidelines

[TOC]

## Introduction {#introduction}

This guide is an addendum to
s.android.com/api-guidelines,
which covers standard and practices for designing platform APIs.

All platform API design guidelines also apply to Jetpack libraries, with any
additional guidelines or exceptions noted in this document. Jetpack libraries
also follow
[explicit API mode](https://kotlinlang.org/docs/reference/whatsnew14.html#explicit-api-mode-for-library-authors)
for Kotlin libraries.

<!--#include file="/company/teams/androidx/api_guidelines/modules.md"-->

<!--#include file="/company/teams/androidx/api_guidelines/platform_compat.md"-->

<!--#include file="/company/teams/androidx/api_guidelines/compat.md"-->

<!--#include file="/company/teams/androidx/api_guidelines/deprecation.md"-->

<!--#include file="/company/teams/androidx/api_guidelines/resources.md"-->

<!--#include file="/company/teams/androidx/api_guidelines/dependencies.md"-->

<!--#include file="/company/teams/androidx/api_guidelines/misc.md"-->

## Testing Guidelines

### [Do not Mock, AndroidX](do_not_mock.md)

### Validating class verification fixes

To verify class verification, the best way is to look for `adb` output during
install time.

You can generate class verification logs from test APKs. Simply call the
class/method that should generate a class verification failure in a test.

The test APK will generate class verification logs on install.

```bash
# Enable ART logging (requires root). Note the 2 pairs of quotes!
adb root
adb shell setprop dalvik.vm.dex2oat-flags '"--runtime-arg -verbose:verifier"'

# Restart Android services to pick up the settings
adb shell stop && adb shell start

# Optional: clear logs which aren't relevant
adb logcat -c

# Install the app and check for ART logs
# This line is what triggers log lines, and can be repeated
adb install -d -r someApk.apk

# it's useful to run this _during_ install in another shell
adb logcat | grep 'dex2oat'
...
... I dex2oat : Soft verification failures in
```

<!--#include file="/company/teams/androidx/api_guidelines/checks.md"-->

<!--#include file="/company/teams/androidx/api_guidelines/behavior_changes.md"-->

<!--#include file="/company/teams/androidx/api_guidelines/samples.md"-->
