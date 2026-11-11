plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.rynekryz.sniffsniff.core.common"
}
dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
}
