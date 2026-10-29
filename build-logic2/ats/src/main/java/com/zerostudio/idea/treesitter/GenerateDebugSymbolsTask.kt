package com.zerostudio.idea.treesitter

import com.android.build.gradle.internal.core.Abi
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * @author Akash Yadav
 * @author android_zero（github:msmt2018）已升级适配 Gradle 9.8 + AGP 9.4+
 */
abstract class GenerateDebugSymbolsTask : DefaultTask() {

  @get:Inject
  abstract val execOperations: ExecOperations

  /**
   * The input directory which contains the unstripped native libraries.
   */
  @get:InputDirectory
  abstract val inputDirectory: DirectoryProperty

  /**
   * The output directory which where the debug symbols will be stored.
   */
  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @get:InputFile
  abstract val objcopyExecutable: RegularFileProperty

  @TaskAction
  fun generateDebugSymbols() {
    inputDirectory.asFileTree.files.forEach {
      generateDebugSymbolsForFile(it)
    }
  }

  private fun generateDebugSymbolsForFile(input: File?) {
    val file = if (input?.extension == "so") input else null ?: return
    
    val abi = checkNotNull(Abi.getByName(file.parentFile.name)) {
      "Unsupported ABI '${file.parentFile.name}' for file '$file'"
    }

    logger.info("Generating debug symbols file from: $file")

    val outputFile = outputDirectory.dir("" + abi.name.lowercase()).apply {
      val dir = get().asFile
      logger.debug("Creating output directory: {}", dir)
      dir.mkdirs()
    }.let {
      File(it.get().asFile, "${file.name}.debug_info").apply {
        logger.info("Output file for debug symbols: $this")
      }
    }

    val cmdLine = arrayOf(
      objcopyExecutable.get().asFile.absolutePath,
      "--only-keep-debug",
      file.absolutePath,
      outputFile.absolutePath
    )

    logger.info("Executing cmd: ${cmdLine.joinToString(separator = " ")}")

    val output = ByteArrayOutputStream()
    
    val result = execOperations.exec {
      setCommandLine(*cmdLine)
      standardOutput = output
      errorOutput = output
      isIgnoreExitValue = true
    }

    check(result.exitValue == 0) {
      "Failed to generate debug symbols for file: ${file}\n\n${
        String(output.toByteArray())
      }"
    }
  }
}
