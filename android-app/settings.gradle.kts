pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // This VM cannot reach plugins.gradle.org from Java (TLS handshake hangs),
    // but Maven Central works via the egress proxy. Redirect the two Kotlin
    // plugin markers to their real Maven Central modules.
    resolutionStrategy {
        eachPlugin {
            // requested.version is null when a subproject applies the plugin
            // without a version (the version lives in the root plugins block),
            // so fall back to the pinned version.
            val version = requested.version ?: "2.0.21"
            if (requested.id.id == "org.jetbrains.kotlin.android") {
                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${version}")
            }
            if (requested.id.id == "org.jetbrains.kotlin.plugin.compose") {
                // Per the plugin marker POM on the Gradle Plugin Portal, this id
                // is implemented by compose-compiler-gradle-plugin.
                useModule("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${version}")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Prognos Labs"
include(":app")
