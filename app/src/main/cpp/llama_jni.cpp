// JNI-мост SchoolHub <-> llama.cpp: текст, встроенный
// чат-шаблон модели и зрение (mtmd).
#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"
#ifdef SCHOOLHUB_VISION
#include "mtmd.h"
#include "mtmd-helper.h"
#endif

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
    std::atomic<bool> abort_requested{false};
#ifdef SCHOOLHUB_VISION
    mtmd_context * mctx = nullptr;
#endif
};

std::once_flag g_backend_once;

void ensure_backend() {
    std::call_once(g_backend_once, [] { llama_backend_init(); });
}

bool abort_callback(void * data) {
    auto * session = static_cast<Session *>(data);
    return session != nullptr && session->abort_requested.load(std::memory_order_relaxed);
}

llama_sampler * make_sampler() {
    llama_sampler * s = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(s, llama_sampler_init_penalties(64, 1.1f, 0.0f, 0.0f));
    llama_sampler_chain_add(s, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(s, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(s, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(s, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    return s;
}

size_t valid_utf8_prefix(const std::string & s) {
    size_t i = 0, ok = 0;
    while (i < s.size()) {
        const unsigned char c = static_cast<unsigned char>(s[i]);
        size_t len = c >= 0xF0 ? 4 : (c >= 0xE0 ? 3 : (c >= 0xC0 ? 2 : 1));
        if (i + len > s.size()) break;
        i += len;
        ok = i;
    }
    return ok;
}

jbyteArray to_bytes(JNIEnv * env, const std::string & s) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(s.size()));
    if (!arr) return nullptr;
    if (!s.empty()) env->SetByteArrayRegion(arr, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    return arr;
}

std::string from_bytes(JNIEnv * env, jbyteArray arr) {
    if (!arr) return {};
    const jsize len = env->GetArrayLength(arr);
    std::string out(static_cast<size_t>(len), '\0');
    if (len > 0) env->GetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte *>(&out[0]));
    return out;
}

bool reset_context(Session * s) {
    if (!s || !s->model) return false;
    if (s->ctx) llama_free(s->ctx);
    s->ctx = llama_init_from_model(s->model, s->cparams);
    if (s->ctx) llama_set_abort_callback(s->ctx, abort_callback, s);
    if (s->smpl) llama_sampler_reset(s->smpl);
    s->n_past = 0;
    s->generated = 0;
    s->pending.clear();
    return s->ctx != nullptr;
}
} // namespace

extern "C" {
JNIEXPORT jlong JNICALL Java_com_school_hub_feature_ai_engine_LlamaBridge_nativeLoad(JNIEnv * env, jobject, jstring jpath, jint n_ctx, jint n_threads) {
    ensure_backend();
    if (!jpath) return 0;
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
    s->model = model; s->ctx = ctx; s->cparams = cparams;
    s->vocab = llama_model_get_vocab(model); s->smpl = make_sampler();
    s->n_ctx = static_cast<int>(llama_n_ctx(ctx));
    llama_set_abort_callback(s->ctx, abort_callback, s);
    LOGI("model loaded, n_ctx=%d threads=%d", s->n_ctx, n_threads);
    return reinterpret_cast<jlong>(s);
}

JNIEXPORT jint JNICALL Java_com_school_hub_feature_ai_engine_LlamaBridge_nativeStartTurn(JNIEnv * env, jobject, jlong handle, jbyteArray jprompt) {
    auto * s = reinterpret_cast<Session *>(handle);
    if (!s || !s->ctx || !s->vocab) return -2;
    s->abort_requested.store(false, std::memory_order_relaxed);
    const std::string prompt = from_bytes(env, jprompt);
    const bool add_special = s->n_past == 0;
    const int n = -llama_tokenize(s->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), nullptr, 0, add_special, true);
    if (n <= 0) return -2;
    std::vector<llama_token> tokens(static_cast<size_t>(n));
    if (llama_tokenize(s->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), tokens.data(), n, add_special, true) < 0) return -2;
    if (s->n_past + n + 16 >= s->n_ctx) return -1;
    const int n_batch = static_cast<int>(s->cparams.n_batch);
    for (int i = 0; i < n; i += n_batch) {
        if (s->abort_requested.load(std::memory_order_relaxed)) return -3;
        const int cnt = std::min(n_batch, n - i);
        llama_batch batch = llama_batch_get_one(tokens.data() + i, cnt);
        if (llama_decode(s->ctx, batch) != 0) {
            if (s->abort_requested.load(std::memory_order_relaxed)) return -3;
            LOGE("decode prompt failed"); return -2;
        }
        s->n_past += cnt;
    }
    s->generated = 0; s->pending.clear();
    return 0;
}

