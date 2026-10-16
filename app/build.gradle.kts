import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasReleaseSigning = keystorePropertiesFile.exists()
if (hasReleaseSigning) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.tmm.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tmm.mobile"
        minSdk = 26
        targetSdk = 37
        versionCode = 31
        versionName = "1.13.3"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Meme cle pour debug (installation par cable depuis Android Studio) et release
        // (APK GitHub) : les deux se mettent a jour l'une l'autre sans desinstaller.
        debug {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                // Les 512 Mo par defaut ne suffisent plus a la suite Compose/Robolectric (OOM).
                it.maxHeapSize = "2g"
                // Sans le plugin Gradle Roborazzi, captureRoboImage n'ecrit rien : les captures se
                // produisent avec -Proborazzi.test.record=true (dans app/build/outputs/roborazzi/).
                providers.gradleProperty("roborazzi.test.record").orNull?.let { v ->
                    it.systemProperty("roborazzi.test.record", v)
                }
                // ponytail: si user.home contient un espace, la resolution des jars natifs
                // Robolectric casse (bug de decodage %20 cote Windows/JVM). On redirige alors
                // le cache Robolectric vers un chemin sans espace, cree a la volee.
                val actualHome = System.getProperty("user.home")
                if (actualHome.contains(" ")) {
                    val safeHome = File(System.getenv("ProgramData") ?: System.getProperty("java.io.tmpdir"), "tmm-robolectric-home")
                    safeHome.mkdirs()
                    it.systemProperty("user.home", safeHome.absolutePath)
                }
                // ponytail: Robolectric reflete dans des paquets JDK internes ; sur les JDK
                // recents (module system strict), il faut ouvrir explicitement ces paquets.
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
                    "--enable-native-access=ALL-UNNAMED",
                )
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.okhttp)
    implementation(libs.play.services.auth)
    implementation(libs.work.runtime.ktx)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    // ponytail: work-testing est publie pour androidTest, mais c'est un jar JVM ordinaire
    // (TestListenableWorkerBuilder, WorkManagerTestInitHelper) qui fonctionne tel quel sous
    // Robolectric en testImplementation, comme le reste de la suite de tests de ce module.
    testImplementation(libs.work.testing)
}
