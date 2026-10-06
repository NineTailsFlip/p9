import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android") version "1.9.24"
}

// Release signing is only available if keystore.properties exists (gitignored — see README/CI
// setup) with storeFile/storePassword/keyAlias/keyPassword. Without it, the release build type
// falls back to unsigned, same as before this key existed.
val keystoreProperties = Properties()
val keystorePropertiesFile = project.file("keystore.properties")
val hasKeystore = keystorePropertiesFile.exists()
if (hasKeystore) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "life.furrer.p9"
    compileSdk = 34

    defaultConfig {
        applicationId = "life.furrer.p9"
        minSdk = 34
        targetSdk = 34
        versionCode = 5
        versionName = "0.3.2"
    }

    if (hasKeystore) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                rootProject.file("proguard-rules.pro"),
            )
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Reproducible builds: don't embed the local git working-copy's VCS metadata (commit
            // hash etc.), since a from-source rebuild elsewhere wouldn't reproduce it identically.
            vcsInfo {
                include = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // AGP embeds a signed "dependency metadata" block for Google Play's dependency-transparency
    // feature by default. It's irrelevant off Play and F-Droid's scanner flags it as an
    // unexpected extra signing block, so it's disabled here.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":contacts"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation(testFixtures(project(":contacts")))
}

// Robolectric reflectively reaches into JDK internals (e.g. FileDescriptor) that JDK 17+'s module
// system blocks by default — these opens are Robolectric's own documented requirement, needed
// only for running tests, not for building the app itself.
tasks.withType<Test> {
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
    )
}
