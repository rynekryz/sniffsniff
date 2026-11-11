import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.application") version "9.3.1" apply false
    id("com.android.library") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("com.google.devtools.ksp") version "2.3.11" apply false
    id("com.google.dagger.hilt.android") version "2.59.2" apply false
}

subprojects {
    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            compileSdk = 37
            defaultConfig {
                minSdk = 31
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
        }
    }
    configurations.configureEach {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.10")
        }
    }
}
