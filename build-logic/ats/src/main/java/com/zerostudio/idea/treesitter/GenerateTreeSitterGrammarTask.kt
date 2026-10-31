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

package com.zerostudio.idea.treesitter

import org.gradle.api.DefaultTask
import org.gradle.api.logging.LogLevel.LIFECYCLE
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * @author Akash Yadav
 * @author android_zero <github.com/msmt2018> [update] : On October 31, 2026, upgraded to support composite multi-parser source generation across sub-paths.
 */
abstract class GenerateTreeSitterGrammarTask : DefaultTask() {

  @TaskAction
  fun generateGrammar() {
    val langName = project.name.substringAfterLast('-')
    val grammars = project.readGrammars()
    val grammar = grammars.find { it.name == langName }
      ?: throw IllegalStateException("Grammar '$langName' definition not found in grammars.json")

    var tsCmd = project.rootProject.file("tree-sitter-lib/cli/build/release/tree-sitter").absolutePath
    if (!BUILD_TS_CLI_FROM_SOURCE) {
      tsCmd = "tree-sitter"
    }

    if (!grammar.composite) {
      val grammarDir = project.rootProject.file("grammars/$langName").absolutePath
      project.logger.log(LIFECYCLE, "Using '$tsCmd' to generate single grammar '${project.name}'")
      project.executeCommand(grammarDir, tsCmd, "generate")
    } else {
      project.logger.log(LIFECYCLE, "Using '$tsCmd' to generate composite grammars for '${project.name}'")
      
      val grammarRootDir = project.rootProject.file("grammars/$langName")
      for (subPath in grammar.subPaths) {
        val subDir = File(grammarRootDir, subPath).absolutePath
        project.logger.log(LIFECYCLE, "Generating sub-parser grammar at: $subDir")
        project.executeCommand(subDir, tsCmd, "generate")
      }
    }
  }
}