import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import groovy.json.JsonSlurper
import java.io.File

plugins {
    id("com.android.library")
}

android {
    namespace = "com.zerostudio.idea.treesitter"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()
    ndkVersion = libs.versions.ndk.get()
    
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        
        externalNativeBuild {
            cmake {
                abiFilters("arm64-v8a")
                
                arguments(
                    "-DANDROID_APP_PLATFORM=android-24",
                    "-DANDROID_STL=c++_static",
                    "-DCMAKE_BUILD_TYPE=Release"
                )
                cFlags(
                    "-O3",
                    "-std=c11",
                    "-D_BSD_SOURCE", 
                    "-D_GNU_SOURCE",
                    "-fvisibility=hidden",
                    "-flto",
                    "-fcolor-diagnostics"
                )
                cppFlags(
                    "-O3",
                    "-std=c++20",
                    "-fvisibility=hidden",
                    "-fvisibility-inlines-hidden",
                    "-fno-rtti",
                    "-fno-exceptions",
                    "-flto"
                )
            }
        }
    }
    
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")          
            version = libs.versions.cmake.get()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    
	buildFeatures {
        viewBinding = true
    }
    
    // clone the treesitter grammars repositories

tasks.register("gitCloneTreeSitter") {
    doLast {
        val reposFile = file("repos.json")
        if (!reposFile.exists()) return@doLast

        val parser = JsonSlurper()
        @Suppress("UNCHECKED_CAST")
        val repos = parser.parse(reposFile) as List<Map<String, String>>
        val destDir = file("src/main/cpp")

        repos.forEach { proj ->
            val name = proj["name"] ?: ""
            val url = proj["url"] ?: ""
            val targetDir = File(destDir, name)

            if (!targetDir.exists() && name.isNotEmpty() && url.isNotEmpty()) {
                println("Cloning via Git (note whether the client has Git installed): $name...")
                
                providers.exec {
                    commandLine("git", "clone", "--depth", "1", url, targetDir.absolutePath)
                }.result.get() // 阻塞直到克隆成功
                
            } else if (targetDir.exists()) {
                println("$name Already exists, skip cloning.")
            }
        }
    }
}

// 挂载到 preBuild 生命周期
tasks.matching { it.name == "preBuild" }.all {
    dependsOn("gitCloneTreeSitter")
}



}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    // Use the kotlin reflect
    implementation(libs.kotlin.reflect)
    // Use the Kotlin JUnit 5 integration.
    testImplementation(libs.kotlin.test.junit5)
}

// unit tests run on JUnit 5 (kotlin.test's junit5 flavour): without this Gradle finds none
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
