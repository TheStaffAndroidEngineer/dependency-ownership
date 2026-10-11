plugins { alias(libs.plugins.androidLibrary); alias(libs.plugins.kotlinCompose) }
android {
    namespace = "staff.android.ownership.ui"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    buildToolsVersion = "37.0.0"
    defaultConfig {
        minSdk = 26
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_26; targetCompatibility = JavaVersion.VERSION_26 }
}
kotlin { jvmToolchain(27); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) } }
dependencies {
    api(project(":session-api"))
    api(project(":legacy-bridge"))
    api(libs.androidxLifecycleViewModel)
    api(platform(libs.composeBom))
    api(libs.composeRuntime)
    implementation(libs.androidxActivityCompose)
    implementation(libs.androidxLifecycleRuntimeCompose)
    implementation(libs.coroutinesAndroid)
    implementation(platform(libs.composeBom))
    implementation(libs.composeUi)
    implementation(libs.composeFoundation)
    implementation(libs.composeMaterial3)
}
