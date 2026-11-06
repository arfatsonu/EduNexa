plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services") apply false
}

val campusConfig = file("google-services.json")
val campusFirebaseConfigured = campusConfig.exists()
if (campusFirebaseConfigured) {
    val config = groovy.json.JsonSlurper().parse(campusConfig) as Map<*, *>
    val project = config["project_info"] as? Map<*, *> ?: error("Firebase project information missing.")
    check(project["project_id"] != "al-nisa-school-portal") {
        "EduNexa must never connect to Al-Nisa's production Firebase project."
    }
    check(!project["project_id"]?.toString().isNullOrBlank()) { "Firebase project ID required." }
    val clients = config["client"] as? List<*> ?: emptyList<Any>()
    check(clients.any { client ->
        val info = (client as? Map<*, *>)?.get("client_info") as? Map<*, *>
        val androidInfo = info?.get("android_client_info") as? Map<*, *>
        androidInfo?.get("package_name") == "pk.edunexa.app"
    }) { "Register the pk.edunexa.app Android app in the separate Firebase project." }
    apply(plugin = "com.google.gms.google-services")
}

// Release keys stay outside source control. Configure all four values together.
fun uploadSetting(name: String): String? =
    providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull

val uploadStoreFile = uploadSetting("EDUNEXA_UPLOAD_STORE_FILE")
val uploadStorePassword = uploadSetting("EDUNEXA_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = uploadSetting("EDUNEXA_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = uploadSetting("EDUNEXA_UPLOAD_KEY_PASSWORD")
val uploadSettings = listOf(uploadStoreFile, uploadStorePassword, uploadKeyAlias, uploadKeyPassword)
val uploadSigningConfigured = uploadSettings.all { !it.isNullOrBlank() }
check(uploadSettings.all { it.isNullOrBlank() } || uploadSigningConfigured) {
    "Configure all four EDUNEXA_UPLOAD_* settings or leave all four unset."
}

android {
    namespace = "pk.edunexa.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "pk.edunexa.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 23
        versionName = "0.4.0"
        multiDexEnabled = true
        buildConfigField("boolean", "BACKEND_CONFIGURED", campusFirebaseConfigured.toString())
        val activityPush = providers.gradleProperty("EDUNEXA_ACTIVITY_PUSH_ENABLED").orElse("true").get()
        check(activityPush in listOf("true", "false")) { "EDUNEXA_ACTIVITY_PUSH_ENABLED must be true or false." }
        buildConfigField("boolean", "ACTIVITY_PUSH_ENABLED", activityPush)
    }

    signingConfigs {
        if (uploadSigningConfigured) {
            create("upload") {
                storeFile = rootProject.file(requireNotNull(uploadStoreFile))
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
        create("campusDebug") {
            storeFile = rootProject.file("keystore/a2-campus-debug.keystore")
            storePassword = "android"
            keyAlias = "campus-debug"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("campusDebug")
        }
        release {
            isDebuggable = false
            if (uploadSigningConfigured) {
                signingConfig = signingConfigs.getByName("upload")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")

    implementation("androidx.compose.runtime:runtime:1.11.4")
    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")
    implementation("androidx.compose.animation:animation:1.11.4")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation(platform("com.google.firebase:firebase-bom:35.0.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    implementation("com.vanniktech:android-image-cropper:4.7.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")

    debugImplementation("androidx.compose.ui:ui-tooling:1.11.4")
}





