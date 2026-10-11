plugins { alias(libs.plugins.kotlinJvm); `java-library` }
kotlin {
    jvmToolchain(27)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) }
}
java { sourceCompatibility = JavaVersion.VERSION_26; targetCompatibility = JavaVersion.VERSION_26 }
tasks.withType<JavaCompile>().configureEach { options.release.set(26) }
dependencies {
    api(project(":session-api"))
    api(libs.coroutinesCore)
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
tasks.test { useJUnitPlatform() }
