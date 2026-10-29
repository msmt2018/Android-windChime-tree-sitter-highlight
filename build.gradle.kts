/*
 *  This file is part of android-tree-sitter.
 *
 *  android-tree-sitter library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  android-tree-sitter library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *  along with android-tree-sitter.  If not, see <https://www.gnu.org/licenses/>.
 */

@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.zerostudio.idea.treesitter.BuildTreeSitterTask
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import com.zerostudio.idea.treesitter.CleanTreeSitterBuildTask
import com.zerostudio.idea.treesitter.projectVersionCode


// @Suppress("DSL_SCOPE_VIOLATION") 
plugins {
  id("build-logic.root-project")
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin) apply false
}


// Android uses the leading 'v'; Maven publications remove it.
val releaseVersion = providers.gradleProperty("releaseVersion").orElse("1.0.0")
version = "v${releaseVersion.get().removePrefix("v")}" 

fun Project.githubPackagesOwner(): String =
  providers.gradleProperty("gpr.owner")
    .orElse(providers.environmentVariable("GITHUB_REPOSITORY_OWNER"))
    .orElse("YOUR_GITHUB_OWNER")
    .get()

fun Project.githubPackagesRepository(): String =
  providers.gradleProperty("gpr.repository")
    .orElse(providers.environmentVariable("GITHUB_REPOSITORY").map { it.substringAfter('/') })
    .orElse("android-tree-sitter")
    .get()

fun Project.configureBaseExtension() {
  extensions.configure<BaseExtension> {
    compileSdkVersion(34)

    defaultConfig {
      minSdk = 21
      targetSdk = 33
      versionCode = project.projectVersionCode
      versionName = rootProject.version.toString()
    }

    compileOptions {
      sourceCompatibility = BuildConfig.JAVA_VERSION
      targetCompatibility = BuildConfig.JAVA_VERSION

      isCoreLibraryDesugaringEnabled = true
    }

    buildTypes {
      getByName("release") {
        isMinifyEnabled = false
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),
          "proguard-rules.pro")
      }
    }

    configurations.getByName("coreLibraryDesugaring").dependencies.add(
      libs.common.coreLibDesugaring.get())
  }
}

subprojects {
  plugins.withId("com.android.application") { configureBaseExtension() }
  plugins.withId("com.android.library") { configureBaseExtension() }
  plugins.withId("java-library") {
    tasks.withType(JavaCompile::class.java) {
      sourceCompatibility = BuildConfig.JAVA_VERSION.majorVersion
      targetCompatibility = BuildConfig.JAVA_VERSION.majorVersion
    }
  }
  plugins.withId("android-tree-sitter.ts") {
    configureTsModule()

    // set java library path for tests
    tasks.withType<Test> {
      systemProperty("java.library.path",
        rootProject.buildDir.resolve("host").absolutePath)
    }
  }

  val githubPackageModules = setOf(
    "android-tree-sitter",
    "annotations",
    "tree-sitter-cpp",
    "tree-sitter-groovy",
    "tree-sitter-java",
    "tree-sitter-json",
    "tree-sitter-kotlin",
    "tree-sitter-log",
    "tree-sitter-xml",
  )

  if (name in githubPackageModules) {
    group = providers.gradleProperty("gpr.group")
      .orElse("com.huiywu.androidcs.treesitter")
      .get()
    version = rootProject.version.toString().removePrefix("v")

    plugins.withId("com.android.library") {
      extensions.configure<LibraryExtension> {
        publishing {
          singleVariant("release") {
            withSourcesJar()
          }
        }
      }
    }

    plugins.withId("maven-publish") {
      afterEvaluate {
        extensions.configure<PublishingExtension> {
          publications {
            create<MavenPublication>("release") {
              artifactId = project.name
              from(components[if (plugins.hasPlugin("com.android.library")) "release" else "java"])

              pom {
                name.set(project.name)
                description.set(project.description
                  ?: "${project.name} for Android Tree-sitter")
                url.set("https://github.com/${githubPackagesOwner()}/${githubPackagesRepository()}")
                licenses {
                  license {
                    name.set("GNU Lesser General Public License v2.1")
                    url.set("https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html")
                    distribution.set("repo")
                  }
                }
                scm {
                  url.set("https://github.com/${githubPackagesOwner()}/${githubPackagesRepository()}")
                  connection.set("scm:git:https://github.com/${githubPackagesOwner()}/${githubPackagesRepository()}.git")
                }
              }
            }
          }

          repositories {
            maven {
              name = "GitHubPackages"
              url = uri("https://maven.pkg.github.com/${githubPackagesOwner()}/${githubPackagesRepository()}")
              credentials {
                username = providers.gradleProperty("gpr.user")
                  .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                  .orNull
                password = providers.gradleProperty("gpr.token")
                  .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                  .orNull
              }
            }
          }
        }
      }
    }
  }
}

tasks.register<BuildTreeSitterTask>("buildTreeSitter")

tasks.register<CleanTreeSitterBuildTask>("cleanTreeSitterBuild")

tasks.register<Delete>("clean").configure {
  dependsOn("cleanTreeSitterBuild")
  delete(rootProject.layout.buildDirectory)
  delete(rootProject.file("tree-sitter-lib/cli/build"))
}

fun Project.configureTsModule() {
  extensions.configure<BaseExtension> {
  
    packagingOptions.jniLibs.keepDebugSymbols += "**/*.so"
    
    val grammarName = project.project.name.substringAfter("tree-sitter-", "")
    if (grammarName.isNotBlank()) {
      namespace = "com.zerostudio.idea.treesitter.$grammarName"
      logger.lifecycle("Set namespace '$namespace' to $project")
    }

    ndkVersion = "29.0.14206865"

    defaultConfig {
      val rootProjDir = project.rootProject.projectDir.absolutePath
      val tsDir = "${rootProjDir}/tree-sitter-lib"

      externalNativeBuild {
        cmake {
          arguments("-DPROJECT_DIR=${rootProjDir}", "-DTS_DIR=${tsDir}")
        }
      }
    }

    externalNativeBuild {
      cmake {
        path = project.file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
      }
    }
  }

  // avoid circular dependency
  if (project.projects.androidTreeSitter.name != project.name) {
    configurations.getByName("api").dependencies.add(
      project.projects.androidTreeSitter)
  }
}
