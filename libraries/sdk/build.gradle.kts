plugins {
    id("com.android.library")
    id("maven-publish")
    id("signing")
}

android {
    namespace = "com.fastcomments.sdk"
    resourcePrefix = "fastcomments_"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    packaging {
        resources {
            pickFirsts.add("META-INF/NOTICE.md")
            pickFirsts.add("META-INF/LICENSE.md")
        }
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.swiperefreshlayout)

    // FastComments Java client
    implementation(libs.fastcommentsCore)
    implementation(libs.fastcommentsClient)
    implementation(libs.fastcommentsPubsub)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.12.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

val releaseVersion = findProperty("releaseVersion") as String? ?: "0.0.1-SNAPSHOT"

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.fastcomments"
            artifactId = "android-sdk"
            version = releaseVersion

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("FastComments Android SDK")
                description.set("Official FastComments Android SDK")
                url.set("https://github.com/FastComments/fastcomments-android")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("fastcomments")
                        name.set("FastComments Team")
                        email.set("support@fastcomments.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/FastComments/fastcomments-android.git")
                    developerConnection.set("scm:git:ssh://github.com/FastComments/fastcomments-android.git")
                    url.set("https://github.com/FastComments/fastcomments-android")
                }
            }
        }
    }

    repositories {
        maven {
            name = "sonatype"
            url = uri(
                if (releaseVersion.endsWith("SNAPSHOT")) {
                    "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                } else {
                    "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                }
            )
            credentials {
                username = findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
                password = findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

signing {
    val signingKey: String? = findProperty("signingKey") as String? ?: System.getenv("SIGNING_KEY")
    val signingPassword: String? = findProperty("signingPassword") as String? ?: System.getenv("SIGNING_PASSWORD")
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["release"])
}