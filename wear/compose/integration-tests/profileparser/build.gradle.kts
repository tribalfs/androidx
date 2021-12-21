plugins {
    application
    kotlin("jvm")
    // Use ShadowJar plugin to build fat jar.
    id("com.github.johnrengelman.shadow")
}

application {
    mainClass.set("androidx.wear.compose.integration.profileparser.ProfileParser")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "androidx.wear.compose.integration.profileparser.ProfileParser"
    }
}
