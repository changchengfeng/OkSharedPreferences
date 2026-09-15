plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("signing")
}

android {
    namespace = "online.greatfeng.oksharedpreferences"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags += ""
            }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:rules:1.5.0")
}

publishing {
    publications {
        create<MavenPublication>("mavenAar") {
            groupId = "online.greatfeng"
            artifactId = "oksharedpreferences"
            version = "1.1.1"
            artifact("$buildDir/outputs/aar/OkSharedPreferences-release.aar")

            pom {
                name.set("OkSharedPreferences")
                description.set("a better SharedPreferences")
                url.set("https://github.com/changchengfeng/OkSharedPreferences")

                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("changchengfeng")
                        name.set("changchengfeng")
                        email.set("changchengfeng001@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git@github.com:changchengfeng/OkSharedPreferences.git")
                    developerConnection.set("scm:git:git@github.com:changchengfeng/OkSharedPreferences.git")
                    url.set("https://github.com/changchengfeng/OkSharedPreferences")
                }

                withXml {
                    asNode().appendNode("properties")
                        .appendNode("gpg.keyname", "488C0CEF9C9199B767D914652BCEBA44DB93D926")
                }
            }
        }
    }

    repositories {
        maven {
            name = "local"
            url = uri("$buildDir/repo")
        }
        maven {
            name = "MavenCentral"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials {
                username = findProperty("centralPortalUsername") as String?
                    ?: findProperty("ossrhUsername") as String?
                    ?: ""
                password = findProperty("centralPortalPassword") as String?
                    ?: findProperty("ossrhPassword") as String?
                    ?: ""
            }
        }
    }
}

signing {
    val signingKeyId: String? by project
    val signingKey: String? by project
    val signingPassword: String? by project
    when {
        !signingKeyId.isNullOrBlank() && !signingKey.isNullOrBlank() -> {
            useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
            sign(publishing.publications["mavenAar"])
        }
        hasProperty("signing.secretKeyRingFile") -> {
            sign(publishing.publications["mavenAar"])
        }
    }
}

tasks.named("publishMavenAarPublicationToMavenCentralRepository") {
    dependsOn("assembleRelease")
}

