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

#include <jni.h>
#include <string>

#include "utils/ts_obj_utils.h"
#include "ts__log.h"

#include "ts__onload.h"

//Manually maintain the core version of the treesitter engine
//Check the core version information of the treesitter engine you are currently pulling:https://github.com/tree-sitter/tree-sitter/blob/master/Cargo.toml
// Cargo.toml --> [workspace.package] --> version = "x.xx.x"
#define TS_VERSION_NUM   "0.28.0"
//Manually maintain the current git commit hash value during pull
#define TS_GIT_HASH      "ad067fd"

#if defined(__ANDROID__)
static jint JNI_VERSION = JNI_VERSION_1_6;
#else
static jint JNI_VERSION = JNI_VERSION_10;
#endif

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {

  JNIEnv *env;
  if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION) != JNI_OK) {
    LOGE(LOG_TAG, "Failed to get JNIEnv* from JavaVM: %p", vm);
    return JNI_ERR;
  }

  TS_JNI_ONLOAD__DEFINE_METHODS_ARR
  TS_JNI_ONLOAD__AUTO_REGISTER(env)

  onLoad(env);

  return JNI_VERSION;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
  JNIEnv *env;
  if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION) != JNI_OK) {
    LOGE(LOG_TAG, "Failed to get JNIEnv* from JavaVM: %p", vm);
    return;
  }

  onUnload(env);
}


extern "C" {
JNIEXPORT jstring JNICALL
Java_com_zerostudio_idea_treesitter_TreeSitter_00024Native_getEngineVersion(JNIEnv *env, jobject /* thiz */) {
    // 使用 std::string 动态连接格式：Vxx.xx-hasa (例如: V0.28.0-a1b2c3d)
    std::string version_str = "V" TS_VERSION_NUM "-" TS_GIT_HASH;

    // 将 C++ 字符串转换为 Java 能够识别的 UTF-8 jstring 并返回
    return env->NewStringUTF(version_str.c_str());
}
} // extern "C"