
plugins {
    kotlin("jvm")
    id("jps-compatible")
}

sourceSets {
    "main" { }
    "test" { projectDefault() }
}

dependencies {
    testCompile(projectTests(":idea"))
    testCompile(projectTests(":r4a-ide-plugin"))
    compile(projectTests(":generators:test-generator"))
}


projectTest {
    workingDir = rootDir
}

val generateTests by generator("org.jetbrains.kotlin.r4a.GenerateTestsKt")

testsJar()
