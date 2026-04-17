plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "org.lightningdevkit.ldkserver.remote"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.lightningdevkit.ldkserver.remote"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Restrict packaged native libs to the three ABIs we cross-compile the Rust
        // client for. Without this, JNA's AAR would drag its mips/armeabi/x86 libs
        // into the APK (only jnidispatch, not our lib, so they'd crash at runtime
        // anyway on those ABIs).
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") {
            // UniFFI-generated Kotlin bindings are written into src/main/kotlin/ by the
            // sync_bindings.sh script; include that tree alongside src/main/java.
            java.srcDirs("src/main/java", "src/main/kotlin")
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // UniFFI-generated bindings require JNA at runtime. The @aar classifier pulls in
    // prebuilt native libs alongside the Java classes so we don't have to manage them.
    implementation("${libs.jna.get().module}:${libs.versions.jna.get()}@aar")

    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Task to regenerate the UniFFI bindings from the sibling ../ldk-server repo.
// Expects ANDROID_NDK_ROOT + cargo-ndk to be available; invokes the helper script.
tasks.register<Exec>("syncBindings") {
    group = "build"
    description = "Regenerates UniFFI Kotlin bindings + native libraries from ../../ldk-server."
    workingDir = rootProject.projectDir.parentFile.parentFile
        .resolve("ldk-server")
    commandLine(
        "bash",
        "scripts/uniffi_bindgen_generate_kotlin_android.sh",
    )
    environment(
        "OUT_DIR",
        rootProject.projectDir.resolve("app/src/main").absolutePath,
    )
}
