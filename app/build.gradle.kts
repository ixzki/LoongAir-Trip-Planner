import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun propOrEnv(key: String): String? =
    (localProps.getProperty(key) ?: System.getenv(key))?.takeIf { it.isNotBlank() }

val unsignedRelease = providers.gradleProperty("allowUnsignedRelease").orNull?.toBooleanStrict() ?: false
val keystorePath = propOrEnv("LOONGAIR_KEYSTORE_PATH")
val keystorePassword = propOrEnv("LOONGAIR_KEYSTORE_PASSWORD")
val releaseKeyAlias = propOrEnv("LOONGAIR_KEY_ALIAS")
val releaseKeyPassword = propOrEnv("LOONGAIR_KEY_PASSWORD")
val pinnedCertificate = propOrEnv("LOONGAIR_RELEASE_CERT_SHA256")
val releaseStore = keystorePath?.let { file(it) }

val verifyReleaseSigningInputs = tasks.register("verifyReleaseSigningInputs") {
    onlyIf { !unsignedRelease }
    doLast {
        check(releaseStore?.isFile == true && keystorePassword != null &&
            releaseKeyAlias != null && releaseKeyPassword != null && pinnedCertificate != null) {
            "正式包需要 LOONGAIR_KEYSTORE_PATH、LOONGAIR_KEYSTORE_PASSWORD、LOONGAIR_KEY_ALIAS、" +
                "LOONGAIR_KEY_PASSWORD 和 LOONGAIR_RELEASE_CERT_SHA256。验证 R8 构建请传 -PallowUnsignedRelease=true。"
        }
        val pin = pinnedCertificate.replace(":", "").trim()
        check(pin.matches(Regex("[a-fA-F0-9]{64}"))) { "LOONGAIR_RELEASE_CERT_SHA256 必须是 64 位十六进制指纹。" }
        val store = KeyStore.getInstance(releaseStore!!, keystorePassword.toCharArray())
        check(store.isKeyEntry(releaseKeyAlias)) { "所选 alias 缺少发布私钥。" }
        val cert = store.getCertificate(releaseKeyAlias)
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString("") { "%02x".format(it) }
        check(digest.equals(pin, ignoreCase = true)) { "发布密钥的证书与固定 SHA-256 指纹不匹配。" }
    }
}

android {
    namespace = "com.loongair.tripplanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.loongair.tripplanner"
        minSdk = 26
        targetSdk = 35
        versionCode = 20
        versionName = "1.8.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    applicationVariants.configureEach {
        val buildTypeName = buildType.name
        val verName = versionName
        outputs.configureEach {
            val out = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val suffix = if (buildTypeName == "release" && unsignedRelease) "release-unsigned" else buildTypeName
            out?.outputFileName = "ChangfeiXing-v${verName}-${suffix}.apk"
        }
    }

    signingConfigs {
        if (!unsignedRelease && releaseStore?.isFile == true) {
            create("release") {
                storeFile = releaseStore
                storePassword = keystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
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
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.matching { it.name in setOf("packageRelease", "packageReleaseBundle", "signReleaseBundle") }.configureEach {
    dependsOn(verifyReleaseSigningInputs)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

// 防止同时构建 Debug + Release 时两个 KSP 任务并发读写同一个 Room Schema 文件
tasks.matching { it.name == "kspReleaseKotlin" }.configureEach {
    mustRunAfter(tasks.matching { t -> t.name == "kspDebugKotlin" })
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
