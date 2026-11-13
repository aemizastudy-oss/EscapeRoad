import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.team.carescape"
    compileSdk = 36
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.team.carescape"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")

    // Pleos 차량 SDK: 방향지시등·전조등·와이퍼·문·경고등 읽기, 무드등 등 제어(미검증)
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // 진행자 음성 안내 (발화는 아직 미검증)
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")

    testImplementation("junit:junit:4.13.2")
}
