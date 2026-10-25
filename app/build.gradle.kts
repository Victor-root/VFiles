/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

import com.android.build.api.variant.impl.VariantOutputImpl
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "me.zhanghai.android.files"
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    ndkVersion = "30.0.16248370"
    defaultConfig {
        applicationId = "fr.vroot.vfiles"
        minSdk = 26
        // Not supporting foreground service timeout yet.
        //noinspection OldTargetApi
        targetSdk = 34
        versionCode = 44
        versionName = "1.8.4"
        resValue("string", "app_version", "$versionName ($versionCode)")
        buildConfigField(
            "String", "FILE_PROVIDIER_AUTHORITY", "APPLICATION_ID + \".file_provider\""
        )
        buildConfigField(
            "String", "DOCUMENTS_PROVIDER_AUTHORITY", "APPLICATION_ID + \".documents\""
        )
        resValue("string", "app_provider_authority", "$applicationId.app_provider")
        resValue("string", "file_provider_authority", "$applicationId.file_provider")
        resValue("string", "documents_provider_authority", "$applicationId.documents")
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=none"
            }
        }
    }
    buildFeatures {
        aidl = true
        buildConfig = true
        compose = true
        resValues = true
        viewBinding = true
    }
    androidResources {
        generateLocaleConfig = true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    lint {
        // For "Invalid package reference in library; not included in Android: javax.security.sasl.
        // Referenced from org.apache.mina.proxy.ProxyAuthException."
        warning += setOf("InvalidPackage", "MissingTranslation")
    }
    signingConfigs {
        create("release") {
            val keystoreProperties = rootProject.file("keystore.properties")
                .takeIf { it.exists() }
                ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }
            fun getString(propertyName: String, environmentName: String, prompt: String): String? =
                keystoreProperties?.getProperty(propertyName)
                    ?: System.getenv(environmentName)
                    ?: System.console()?.readLine("\n$prompt: ")

            storeFile = getString("storeFile", "STORE_FILE", "Store file")?.let(rootProject::file)
            storePassword = getString("storePassword", "STORE_PASSWORD", "Store password")
            keyAlias = getString("keyAlias", "KEY_ALIAS", "Key alias")
            keyPassword = getString("keyPassword", "KEY_PASSWORD", "Key password")
        }
    }
    buildTypes {
        debug {
            // Sign debug builds (the green-triangle install) with the release key too, when
            // keystore.properties is present, so they share a signature with the published release
            // APK. Without that shared signature you can't install one over the other without
            // uninstalling first. Guarded so builds without keystore.properties are unaffected.
            if (rootProject.file("keystore.properties").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "org/bouncycastle/pqc/crypto/picnic/*")
        }
    }
    bundle {
        language {
            enableSplit = false
        }
    }

    // Opt-in "systemPicker" flavor for ROM integrators (see docs/systempicker-integration.md).
    //
    // Enabled only when the build is invoked with -PsystemPicker; without that property NO product
    // flavor is registered, so the standard build is completely unchanged - same "assembleRelease"
    // task, same output directory, same merged manifest (no MANAGE_DOCUMENTS), same BuildConfig.
    //
    // The flavor's sole build-time effect is merging app/src/systemPicker/AndroidManifest.xml
    // (which adds the MANAGE_DOCUMENTS permission). The URI-emission code lives in the main source
    // set and is gated at runtime on actually holding that permission, so it stays inert unless the
    // flavor build is signed with the platform key.
    if (project.hasProperty("systemPicker")) {
        flavorDimensions += "documents"
        productFlavors {
            create("systemPicker") {
                dimension = "documents"
            }
        }
    }
}

kotlin {
    compilerOptions {
        // Most of the Material 3 app bar and menu APIs are still experimental.
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

// Always name the assembled APK "VFiles-v<versionName>.apk" so the file name carries the current
// version. This is the name users see on the GitHub Releases page.
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            (output as? VariantOutputImpl)?.outputFileName
                ?.set("VFiles-v${android.defaultConfig.versionName}.apk")
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.material)

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.runtime.livedata)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.material.color.utilities)

    implementation(libs.androidsvg)
    implementation(libs.bouncycastle.bcpkix)
    implementation(libs.bouncycastle.bcprov)
    implementation(platform(libs.coil.bom))
    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.coil.video)
    implementation(libs.commons.net)
    implementation(libs.dav4jvm) {
        exclude(group = "org.ogce", module = "xpp3")
    }
    implementation(libs.dcerpc) {
        // SMB-RPC depends on the JRE flavor of Guava which targets Java 8.
        exclude(group = "com.google.guava", module = "guava")
        // Prevent promotion of SMBJ version.
        exclude(group = "com.hierynomus", module = "smbj")
        exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
    }
    implementation(libs.drakeet.drawer)
    implementation(libs.fastscroll)
    implementation(libs.ftpserver.core)
    implementation(libs.guava)
    implementation(libs.guava.listenablefuture)
    implementation(libs.insetter)
    implementation(libs.jcifs.ng) {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
    }
    implementation(libs.libsu.service)
    implementation(libs.licensesdialog)
    implementation(libs.materialshadowninepatch)
    implementation(libs.advrecyclerview)
    // Also a dependency of org.apache.ftpserver:ftpserver-core, upgraded for its bug fixes.
    implementation(libs.mina.core)
    implementation(libs.photoview)
    implementation(libs.preferencex)
    implementation(libs.shizuku.api)
    implementation(libs.simplemenu.preference)
    // Also a dependency of jCIFS-NG.
    implementation(libs.slf4j.android)
    implementation(libs.smbj) {
        // Use the JDK 1.5 to 1.8 flavor of Bouncy Castle like the rest of the app, so that there's
        // only one copy of it.
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    }
    implementation(libs.speed.dial)
    implementation(libs.sshj) {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
        exclude(group = "org.bouncycastle", module = "bcpkix-jdk18on")
    }
    implementation(libs.subsampling.scale.image.view)
    implementation(libs.zhanghai.appiconloader)
    implementation(libs.zhanghai.foregroundcompat)
    implementation(libs.zhanghai.libarchive)
    implementation(libs.zhanghai.libselinux)
    implementation(libs.zhanghai.retrofile)
    implementation(libs.zhanghai.systemuihelper)

    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
