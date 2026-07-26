plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
dependencies {
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.mlkit:image-labeling:17.0.9")
}
