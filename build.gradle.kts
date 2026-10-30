@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.zerostudio.idea.treesitter.BuildTreeSitterTask
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import com.zerostudio.idea.treesitter.CleanTreeSitterBuildTask
import com.zerostudio.idea.treesitter.projectVersionCode
import org.gradle.plugins.signing.SigningExtension

buildscript {
  dependencies {
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
  }
}

@Suppress("DSL_SCOPE_VIOLATION") plugins {
  id("build-logic.root-project")
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
    group = "io.github.msmt2018"
    version = rootProject.version.toString().removePrefix("v")

    plugins.withId("com.android.library") {
      extensions.configure<LibraryExtension> {
        publishing {
          singleVariant("release") {
            withSourcesJar()
            // 🔹 补全中央仓库强制要求的 Javadoc（生成空包或标准包均可，中央仓库强制校验其存在）
            withJavadocJar()
          }
        }
      }
    }

    // java-library 模块（如 annotations）也需要显式补全源码和文档生成
    plugins.withId("java-library") {
      extensions.configure<JavaPluginExtension> {
        withSourcesJar()
        withJavadocJar()
      }
    }

    plugins.withId("maven-publish") {
      // 🔹 引入并配置中央仓库必须的 GPG 签名插件
      apply(plugin = "signing")

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
                    name.set("GNU Lesser General Public License v3.0")
                    url.set("https://www.gnu.org/licenses/gpl-3.0.html#license-text")
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
            // 🔹 将原本推送到 GitHub 的目标替换为新版中央仓库 Portal 的发布 API 入口
            maven {
              name = "MavenCentral"
              url = uri("https://sonatype.com")
              credentials {
                username = providers.environmentVariable("MAVEN_USERNAME").orNull
                password = providers.environmentVariable("MAVEN_PASSWORD").orNull
              }
            }
          }
        }

        // 🔹 配置自动读取环境变量中的 GPG 私钥并对当前 release publication 执行签名
        extensions.configure<SigningExtension> {
          val signingKey = providers.environmentVariable("GPG_SIGNING_KEY").orNull
          val signingPassword = providers.environmentVariable("GPG_PASSWORD").orNull
          if (signingKey != null && signingPassword != null) {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(extensions.getByType<PublishingExtension>().publications["release"])
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

    ndkVersion = "24.0.8215888"

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
