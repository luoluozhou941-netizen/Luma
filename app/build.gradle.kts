import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 读取 local.properties 里的密码，这个文件不会进git仓库，密码不会跟着代码一起公开
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
val lumaKeystorePassword: String =
    (localProperties.getProperty("LUMA_KEYSTORE_PASSWORD")
        ?: System.getenv("LUMA_KEYSTORE_PASSWORD"))
        ?: throw GradleException(
            "没找到签名密码。请在项目根目录的 local.properties 文件里加一行：\n" +
            "LUMA_KEYSTORE_PASSWORD=你的密码"
        )

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
            // 测试版不用自己的私钥了，交给系统自带的免费测试签名，
            // 这样CI(GitHub Actions)编译的时候不用配置任何密码/密钥，直接能跑。
            // 正式发布版(release)不受影响，还是用下面那把自己的私钥。
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

    debugImplementation("androidx.compose.ui:ui-tooling")
}
