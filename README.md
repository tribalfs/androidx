# SESL(OneUI) Android Jetpack (Unofficial)

This fork hosts modified versions of some Android Jetpack modules and additional sesl.androidx.* modules. These are intended for implementing OneUI-styled Android applications while simultaneously enjoying the latest features and updates of Android Jetpack. This library is free for everyone to use.

Any form of contributions, including suggestions, bug reports, corrections, and feature requests, are welcome.

Info: Samsung’s One UI apps are created using heavily modified versions of some [Android Jetpack](https://github.com/androidx/androidx) and [Material Components for Android](https://github.com/material-components/material-components-android) libraries. These include (but are not limited to) custom themes/styles, custom implementations, and additional APIs. These are internally referenced as SESL. Samsung also added its own androidx modules.

## Available modules (as GithubPackages)
#### SESL6(OneUI 6) Android Jetpack
- [sesl.androidx.core:core](https://github.com/tribalfs/sesl-androidx/packages/2110024)
- [sesl.androidx.core:core-ktx](https://github.com/tribalfs/sesl-androidx/packages/2110025)
- [sesl.androidx.customview:customview](https://github.com/tribalfs/sesl-androidx/packages/2110026)
- [sesl.androidx.drawerlayout:drawerlayout](https://github.com/tribalfs/sesl-androidx/packages/2110027)
- [sesl.androidx.viewpager:viewpager](https://github.com/tribalfs/sesl-androidx/packages/2110037)
- [sesl.androidx.swiperefreshlayout:swiperefreshlayout](https://github.com/tribalfs/sesl-androidx/packages/2110035)
- [sesl.androidx.coordinatorlayout:coordinatorlayout](https://github.com/tribalfs/sesl-androidx/packages/2110023)
- [sesl.androidx.fragment:fragment](https://github.com/tribalfs/sesl-androidx/packages/2110028)
- [sesl.androidx.recyclerview:recyclerview](https://github.com/tribalfs/sesl-androidx/packages/2110033)
- [sesl.androidx.appcompat:appcompat](https://github.com/tribalfs/sesl-androidx/packages/2110021)
- [sesl.androidx.viewpager2:viewpager2](https://github.com/tribalfs/sesl-androidx/packages/2110041)
- [sesl.androidx.slidingpanelayout:slidingpanelayout](https://github.com/tribalfs/sesl-androidx/packages/2110034)
- [sesl.androidx.preference:preference](https://github.com/tribalfs/sesl-androidx/packages/2110032)
#### SESL6(OneUI 6) Samsung
- [sesl.androidx.indexscroll:indexscroll](https://github.com/tribalfs/sesl-androidx/packages/2110029)
- [sesl.androidx.picker:picker-basic](https://github.com/tribalfs/sesl-androidx/packages/2110030)
- [sesl.androidx.picker:picker-color](https://github.com/tribalfs/sesl-androidx/packages/2110031)
- [sesl.androidx.apppickerview:apppickerview](https://github.com/tribalfs/sesl-androidx/packages/2110022)

#### These modules are intended for use together with [sesl-material-components-android](https://github.com/tribalfs/sesl-material-components-android?tab=readme-ov-file#sesloneui-material-components-for-android-unofficial) library.

### Group id and versioning scheme
In order to provide direct information about the equivalent official Android Jetpack module and the applied SESL version, $`\textcolor{orange}{\text{sesl.}}`$ is prepended to the existing group id. 
We also apply a versioning scheme composed of the $`\textcolor{green}{\text{[Android Jetpack module version]}}`$, $`\textcolor{yellow}{\text{[SESL version]}}`$, and $`\textcolor{skyblue}{\text{[internal version]}}`$:

Example:

> $`\textcolor{orange}{\text{sesl.}}`$androidx.drawerlayout:drawerlayout:$`\textcolor{green}{\text{1.2.0}}`$+$`\textcolor{yellow}{\text{1.0.1-sesl6}}`$+$`\textcolor{skyblue}{\text{rev0}}`$
>
> This means that the module is based on _androidx.drawerlayout:drawerlayout:1.2.0_ modified based on Samsung's _androidx.drawerlayout:drawerlayout.1.0.1-sesl6_. _rev0_ will be incremented if there are bug fixing releases.


## Usage
To use these libraries in your project, set compileSdk to at least 34 and use Java 8(1.8) or higher. Then:
#### 1. Depending on your app's setup, add the following either to _allprojects_ section of the project-level _build.gradle(.kts)_ or to the _dependencyResolutionManagement_ section of _settings.gradle(.kts)_. This will authenticate to the GitHub Packages registry using a _personal access token_ with at least `read:packages` scope. If you don’t have a personal access token yet, you can [generate one](https://github.com/settings/tokens/new).
```
repositories {
    maven {
      url = uri("https://maven.pkg.github.com/tribalfs/sesl-androidx")
      credentials {
          username = "your_gh_username"
          password = "your_gh_access_token"
      }
   } 
   maven {
      url = uri("https://maven.pkg.github.com/tribalfs/sesl-material-components-android")
      credentials {
          username = "your_gh_username"
          password = "your_gh_access_token"
      }
   } 
}
``` 
_Note: If you are sharing your project, it's best to save these credentials in a separate file that's excluded from version control and expose them as project properties. Alternatively, you can save and access them as system env. variables._

#### 2. Add the specific modules you need to the _dependencies_ section of the app-level _build.gradle(.kts)_'s (replacing the equivalent Android Jetpack module, if any)

Example:
##### Groovy
 ```groovy
dependencies {
  //sesl androidx
  implementation "sesl.androidx.appcompat:appcompat:1.7.0-alpha03+1.0.28-sesl6+rev0"
  implementation "sesl.androidx.fragment:fragment:1.7.0-beta01+1.0.0-sesl6+rev0"
  //sesl material
  implementation "sesl.com.google.android.material.material:1.12.0-beta01+1.0.18-sesl6+rev0"
}
```

##### Kotlin DSL
```kotlin
dependencies {
  //sesl androidx
  implementation("sesl.androidx.appcompat:appcompat:1.7.0-alpha03+1.0.28-sesl6+rev0")
  implementation("sesl.androidx.fragment:fragment:1.7.0-beta01+1.0.0-sesl6+rev0")
  //sesl material
  implementation ("sesl.com.google.android.material.material:1.12.0-beta01+1.0.18-sesl6+rev0")
}
```

#### 2. Exclude implementations of the equivalent official Android Jetpack and Material Components for Android modules in order avoid duplicate class errors when compiling your app by adding the following in the app-level _build.gradle(.kts)_:
##### Groovy
```
configurations.implementation {
    exclude (group:"androidx.core",  module:"core")
    exclude (group:"androidx.core",  module:"core-ktx")
    exclude (group:"androidx.customview",  module:"customview")
    exclude (group:"androidx.coordinatorlayout",  module:"coordinatorlayout")
    exclude (group:"androidx.drawerlayout",  module:"drawerlayout")
    exclude (group:"androidx.viewpager2",  module:"viewpager2")
    exclude (group:"androidx.viewpager",  module:"viewpager")
    exclude (group:"androidx.appcompat", module:"appcompat")
    exclude (group:"androidx.fragment", module:"fragment")
    exclude (group:"androidx.preference",  module:"preference")
    exclude (group:"androidx.recyclerview", module:"recyclerview")
    exclude (group:"androidx.slidingpanelayout",  module:"slidingpanelayout")
    exclude (group:"androidx.swiperefreshlayout",  module:"swiperefreshlayout")
    exclude (group:"com.google.android.material.component", module: "material")
}
```
##### Kotlin DSL
```
configurations.implementation {
    exclude ("androidx.core",  "core")
    exclude ("androidx.core",  "core-ktx")
    exclude ("androidx.customview",  "customview")
    exclude ("androidx.coordinatorlayout",  "coordinatorlayout")
    exclude ("androidx.drawerlayout",  "drawerlayout")
    exclude ("androidx.viewpager2",  "viewpager2")
    exclude ("androidx.viewpager",  "viewpager")
    exclude ("androidx.appcompat", "appcompat")
    exclude ("androidx.fragment", "fragment")
    exclude ("androidx.preference",  "preference")
    exclude ("androidx.recyclerview", "recyclerview")
    exclude ("androidx.slidingpanelayout",  "slidingpanelayout")
    exclude ("androidx.swiperefreshlayout",  "swiperefreshlayout")
    exclude ("com.google.android.material.component", "material")
}
```

## [Sample app](https://github.com/tribalfs/oneui-design-sampleapp#one-ui-sample-app-using-sesl6-modules)

## [Android studio layout editor preview fix](https://github.com/tribalfs/android-studio-sec-fonts#android-studio-sec-fonts)


## More info
- About Android JetPack

  Jetpack is a suite of libraries, tools, and guidance to help developers write high-quality apps easier. These components help you follow best practices, free you from writing boilerplate code, and simplify complex tasks, so you can focus on the code you care about.

  Jetpack comprises the `androidx.*` package libraries, unbundled from the platform APIs. This means that it offers backward compatibility and is updated more frequently than the Android platform, making sure you always have access to the latest and greatest versions of the Jetpack components.

  Android JetPack official AARs and JARs binaries are distributed through [Google Maven](https://maven.google.com).

  You can learn more about using it from [Android Jetpack landing page](https://developer.android.com/jetpack).
- [Material Components](https://material.io/components?platform=android)
- [One UI design guidelines](https://developer.samsung.com/one-ui/index.html)

## Credits
- [Google](https://developer.android.com/jetpack) for their Jetpack and Material Components libraries.
- [Samsung](https://www.samsung.com/) for their awesome OneUI Design.
- [Yanndroid](https://github.com/Yanndroid) and BlackMesa123 for their [OneUI4 sesl library](https://github.com/OneUIProject/oneui-core) that highly inspired this project. Some commits are straightly cherry-picked from this project.

