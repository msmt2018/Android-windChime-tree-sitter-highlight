import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("android-tree-sitter.dynamic-modules") {
            id = "android-tree-sitter.dynamic-modules"
            implementationClass = "com.zerostudio.idea.treesitter.DynamicModulePlugin"
        }

        create("android-tree-sitter.ts") {
            id = "android-tree-sitter.ts"
            implementationClass = "com.zerostudio.idea.treesitter.TreeSitterPlugin"
        }

        create("android-tree-sitter.ts-grammar") {
            id = "android-tree-sitter.ts-grammar"
            implementationClass = "com.zerostudio.idea.treesitter.TsGrammarPlugin"
        }
    }
}

repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
    this.compilerOptions {
        this.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}


dependencies {
    implementation(gradleApi())
    implementation(libs.gradle.android)
    implementation(libs.google.gson)
    implementation(libs.google.guava)
    implementation(libs.javaparser.core)
}