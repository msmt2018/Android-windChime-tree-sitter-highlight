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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File

/**
 * Plugin applied to grammar modules.
 *
 * @author Akash Yadav
 * @author android_zero <github.com/msmt2018> [update] : On October 31, 2026, upgraded to support composite sub-paths inputs & outputs tracking.
 */
class TsGrammarPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.run {
      val grammars = target.readGrammars()
      val grammar = grammars.find {
        it.name == project.name.substringAfterLast('-')
      } ?: return@run

      val grammarDir = rootProject.rootDir.resolve("grammars/${grammar.name}")

      val generateTask = tasks.register("generateTreeSitterGrammar",
        GenerateTreeSitterGrammarTask::class.java) {

        dependsOn(rootProject.tasks.getByName("buildTreeSitter"))

        if (!grammar.composite) {
          inputs.file(File(grammarDir, "grammar.js"))
          for (extra in grammar.srcExtra) {
            inputs.file(File(grammarDir, extra))
          }
          outputs.file(File(grammarDir, "src/parser.c"))
        } else {
          // 复合多解析器：为每个 subPath 注册输入 grammar.js 和输出 parser.c
          for (subPath in grammar.subPaths) {
            val subDir = File(grammarDir, subPath)
            val grammarJs = File(subDir, "grammar.js")
            if (grammarJs.exists()) {
              inputs.file(grammarJs)
            }
            outputs.file(File(subDir, "src/parser.c"))
          }
          for (extra in grammar.srcExtra) {
            inputs.file(File(grammarDir, extra))
          }
        }
      }
      tasks.withType(JavaCompile::class.java) { dependsOn(generateTask) }
    }
  }
}