JNIEXPORT void JNICALL Java_com_school_hub_feature_ai_engine_LlamaBridge_nativeAbort(JNIEnv *, jobject, jlong handle) {
    auto * s = reinterpret_cast<Session *>(handle);
    if (s) s->abort_requested.store(true, std::memory_order_relaxed);
}

JNIEXPORT jbyteArray JNICALL Java_com_school_hub_feature_ai_engine_LlamaBridge_nativeNext(JNIEnv * env, jobject, jlong handle) {
    auto * s = reinterpret_cast<Session *>(handle);
    if (!s || s->abort_requested.load(std::memory_order_relaxed)) return nullptr;
    if (s->generated >= s->max_tokens || s->n_past + 1 >= s->n_ctx) return nullptr;
    llama_token tok = llama_sampler_sample(s->smpl, s->ctx, -1);
    if (llama_vocab_is_eog(s->vocab, tok)) return nullptr;
    char buf[256];
    int len = llama_token_to_piece(s->vocab, tok, buf, sizeof(buf), 0, false);
    if (len < 0) {
        std::vector<char> big(static_cast<size_t>(-len));
        len = llama_token_to_piece(s->vocab, tok, big.data(), static_cast<int32_t>(big.size()), 0, false);
        if (len > 0) s->pending.append(big.data(), static_cast<size_t>(len));
    } else if (len > 0) s->pending.append(buf, static_cast<size_t>(len));
    llama_batch batch = llama_batch_get_one(&tok, 1);
    if (llama_decode(s->ctx, batch) != 0) return nullptr;
    s->n_past++; s->generated++;
    const size_t ok = valid_utf8_prefix(s->pending);
    const std::string out = s->pending.substr(0, ok);
    s->pending.erase(0, ok);
    return to_bytes(env, out);
}

JNIEXPORT jboolean JNICALL Java_com_school_hub_feature_ai_engine_LlamaBridge_nativeReset(JNIEnv *, jobject, jlong handle) {
    auto * s = reinterpret_cast<Session *>(handle);
    if (!s) return JNI_FALSE;
    s->abort_requested.store(false, std::memory_order_relaxed);
    return reset_context(s) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_school_hub_feature_ai_engine_LlamaBridge_nativeFree(JNIEnv *, jobject, jlong handle) {
    auto * s = reinterpret_cast<Session *>(handle);
    if (!s) return;
    s->abort_requested.store(true, std::memory_order_relaxed);
#ifdef SCHOOLHUB_VISION
    if (s->mctx) mtmd_free(s->mctx);
#endif
    if (s->smpl) llama_sampler_free(s->smpl);
    if (s->ctx) llama_free(s->ctx);
    if (s->model) llama_model_free(s->model);
    delete s;
}

JNIEXPORT jstring JNICALL Java_com_school_hub_feature_ai_engine_LlamaBridge_nativeSystemInfo(JNIEnv * env, jobject) { return env->NewStringUTF(llama_print_system_info()); }

JNIEXPORT jbyteArray JNICALL Java_com_school_hub_feature_ai_engine_LlamaBridge_nativeApplyTemplate(JNIEnv * env, jobject, jlong handle, jobjectArray jroles, jobjectArray jcontents, jboolean add_ass) {
    auto * s = reinterpret_cast<Session *>(handle);
    if (!s) return to_bytes(env, "");
    const char * tmpl = llama_model_chat_template(s->model, nullptr);
    if (!tmpl) return to_bytes(env, "");
    const jsize n = env->GetArrayLength(jroles);
    std::vector<std::string> roles(n), contents(n); std::vector<llama_chat_message> msgs(n);
    for (jsize i = 0; i < n; ++i) { roles[i] = from_bytes(env, (jbyteArray) env->GetObjectArrayElement(jroles, i)); contents[i] = from_bytes(env, (jbyteArray) env->GetObjectArrayElement(jcontents, i)); msgs[i] = {roles[i].c_str(), contents[i].c_str()}; }
    std::vector<char> buf(8192);
    int len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), add_ass, buf.data(), static_cast<int32_t>(buf.size()));
    if (len > static_cast<int>(buf.size())) { buf.resize(static_cast<size_t>(len) + 1); len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), add_ass, buf.data(), static_cast<int32_t>(buf.size())); }
    return len > 0 ? to_bytes(env, std::string(buf.data(), static_cast<size_t>(len))) : to_bytes(env, "");
}

