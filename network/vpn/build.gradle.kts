plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}
android {
    namespace = "com.rynekryz.sniffsniff.network.vpn"
}
dependencies {
    implementation(project(":core:common"))
    implementation(project(":network:capture"))
    implementation(project(":network:forwarding"))
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
}
