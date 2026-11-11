plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.rynekryz.sniffsniff.network.capture"
}
dependencies {
    api(project(":core:common"))
    api(project(":network:forwarding"))
    implementation(project(":network:parser"))
    implementation(project(":network:flow"))
    implementation(project(":network:pcap"))
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
}
