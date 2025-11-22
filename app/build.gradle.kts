plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.ffddas"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.ffddas"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++11", "-frtti", "-fexceptions")
                arguments(
                    "-DOpenCV_DIR=" + project.rootDir.absolutePath + "/OpenCV/native/jni",
                    "-DANDROID_STL=c++_shared"
                )
            }
        }
        
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    packagingOptions {
        jniLibs {
            useLegacyPackaging = true // ensure .so libs packed correctly with multiple ABIs
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(project(":OpenCV"))
    
    // CameraX dependencies
    val cameraxVersion = "1.3.0"
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")
    implementation("androidx.camera:camera-extensions:${cameraxVersion}")
    
    // CardView for gallery
    implementation("androidx.cardview:cardview:1.0.0")
    
    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    
    // Glide for image loading
    implementation("com.github.bumptech.glide:glide:4.15.1")
    
    // NanoHTTPD for embedded web server
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
