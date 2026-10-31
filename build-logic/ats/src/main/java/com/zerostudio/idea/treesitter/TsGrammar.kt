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
 *  along with android-tree-sitter.  If not, see <https://gnu.org>.
 */

package com.zerostudio.idea.treesitter

import com.google.gson.annotations.SerializedName

/**
 * Model for grammar entries in `grammars.json` file.
 * Enhanced to support composite multi-parser repositories seamlessly.
 *
 * @author android_zero <github.com/msmt2018> [update] : For TsGrammar
 * @author Akash Yadav [Explanation]:Historical contribution: Wrote the basic source code for TsGrammar
 */
data class TsGrammar(
    @SerializedName("name") val name: String,
    // composite : "Boolean" 
    //true：When set to true, it indicates that the grammars/{$language} It contains multiple identical C/C++ parser source codes
    // Demonstration:
    // grammars/markdwon/tree-sitter-markdown/src/scanner.c
    // grammars/markdwon/tree-sitter-markdown-inline/src/scanner.c
    // If it is false or there is no composite, use the traditional method: grammars/yaml/src/parser.c
    @SerializedName("composite") val composite: Boolean = false,
    @SerializedName("sub.paths") val subPaths: List<String> = emptyList(),
    @SerializedName("src.extra") val srcExtra: List<String> = emptyList()
) {
    fun capitalizedName() = name.replaceFirstChar { it.titlecaseChar() }
}