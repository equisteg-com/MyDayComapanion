// Pure Kotlin/JVM module: all business rules, policy, planner and AI-validation logic live here
// so they are testable without an Android device and cannot touch Android APIs directly.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-test-fixtures`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    testFixturesApi(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    // Golden AI-evaluation data lives at the repository root so it is shared across modules.
    systemProperty("mdc.testdata.dir", file("../tests/testdata").absolutePath)
}
