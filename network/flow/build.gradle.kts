plugins {
    id("com.android.library")
}
android {
    namespace = "com.rynekryz.sniffsniff.network.flow"
}
dependencies {
    api(project(":core:common"))
    api(project(":network:parser"))
}
