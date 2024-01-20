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
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
dependencies {
    implementation("androidx.annotation:annotation-jvm:1.7.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}

publishing {
    publications {

        create<MavenPublication>("mavenAar") {
            groupId = "top.greatfeng"
            artifactId = "oksharedpreferences"
            version = "1.0.0"
//            artifact("$buildDir/outputs/aar/OkSharedPreferences-release.aar")
            // 发布 AAR 文件
            signing {
                sign(publishing.publications["mavenAar"])

            }
            afterEvaluate {
                from(components["release"])
            }

            // 配置 POM 文件
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
                    connection.set("scm:git:https://github.com/changchengfeng/OkSharedPreferences.git")
                    developerConnection.set("scm:git:https://github.com/changchengfeng/OkSharedPreferences.git")
                    url.set("https://github.com/changchengfeng/OkSharedPreferences")
                }

                // 设置签名
                withXml {
                    asNode().appendNode("properties")
                        .appendNode("gpg.keyname", "95DC60737E87C11CAC959A8944910AE317A4EB70")
                }
            }
        }
    }


    repositories {
//        maven {
//            url = uri("$buildDir/repo")
//        }
        maven {
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = findProperty("ossrhUsername") as String
                password = findProperty("ossrhPassword") as String
//                username = findProperty("username") as String
//                password = findProperty("password") as String
            }
        }


//        maven {
//            isAllowInsecureProtocol = true
//            url = uri("http://greatfeng.online:9081/repository/maven-releases/")
//            credentials {
//                username = findProperty("localUsername") as String
//                password = findProperty("localPassword") as String
//            }
//        }
    }


}