plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.secrets)
}

android {
    namespace = "com.fluxio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fluxio.app"
        minSdk = 23
        targetSdk = 34
        versionCode = 7
        versionName = "v0.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.template"
}

tasks.register("ensureGoogleServicesJson") {
    doFirst {
        val googleServicesFile = file("google-services.json")
        if (!googleServicesFile.exists()) {
            googleServicesFile.writeText(
                """
                {
                  "project_info": {
                    "project_number": "577398237015",
                    "firebase_url": "https://fluxio-app2026-default-rtdb.firebaseio.com",
                    "project_id": "fluxio-app2026",
                    "storage_bucket": "fluxio-app2026.firebasestorage.app"
                  },
                  "client": [
                    {
                      "client_info": {
                        "mobilesdk_app_id": "1:577398237015:android:45a9062c3718595bf88012",
                        "android_client_info": {
                          "package_name": "com.fluxio.app"
                        }
                      },
                      "oauth_client": [
                        {
                          "client_id": "577398237015-5ki0s15lv6vjqc85slg6tj84g7dokd22.apps.googleusercontent.com",
                          "client_type": 1,
                          "android_info": {
                            "package_name": "com.fluxio.app",
                            "certificate_hash": "b6fb9d29990b013c210d07a38a9e8694a55c8e93"
                          }
                        },
                        {
                          "client_id": "577398237015-n885ooqn22n622fci7cl640dlug74254.apps.googleusercontent.com",
                          "client_type": 1,
                          "android_info": {
                            "package_name": "com.fluxio.app",
                            "certificate_hash": "37924bdcec97abb251cc69142ae7ffaa1d31d89f"
                          }
                        },
                        {
                          "client_id": "577398237015-oep5qecg9l9rp87r7bso9ioj7idkauq7.apps.googleusercontent.com",
                          "client_type": 1,
                          "android_info": {
                            "package_name": "com.fluxio.app",
                            "certificate_hash": "1c2e746159c8b94e8f9114683e2958cfa2e4c0e3"
                          }
                        },
                        {
                          "client_id": "577398237015-tvtgcu30c0ti45ru9e7uqicggn9f38m0.apps.googleusercontent.com",
                          "client_type": 1,
                          "android_info": {
                            "package_name": "com.fluxio.app",
                            "certificate_hash": "0615d03a6396ff1eef3cf1534e286ee073a74b2d"
                          }
                        },
                        {
                          "client_id": "577398237015-v863l5tahtr04dm40rpasrr2ho95p4gr.apps.googleusercontent.com",
                          "client_type": 1,
                          "android_info": {
                            "package_name": "com.fluxio.app",
                            "certificate_hash": "8b5fa8bbfd700f703d32ab4305cbcadd4af735eb"
                          }
                        },
                        {
                          "client_id": "577398237015-sgrt2thd7n2oku71ermqn8tll2ncl5m5.apps.googleusercontent.com",
                          "client_type": 3
                        }
                      ],
                      "api_key": [
                        {
                          "current_key": "AIzaSyBrY82bjnLXzmUunrEKMKBS3nT2YCaUbPM"
                        }
                      ],
                      "services": {
                        "appinvite_service": {
                          "other_platform_oauth_client": [
                            {
                              "client_id": "577398237015-sgrt2thd7n2oku71ermqn8tll2ncl5m5.apps.googleusercontent.com",
                              "client_type": 3
                            }
                          ]
                        }
                      }
                    }
                  ],
                  "configuration_version": "1"
                }
                """.trimIndent()
            )
        }
    }
}

tasks.matching { it.name.startsWith("process") && it.name.endsWith("GoogleServices") }.configureEach {
    dependsOn("ensureGoogleServicesJson")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-ripple")
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.ui)
    implementation(libs.coil.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.play.services.auth)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation("junit:junit:4.13.2")
}

