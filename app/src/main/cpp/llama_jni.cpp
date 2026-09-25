// JNI-мост SchoolHub <-> llama.cpp (тег b4900).
#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <string>
#include <vector>
#include "llama.h"

#define TAG "SchoolHubLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct Session {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    llama_sampler * smpl = nullptr;
    const llama_vocab * vocab = nullptr;
    llama_context_params cparams{};
    int n_past = 0;
    int n_ctx = 0;
    int generated = 0;
    int max_tokens = 1024;
    std::string pending;
};

bool g_backend_ready = false;

llama_sampler * make_sampler() {
    llama_sampler * s = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(s, llama_sampler_init_penalties(64, 1.1f, 0.0f, 0.0f));
    llama_sampler_chain_add(s, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(s, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(s, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(s, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    return s;
}

// Длина самого длинного префикса из целых UTF-8 символов (токен может разрезать символ).
size_t valid_utf8_prefix(const std::string & s) {
    size_t i = 0, ok = 0;
    while (i < s.size()) {
        const unsigned char c = static_cast<unsigned char>(s[i]);
        size_t len = 1;
        if (c >= 0xF0) len = 4; else if (c >= 0xE0) len = 3; else if (c >= 0xC0) len = 2;
        if (i + len > s.size()) break;
        i += len;
        ok = i;
    }
    return ok;
}

jbyteArray to_bytes(JNIEnv * env, const std::string & s) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(s.size()));
    if (!s.empty()) {
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    }
    return arr;
}

std::string from_bytes(JNIEnv * env, jbyteArray arr) {
    const jsize len = env->GetArrayLength(arr);
    std::string out(static_cast<size_t>(len), '\0');
    if (len > 0) env->GetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte *>(&out[0]));
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_school_hub_feature_ai_engine_LlamaBridge_nativeLoad(JNIEnv * env, jobject, jstring jpath, jint n_ctx, jint n_threads) {
    if (!g_backend_ready) { llama_backend_init(); g_backend_ready = true; }

    const char * path = env->GetStringUTFChars(jpath, nullptr);
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    llama_model * model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jpath, path);
    if (!model) { LOGE("model load failed"); return 0; }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(n_ctx);
    cparams.n_batch = 512;
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;

    llama_context * ctx = llama_init_from_model(model, cparams);
    if (!ctx) { LOGE("context init failed"); llama_model_free(model); return 0; }

    auto * s = new Session();
    s->model = model;
    s->ctx = ctx;
    s->cparams = cparams;
    s->vocab = llama_model_get_vocab(model);
    s->smpl = make_sampler();
    s->n_ctx = static_cast<int>(llama_n_ctx(ctx));
    LOGI("model loaded, n_ctx=%d threads=%d", s->n_ctx, n_threads);
    return reinterpret_cast<jlong>(s);
}

// 0 — ок, -1 — не влезает в контекст, -2 — ошибка
JNIEXPORT jint JNICALL
Java_com_school_hub_feature_ai_engine_LlamaBridge_nativeStartTurn(JNIEnv * env, jobject, jlong handle, jbyteArray jprompt) {
    auto * s = reinterpret_cast<Session *>(handle);
    if (!s) return -2;
    const std::string prompt = from_bytes(env, jprompt);
    const bool add_special = s->n_past == 0;

    const int n = -llama_tokenize(s->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), nullptr, 0, add_special, true);
    if (n <= 0) return -2;
    std::vector<llama_token> tokens(static_cast<size_t>(n));
    if (llama_tokenize(s->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), tokens.data(), n, add_special, true) < 0) return -2;
    if (s->n_past + n + 16 >= s->n_ctx) return -1;

    const int n_batch = static_cast<int>(s->cparams.n_batch);
    for (int i = 0; i < n; i += n_batch) {
        const int cnt = std::min(n_batch, n - i);
        llama_batch batch = llama_batch_get_one(tokens.data() + i, cnt);
        if (llama_decode(s->ctx, batch) != 0) { LOGE("decode prompt failed"); return -2; }
        s->n_past += cnt;
    }
    s->generated = 0;
    s->pending.clear();
    return 0;
}

// Возвращает кусок текста (UTF-8), пустой массив — "жди дальше", null — конец ответа.
JNIEXPORT jbyteArray JNICALL
Java_com_school_hub_feature_ai_engine_LlamaBridge_nativeNext(JNIEnv * env, jobject, jlong handle) {
    auto * s = reinterpret_cast<Session *>(handle);
    if (!s) return nullptr;
    if (s->generated >= s->max_tokens || s->n_past + 1 >= s->n_ctx) return nullptr;

    llama_token tok = llama_sampler_sample(s->smpl, s->ctx, -1);
    if (llama_vocab_is_eog(s->vocab, tok)) return nullptr;

    char buf[256];
    int len = llama_token_to_piece(s->vocab, tok, buf, sizeof(buf), 0, false);
    if (len < 0) {
        std::vector<char> big(static_cast<size_t>(-len));
        len = llama_token_to_piece(s->vocab, tok, big.data(), static_cast<int32_t>(big.size()), 0, false);
        if (len > 0) s->pending.append(big.data(), static_cast<size_t>(len));
    } else if (len > 0) {
        s->pending.append(buf, static_cast<size_t>(len));
    }

    llama_batch batch = llama_batch_get_one(&tok, 1);
    if (llama_decode(s->ctx, batch) != 0) return nullptr;
    s->n_past += 1;
    s->generated += 1;

    const size_t ok = valid_utf8_prefix(s->pending);
    const std::string out = s->pending.substr(0, ok);
    s->pending.erase(0, ok);
    return to_bytes(env, out);
}

JNIEXPORT jboolean JNICALL
Java_com_school_hub_feature_ai_engine_LlamaBridge_nativeReset(JNIEnv *, jobject, jlong handle) {
    auto * s = reinterpret_cast<Session *>(handle);
    if (!s) return JNI_FALSE;
    if (s->ctx) llama_free(s->ctx);
    s->ctx = llama_init_from_model(s->model, s->cparams);
    llama_sampler_reset(s->smpl);
    s->n_past = 0;
    s->generated = 0;
    s->pending.clear();
    return s->ctx ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_school_hub_feature_ai_engine_LlamaBridge_nativeFree(JNIEnv *, jobject, jlong handle) {
    auto * s = reinterpret_cast<Session *>(handle);
    if (!s) return;
    if (s->smpl) llama_sampler_free(s->smpl);
    if (s->ctx) llama_free(s->ctx);
    if (s->model) llama_model_free(s->model);
    delete s;
}

JNIEXPORT jstring JNICALL
Java_com_school_hub_feature_ai_engine_LlamaBridge_nativeSystemInfo(JNIEnv * env, jobject) {
    return env->NewStringUTF(llama_print_system_info());
}

} // extern "C"
