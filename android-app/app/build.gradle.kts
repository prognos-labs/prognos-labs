import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "xyz.fruitslice.prognos"
    compileSdk = 34

    defaultConfig {
        applicationId = "xyz.fruitslice.prognos"
        minSdk = 26
        targetSdk = 34
        versionCode = 28
        // v1.1.16: Whisper only, model bundled, new call screen.
        // The phone's built-in recognizer is gone: every listen
        // goes through the speech library, whose model ships
        // inside the APK, so there is no download and no wait. The
        // voice is a full-screen call view: logo at the top, one
        // big lime mic with waveform bars and "Listening...", your
        // words as a bubble on the right as you speak, the app's
        // read-back as a bubble on the left, "Say yes or no" by
        // voice, and one red hang-up button. No other buttons.
        // Everything from v1.1.15 carries over: the header mic on
        // every screen, greetings on the first word, "What is
        // Prognos Labs?" answered, "Can you hear me?" answered,
        // spoken words shown as text, no stray "Thank you.", ETH
        // rows saying "No price yet". Firebase Analytics from
        // v1.1.10 is intact.
        versionName = "1.1.18"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The speech library's native code: 64-bit and 32-bit ARM.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        externalNativeBuild {
            cmake {
                version = "3.22.1"
                cppFlags += "-O2"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    // The native library build, under app/src/main/cpp.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // v1.1.18: one stable signing key for every cloud build, restored
    // from repo secrets by the workflow. Without the properties file
    // (a local checkout) debug keeps Gradle's default debug key and
    // nothing breaks. This is what makes each new APK install over
    // the last one on the phone instead of failing with a signature
    // mismatch, which is what happened between v1.1.17 and fix1.
    signingConfigs {
        if (rootProject.file("signing.properties").exists()) {
            create("prognos") {
                val props = Properties()
                rootProject.file("signing.properties").inputStream().use { props.load(it) }
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("prognos")?.let { signingConfig = it }
        }
        release {
            signingConfigs.findByName("prognos")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        // v9: Settings > About shows the version from BuildConfig.
        buildConfig = true
    }

    // v1.1.6: Robolectric needs the merged manifest and resources
    // passed to the unit-test JVM, or it cannot resolve MainActivity
    // and every UI test fails before it starts.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.0")
    // v1.1.9: tried material-icons-core for APK size; nearly every
    // icon the app uses (Mic, History, ShowChart, PieChart,
    // AccountBalanceWallet, Visibility, StarOutline, DarkMode,
    // LightMode, SwapHoriz) is extended-only, so the extended
    // library stays. The dex is dominated by it, but there is no
    // easy win here without redrawing the icons.
    implementation("androidx.compose.material:material-icons-extended:1.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Encrypted storage for the auth token and the trading session.
    // If it cannot be created on a device, TokenStore and VoiceStore
    // keep the values in memory only and never write them to disk in
    // plain text. (The logging interceptor was removed in v1.1.8: it
    // was never wired in, and if it ever were it would log the
    // Authorization and session headers.)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // v1.1.10: Firebase Analytics (Google Analytics). The BOM pins
    // consistent library versions; only firebase-analytics is used.
    // No other Firebase product is wired in.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.code.gson:gson:2.11.0")
    // v1.1.13: a local HTTP server for the model download tests.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // v1.1.6: Robolectric + Compose UI tests, because the box has no
    // KVM and no emulator can boot on it. The voice screens render
    // and behave on the JVM instead: the mic button, the listening
    // sheet, the confirm card, the enrollment and history screens,
    // in light and dark.
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// The build box reaches the internet through a local proxy. The test
// JVM does not inherit it from the shell, so Robolectric's download
// of its android-all framework jar fails with a socket error without
// it. Read the proxy from the environment when present; when there is
// no proxy set, nothing changes.
tasks.withType<Test>().configureEach {
    System.getenv("https_proxy")?.let { proxy ->
        val hostPort = proxy.substringAfter("://", proxy).substringBefore("/")
        val host = hostPort.substringBefore(":")
        val port = hostPort.substringAfter(":", "").toIntOrNull()
        if (host.isNotEmpty() && port != null) {
            systemProperty("https.proxyHost", host)
            systemProperty("https.proxyPort", port.toString())
            systemProperty("http.proxyHost", host)
            systemProperty("http.proxyPort", port.toString())
            systemProperty("http.nonProxyHosts", "localhost|127.0.0.1")
            systemProperty("https.nonProxyHosts", "localhost|127.0.0.1")
        }
    }
}