JNIEXPORT jboolean JNICALL Java_com_school_hub_feature_ai_engine_LlamaBridge_nativeHasVision(JNIEnv *, jobject) {
#ifdef SCHOOLHUB_VISION
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

JNIEXPORT jboolean JNICALL Java_com_school_hub_feature_ai_engine_LlamaBridge_nativeLoadVision(JNIEnv * env, jobject, jlong handle, jstring jpath, jint n_threads) {
#ifdef SCHOOLHUB_VISION
    auto * s = reinterpret_cast<Session *>(handle); if (!s) return JNI_FALSE; if (s->mctx) return JNI_TRUE;
    const char * path = env->GetStringUTFChars(jpath, nullptr); mtmd_context_params p = mtmd_context_params_default(); p.use_gpu = false; p.n_threads = n_threads; p.print_timings = false;
    s->mctx = mtmd_init_from_file(path, s->model, p); env->ReleaseStringUTFChars(jpath, path); return s->mctx ? JNI_TRUE : JNI_FALSE;
#else
    (void)env; (void)handle; (void)jpath; (void)n_threads; return JNI_FALSE;
#endif
}

JNIEXPORT jstring JNICALL Java_com_school_hub_feature_ai_engine_LlamaBridge_nativeMediaMarker(JNIEnv * env, jobject) {
#ifdef SCHOOLHUB_VISION
    return env->NewStringUTF(mtmd_default_marker());
#else
    return env->NewStringUTF("<__media__>");
#endif
}

JNIEXPORT jint JNICALL Java_com_school_hub_feature_ai_engine_LlamaBridge_nativeVisionTurn(JNIEnv * env, jobject, jlong handle, jbyteArray jprompt, jbyteArray jrgb, jint w, jint h) {
#ifdef SCHOOLHUB_VISION
    auto * s = reinterpret_cast<Session *>(handle); if (!s || !s->mctx) return -3;
    s->abort_requested.store(false, std::memory_order_relaxed);
    if (!reset_context(s)) return -2;
    const std::string prompt = from_bytes(env, jprompt), rgb = from_bytes(env, jrgb);
    if (static_cast<jint>(rgb.size()) < w * h * 3) return -2;
    mtmd_bitmap * bmp = mtmd_bitmap_init(static_cast<uint32_t>(w), static_cast<uint32_t>(h), reinterpret_cast<const unsigned char *>(rgb.data())); if (!bmp) return -2;
    mtmd_input_text text{prompt.c_str(), true, true}; mtmd_input_chunks * chunks = mtmd_input_chunks_init(); const mtmd_bitmap * bitmaps[1] = {bmp};
    int rc = s->abort_requested.load(std::memory_order_relaxed) ? -3 : mtmd_tokenize(s->mctx, chunks, &text, bitmaps, 1);
    if (rc == 0 && !s->abort_requested.load(std::memory_order_relaxed)) { llama_pos new_past = 0; rc = mtmd_helper_eval_chunks(s->mctx, s->ctx, chunks, 0, 0, static_cast<int32_t>(s->cparams.n_batch), true, &new_past) == 0 ? 0 : -2; s->n_past = static_cast<int>(new_past); if (rc == 0 && s->n_past + 16 >= s->n_ctx) rc = -1; }
    else if (rc == 0) rc = -3;
    mtmd_input_chunks_free(chunks); mtmd_bitmap_free(bmp); return rc;
#else
    (void)env; (void)handle; (void)jprompt; (void)jrgb; (void)w; (void)h; return -3;
#endif
}
}
