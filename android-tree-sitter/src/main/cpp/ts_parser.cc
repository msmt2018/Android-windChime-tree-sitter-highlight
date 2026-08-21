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

#include <atomic>
#include <chrono>
#include <iostream>

#include "utf16str/UTF16String.h"
#include "utils/ts_obj_utils.h"
#include "utils/ts_exceptions.h"
#include "utils/ts_preconditions.h"
#include "ts__log.h"

#include "ts_parser.h"

/**
 * `TSParserInternal` stores the actual tree sitter parser instance along
 * with the cancellation flag and the cancellation flag mutex.
 */
class TSParserInternal {
 public:
  TSParserInternal() : parser(ts_parser_new()) {}

  ~TSParserInternal() {
    ts_parser_delete(parser);
    parser = nullptr;
  }

  TSParser *getParser(JNIEnv *env) {
    if (check_destroyed(env)) {
      return nullptr;
    }
    return parser;
  }

  bool beginRound(JNIEnv *env) {
    bool expected = false;
    if (!parsing.compare_exchange_strong(expected, true)) {
      throw_illegal_state(env, "Parser is already parsing another syntax tree!");
      return false;
    }
    cancellationRequested.store(false);
    return true;
  }

  void endRound() {
    parsing.store(false);
    cancellationRequested.store(false);
  }

  bool requestCancellation() {
    if (!parsing.load()) {
      return false;
    }
    cancellationRequested.store(true);
    return true;
  }

  void setTimeoutMicros(uint64_t timeout) {
    timeoutMicros.store(timeout);
  }

  uint64_t getTimeoutMicros() const {
    return timeoutMicros.load();
  }

  bool shouldStop() const {
    if (cancellationRequested.load()) {
      return true;
    }
    const auto timeout = timeoutMicros.load();
    return timeout != 0 &&
      std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::steady_clock::now() - parseStart).count() >= timeout;
  }

  void markParseStart() {
    parseStart = std::chrono::steady_clock::now();
  }

 private:
  TSParser *parser;
  std::atomic_bool parsing{false};
  std::atomic_bool cancellationRequested{false};
  std::atomic_uint64_t timeoutMicros{0};
  std::chrono::steady_clock::time_point parseStart{};

  bool check_destroyed(JNIEnv *env) const {
    if (parser == nullptr) {
      throw_illegal_state(env, "TSParserInternal has already been destroyed");
      return true;
    }
    return false;
  }
};

static bool TSParser_progressCallback(TSParseState *state) {
  auto *parser = static_cast<TSParserInternal *>(state->payload);
  return parser == nullptr || parser->shouldStop();
}

struct TSParserInput {
  const char *bytes;
  uint32_t length;
};

static const char *TSParser_readInput(
    void *payload,
    uint32_t byte_index,
    TSPoint position,
    uint32_t *bytes_read) {
  auto *input = static_cast<TSParserInput *>(payload);
  if (byte_index >= input->length) {
    *bytes_read = 0;
    return nullptr;
  }

  *bytes_read = input->length - byte_index;
  return input->bytes + byte_index;
}

static jlong
TSParser_newParser(JNIEnv *env,
                   jclass self) {
  auto parser = new TSParserInternal;
  return (jlong) parser;
}

static void
TSParser_delete(JNIEnv *env,
                jclass self,
                jlong parser_ptr) {
  req_nnp(env, parser_ptr);

  auto parser = (TSParserInternal *) parser_ptr;
  delete parser;
}

static void
TSParser_setLanguage(JNIEnv *env,
                     jclass self,
                     jlong parser,
                     jlong language) {
  req_nnp(env, parser, "parser");
  req_nnp(env, language, "language");
  ts_parser_set_language(((TSParserInternal *) parser)->getParser(env),
                         (TSLanguage *) language);
}

static jlong
TSParser_getLanguage(JNIEnv *env,
                     jclass self,
                     jlong parser) {
  req_nnp(env, parser);
  return (jlong) ts_parser_language(((TSParserInternal *) parser)->getParser(env));
}

static void
TSParser_reset(JNIEnv *env,
               jclass self,
               jlong parser) {
  req_nnp(env, parser);
  TSParser *pParser = ((TSParserInternal *) (parser))->getParser(env);
  LOGD("ts_parser.cc", "Reset parser: %p, language: %p", pParser,
       ts_parser_language(pParser));
  ts_parser_reset(pParser);
}

static void
TSParser_setTimeout(JNIEnv *env,
                    jclass self,
                    jlong parser,
                    jlong macros) {
  req_nnp(env, parser);
  ((TSParserInternal *) parser)->setTimeoutMicros(static_cast<uint64_t>(macros));
}

