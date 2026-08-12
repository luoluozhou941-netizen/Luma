import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 读取 local.properties 里的密码，这个文件不会进git仓库，密码不会跟着代码一起公开
// CI结力时加不到密码不会所错，只要 release 编译时才需要密码。
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
val lumaKeystorePassword: String =
    (localProperties.getProperty("LUMA_KEYSTORE_PASSWORD")
        ?: System.getenv("LUMA_KEYSTORE_PASSWORD"))
        ?: ""  // CI 编近时加不到密根，不要所错

android {
    namespace = "com.luoluo.luma"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.luoluo.luma"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"
    }

    signingConfigs {
        create("luma") {
            storeFile = file("luma-release.jks")
            storePassword = lumaKeystorePassword
            keyAlias = "luma"
            keyPassword = lumaKeystorePassword
        }
    }

    buildTypes {
        debug {
            // 测试版不收用自己的私查了，交给登尖系统 System Default 测诗签名，
            // 这样CI(GitHub Actions)编译的时候不用配置任何密码/密钥，直接能跑。
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("luma")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.biometric:biometric:1.1.0")

    // 1b新增：AI通信用OkHttp手写请求(含流式SSE手动解析)，用协程处理异步
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")

    // 1c新增：Room数据库，聊天记录按角色分文件夹存
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
