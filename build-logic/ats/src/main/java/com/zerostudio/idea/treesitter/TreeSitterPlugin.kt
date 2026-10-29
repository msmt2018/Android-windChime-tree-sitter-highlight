package com.zerostudio.idea.treesitter

import com.android.build.api.artifact.SingleArtifact.MERGED_NATIVE_LIBS
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.gradle.internal.tasks.MergeNativeLibsTask
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.tasks.ExternalNativeBuildTask
import com.zerostudio.idea.treesitter.jni.GenerateNativeHeadersTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import java.io.File
import java.util.Locale

/**
 * Marker plugin.
 *
 * @author Akash Yadav
 * @author android_zero（github:msmt2018）已升级适配 Gradle 9.8 + AGP 9.4+ (彻底扫除 CommonExtension / SourceSets 编译阻碍)
 */
class TreeSitterPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.run {
      val cppDir = project.file("src/main/cpp")
      val nativeHeadersDir =
        project.layout.buildDirectory.dir("generated/native_headers")

      val buildForHost =
        tasks.register("buildForHost", BuildForHostTask::class.java) {
          // Build tree-sitter library first
          dependsOn(rootProject.tasks.getByName("buildTreeSitter"))

          // Generate the tree sitter parser from grammar.js
          tasks.findByName("generateTreeSitterGrammar")?.also { dependsOn(it) }

          this.cppDir.set(cppDir)
          this.outputFile.set(BuildForHostTask.getOutputFile(project).second)
        }

      val cleanHostBuild =
        tasks.register("cleanHostBuild", Delete::class.java) {
          delete(cppDir.resolve("host-build"))
        }

      tasks.named("clean") {
        dependsOn(cleanHostBuild)
      }

      tasks.withType(ExternalNativeBuildTask::class.java) {
        dependsOn(buildForHost)
      }

      tasks.withType(Test::class.java) {
        dependsOn(buildForHost)

        if (!project.name.startsWith("tree-sitter-")) {
          rootProject.subprojects.filter {
            it.name.startsWith("tree-sitter-")
          }.forEach { grammarProject ->
            dependsOn(
              grammarProject.tasks.withType(BuildForHostTask::class.java))
          }
        }
      }

      // ✅ 修复 1: 移除由于 AGP 9.4 泛型重构导致的过时形参报错
      val androidExtension = extensions.getByType(CommonExtension::class.java)

      androidExtension.defaultConfig.externalNativeBuild.cmake.arguments(
        "-DAUTOGEN_HEADERS=${nativeHeadersDir.get().asFile.absolutePath}")

      // ✅ 修复 2: 替代旧的 ndkDirectory 属性。利用组件库公开的 sdkComponents 拿到标准的 ndkDirectory Provider
      val androidComponents = extensions.getByType(AndroidComponentsExtension::class.java)
      val ndkDirectoryProvider = androidComponents.sdkComponents.ndkDirectory.map { it.asFile }

      androidComponents.apply {
        onVariants { variant ->
          configureVariant(variant, ndkDirectoryProvider, androidExtension, buildForHost)
        }
      }
    }
  }

  private fun Project.configureVariant(variant: Variant,
                                       ndkDirectoryProvider: org.gradle.api.provider.Provider<File>,
                                       androidExtension: CommonExtension,
                                       buildForHost: TaskProvider<BuildForHostTask>
  ) {
    val variantName = variant.name.replaceFirstChar { name ->
      if (name.isLowerCase()) name.titlecase(Locale.ROOT) else name.toString()
    }
    configureGenDbgSymsTask(variantName, variant, ndkDirectoryProvider)
    configureGenNativeHeadersTask(variantName, androidExtension, variant,
      buildForHost)
  }

  @Suppress("UnstableApiUsage")
  private fun Project.configureGenNativeHeadersTask(variantName: String,
                                                    androidExtension: CommonExtension,
                                                    variant: Variant,
                                                    buildForHost: TaskProvider<BuildForHostTask>
  ) {

    val generateNativeHeadersTask =
      tasks.register("generateNativeHeaders$variantName",
        GenerateNativeHeadersTask::class.java) {

        val javaSourceSet = androidExtension.sourceSets.getByName("main").java
        
        val srcDirsCollection = project.files(javaSourceSet)
        
        this.srcDirs.set(srcDirsCollection)
        
        this.srcFiles = srcDirsCollection.asFileTree
        
        this.classPath = variant.compileClasspath
        this.outputDirectory.set(
          project.layout.buildDirectory.dir("generated/native_headers"))
      }

    buildForHost.dependsOn(generateNativeHeadersTask)
  }



  private fun Project.configureGenDbgSymsTask(variantName: String,
                                              variant: Variant,
                                              ndkDirectoryProvider: org.gradle.api.provider.Provider<File>
  ) {
    @Suppress("UnstableApiUsage")
    val generateDebugSymbolsTask =
      tasks.register("generateDebugSymbols$variantName",
        GenerateDebugSymbolsTask::class.java) {

        this.inputDirectory.set(variant.artifacts.get(MERGED_NATIVE_LIBS))
        this.outputDirectory.set(project.layout.buildDirectory.dir("debug-symbols"))
        
        val objcopyFileProvider: org.gradle.api.provider.Provider<File> = ndkDirectoryProvider.map { ndkDir ->
            val osName = System.getProperty("os.name").lowercase()
            val hostOsDir = when {
                osName.contains("win") -> "windows-x86_64"
                osName.contains("mac") -> "darwin-x86_64"
                else -> "linux-x86_64"
            }
            val exeExtension = if (osName.contains("win")) ".exe" else ""
            
            val objcopyFile = ndkDir.resolve("toolchains/llvm/prebuilt/$hostOsDir/bin/llvm-objcopy$exeExtension")
            if (!objcopyFile.exists()) {
                val alternative = ndkDir.resolve("toolchains/llvm/prebuilt/$hostOsDir/bin/objcopy$exeExtension")
                if(alternative.exists()) alternative else objcopyFile
            } else {
                objcopyFile
            }
        }

        this.objcopyExecutable.set(project.layout.file(objcopyFileProvider))
      }

    tasks.withType(MergeNativeLibsTask::class.java) {
      finalizedBy(generateDebugSymbolsTask)
    }
  }
}
