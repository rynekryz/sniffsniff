plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}
android {
    namespace = "com.rynekryz.sniffsniff"
    compileSdk = 37
    androidResources {
        localeFilters += "en"
    }
    defaultConfig {
        applicationId = "com.rynekryz.sniffsniff"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.0-alpha"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
}
androidComponents {
    onVariants { variant ->
        val versionName = variant.outputs.first().versionName.get()
        variant.outputs.forEach { output ->
            val abiFilter = output.filters.find {
                it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
            }?.identifier
            val abiName = when (abiFilter) {
                "arm64-v8a" -> "arm64"
                "armeabi-v7a" -> "armv7"
                "x86_64" -> "x86_64"
                else -> abiFilter ?: "universal"
            }
            val buildTypeName = variant.buildType ?: "debug"
            output.outputFileName.set("sniffsniff-$versionName-$abiName-$buildTypeName.apk")
        }
    }
}
dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:connections"))
    implementation(project(":feature:inspector"))
    implementation(project(":feature:applications"))
    implementation(project(":feature:settings"))
    implementation(project(":network:vpn"))
    implementation(project(":network:capture"))
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha25")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.hilt:hilt-lifecycle-viewmodel-compose:1.4.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