static jlong
TSParser_getTimeout(JNIEnv *env,
                    jclass self,
                    jlong parser) {
  req_nnp(env, parser);
  return static_cast<jlong>(
      ((TSParserInternal *) parser)->getTimeoutMicros());
}

static jboolean
TSParser_setIncludedRanges(
    JNIEnv *env,
    jclass self,
    jlong parser,
    jobjectArray ranges) {
  req_nnp(env, parser);
  int count = env->GetArrayLength(ranges);
  TSRange tsRanges[count];
  for (int i = 0; i < count; i++) {
    jobject range = env->GetObjectArrayElement(ranges, i);
    std::string msg = std::string("ranges[") + std::to_string(i) + "]";
    req_nnp(env, range, msg);
    tsRanges[i] = _unmarshalRange(env, range);
  }

  const TSRange *r = tsRanges;
  return (jboolean) ts_parser_set_included_ranges(((TSParserInternal *) parser)->getParser(
      env), r, count);
}

static jobjectArray
TSParser_getIncludedRanges(
    JNIEnv *env,
    jclass self,
    jlong parser) {
  req_nnp(env, parser);
  jint count;
  const TSRange *ranges =
      ts_parser_included_ranges(((TSParserInternal *) parser)->getParser(env),
                                reinterpret_cast<uint32_t *>(&count));
  jobjectArray result = createRangeArr(env, count);
  req_nnp(env, result, "TSRange[] from factory");

  for (uint32_t i = 0; i < count; i++) {
    const TSRange *r = (ranges + i);
    env->SetObjectArrayElement(result, (jint) i, _marshalRange(env, *r));
  }
  return result;
}

static jlong TSParser_parse(JNIEnv *env,
                            jclass clazz,
                            jlong parser,
                            jlong tree_pointer,
                            jlong str_pointer) {
  req_nnp(env, parser);
  req_nnp(env, str_pointer, "string");
  auto *ts_parser_internal = (TSParserInternal *) parser;
  TSParser *ts_parser = ts_parser_internal->getParser(env);
  TSTree *old_tree = tree_pointer == 0 ? nullptr : (TSTree *) tree_pointer;
  auto *source = as_str(env, str_pointer);

  if (!ts_parser_internal->beginRound(env)) {
    return 0;
  }

  const char *src_cstring = source->to_cstring();
  TSParserInput inputPayload{
      src_cstring,
      static_cast<uint32_t>(source->byte_length()),
  };
  TSInput input{
      &inputPayload,
      TSParser_readInput,
      TSInputEncodingUTF16LE,
      nullptr,
  };
  TSParseOptions options{
      ts_parser_internal,
      TSParser_progressCallback,
  };

  // Tree-sitter 0.26 replaces timeout and cancellation-flag APIs with a
  // progress callback. It returns nullptr when that callback cancels parsing.
  ts_parser_internal->markParseStart();
  auto tree = ts_parser_parse_with_options(ts_parser, old_tree, input, options);

  ts_parser_internal->endRound();
  delete[] src_cstring;
  return (jlong) tree;
}

static jboolean
TSParser_requestCancellation(
    JNIEnv *env,
    jclass clazz,
    jlong parser) {

  req_nnp(env, parser);
  const bool requested = ((TSParserInternal *) parser)->requestCancellation();
  if (requested) {
    LOGD("TSParser", "Cancellation has been requested");
  }
  return static_cast<jboolean>(requested);
}

void TSParser_Native__SetJniMethods(JNINativeMethod *methods, int count) {
  SET_JNI_METHOD(methods, TSParser_Native_newParser, TSParser_newParser);
  SET_JNI_METHOD(methods, TSParser_Native_delete, TSParser_delete);
  SET_JNI_METHOD(methods, TSParser_Native_setLanguage, TSParser_setLanguage);
  SET_JNI_METHOD(methods, TSParser_Native_getLanguage, TSParser_getLanguage);
  SET_JNI_METHOD(methods, TSParser_Native_reset, TSParser_reset);
  SET_JNI_METHOD(methods, TSParser_Native_setTimeout, TSParser_setTimeout);
  SET_JNI_METHOD(methods, TSParser_Native_getTimeout, TSParser_getTimeout);
  SET_JNI_METHOD(methods, TSParser_Native_setIncludedRanges, TSParser_setIncludedRanges);
  SET_JNI_METHOD(methods, TSParser_Native_getIncludedRanges, TSParser_getIncludedRanges);
  SET_JNI_METHOD(methods, TSParser_Native_parse, TSParser_parse);
  SET_JNI_METHOD(methods, TSParser_Native_requestCancellation,
                 TSParser_requestCancellation);
}