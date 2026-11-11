plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.rynekryz.sniffsniff.core.database"
}
dependencies {
    api(project(":core:common"))
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
}
