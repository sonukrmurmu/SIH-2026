#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <chrono>
#include <mutex>
#include <thread>
#include <android/log.h>
#include <ctranslate2/translator.h>
#include <ctranslate2/replica_pool.h>
#include <sentencepiece_processor.h>
#include <algorithm>

#define LOG_TAG "SIH_AI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static sentencepiece::SentencePieceProcessor* g_spm_source = nullptr;
static sentencepiece::SentencePieceProcessor* g_spm_target = nullptr;
static std::string g_model_dir = "";
static ctranslate2::Translator* g_translator = nullptr;
static std::mutex g_engine_mutex;

static std::vector<std::string> split_into_chunks(const std::string& text) {
    std::vector<std::string> chunks;
    std::stringstream ss(text);
    std::string chunk;
    while (std::getline(ss, chunk, '.')) {
        if (!chunk.empty()) {
            chunks.push_back(chunk + ".");
        }
    }
    if (chunks.empty()) {
        chunks.push_back(text);
    }
    return chunks;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_indicT_MainActivity_initNativeTranslator(
        JNIEnv* env, jobject /* this */, jstring model_dir, jstring /* unused_spm_path */) {

    std::lock_guard<std::mutex> lock(g_engine_mutex);

    const char* native_model = env->GetStringUTFChars(model_dir, nullptr);
    std::string target_model_dir = std::string(native_model);
    env->ReleaseStringUTFChars(model_dir, native_model);

    // If already active in memory with the exact same directory, reuse it
    if (g_translator != nullptr && g_spm_source != nullptr && g_spm_target != nullptr && g_model_dir == target_model_dir) {
        LOGI("Engine 1 (Eng->Indic) already loaded in RAM. Skipping re-init.");
        return 0;
    }

    // Purge any stale instances before loading fresh model
    if (g_translator != nullptr) { delete g_translator; g_translator = nullptr; }
    if (g_spm_source != nullptr) { delete g_spm_source; g_spm_source = nullptr; }
    if (g_spm_target != nullptr) { delete g_spm_target; g_spm_target = nullptr; }

    g_model_dir = target_model_dir;
    std::string src_path = g_model_dir + "/model.SRC";
    std::string tgt_path = g_model_dir + "/model.TGT";

    g_spm_source = new sentencepiece::SentencePieceProcessor();
    g_spm_target = new sentencepiece::SentencePieceProcessor();

    LOGI("Loading Source Dictionary: %s", src_path.c_str());
    if (!g_spm_source->Load(src_path).ok()) {
        LOGE("Failed to load source dictionary at %s", src_path.c_str());
        delete g_spm_source; g_spm_source = nullptr;
        delete g_spm_target; g_spm_target = nullptr;
        g_model_dir = "";
        return 1;
    }

    LOGI("Loading Target Dictionary: %s", tgt_path.c_str());
    if (!g_spm_target->Load(tgt_path).ok()) {
        LOGE("Failed to load target dictionary at %s", tgt_path.c_str());
        delete g_spm_source; g_spm_source = nullptr;
        delete g_spm_target; g_spm_target = nullptr;
        g_model_dir = "";
        return 2;
    }

    ctranslate2::ReplicaPoolConfig pool_config;
    unsigned int hardware_threads = std::thread::hardware_concurrency();
    pool_config.num_threads_per_replica = std::max(4u, hardware_threads);

    try {
        ctranslate2::models::ModelLoader loader(g_model_dir);
        loader.compute_type = ctranslate2::ComputeType::AUTO;
        g_translator = new ctranslate2::Translator(loader, pool_config);
        LOGI("Engine 1 (Eng->Indic) successfully initialized in RAM with %zu threads (Quantized INT8/AUTO).", pool_config.num_threads_per_replica);
    } catch (const std::exception& e) {
        LOGE("Failed to boot CTranslate2 Engine 1: %s", e.what());
        delete g_spm_source; g_spm_source = nullptr;
        delete g_spm_target; g_spm_target = nullptr;
        g_model_dir = "";
        return 3;
    }

    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_indicT_MainActivity_unloadNativeTranslator(JNIEnv* env, jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);

    if (g_spm_source != nullptr) {
        delete g_spm_source;
        g_spm_source = nullptr;
    }
    if (g_spm_target != nullptr) {
        delete g_spm_target;
        g_spm_target = nullptr;
    }
    if (g_translator != nullptr) {
        delete g_translator;
        g_translator = nullptr;
    }

    g_model_dir = "";
    LOGI("Engine 1 (Eng->Indic) Native RAM wiped successfully.");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_indicT_MainActivity_translateNativeText(
        JNIEnv* env, jobject /* this */, jstring text, jstring src_lang, jstring tgt_lang, jint beam_size) {

    std::lock_guard<std::mutex> lock(g_engine_mutex);

    const char* native_text = env->GetStringUTFChars(text, nullptr);
    const char* native_src = env->GetStringUTFChars(src_lang, nullptr);
    const char* native_tgt = env->GetStringUTFChars(tgt_lang, nullptr);

    if (g_translator == nullptr || g_model_dir.empty() || g_spm_source == nullptr || g_spm_target == nullptr) {
        LOGE("translateNativeText called but Engine 1 is NULL in RAM.");
        env->ReleaseStringUTFChars(text, native_text);
        env->ReleaseStringUTFChars(src_lang, native_src);
        env->ReleaseStringUTFChars(tgt_lang, native_tgt);
        return env->NewStringUTF("Error: AI Engine is NULL. The models failed to load.");
    }

    std::string input_text(native_text);
    if (!input_text.empty() && input_text.back() != '.' && input_text.back() != '?' && input_text.back() != '!') {
        input_text += ".";
    }

    std::vector<std::string> sentences = split_into_chunks(input_text);
    std::vector<std::vector<std::string>> batch;
    std::vector<std::vector<std::string>> target_prefixes;

    size_t max_raw_token_len = 0;
    for (const auto& sentence : sentences) {
        std::vector<std::string> raw_tokens;
        g_spm_source->Encode(sentence, &raw_tokens);
        max_raw_token_len = std::max(max_raw_token_len, raw_tokens.size());

        std::vector<std::string> source_tokens = {std::string(native_src)};
        for (const auto& t : raw_tokens) {
            source_tokens.push_back(t);
        }
        source_tokens.push_back("</s>");
        source_tokens.push_back(std::string(native_tgt));

        batch.push_back(source_tokens);
        target_prefixes.push_back({std::string(native_tgt)});
    }

    std::string final_stitched_text = "";

    try {
        ctranslate2::TranslationOptions options;
        options.beam_size = (beam_size > 0 && beam_size <= 5) ? static_cast<size_t>(beam_size) : 1;
        options.max_decoding_length = std::min(static_cast<size_t>(128), std::max(static_cast<size_t>(15), max_raw_token_len * 2 + 5));
        options.end_token = "</s>";
        options.repetition_penalty = 1.3;
        options.replace_unknowns = true;

        auto results = g_translator->translate_batch(batch, target_prefixes, options);

        for (const auto& result : results) {
            std::vector<std::string> output_tokens = result.hypotheses[0];

            while (!output_tokens.empty() &&
                   (output_tokens.front().find("Deva") != std::string::npos ||
                    output_tokens.front().find("Olck") != std::string::npos ||
                    output_tokens.front() == "eng_Latn" ||
                    output_tokens.front() == "</s>" ||
                    output_tokens.front() == "<pad>")) {
                output_tokens.erase(output_tokens.begin());
            }

            std::string decoded_chunk;
            g_spm_target->Decode(output_tokens, &decoded_chunk);

            // Strip unicode unknown symbols and control chars
            std::string utf8_unk = "\xE2\x81\x87";
            if (decoded_chunk.find(utf8_unk) == 0) {
                decoded_chunk.erase(0, 3);
            }
            std::string android_unk = "\xEF\xBF\xBD";
            while (decoded_chunk.find(android_unk) != std::string::npos) {
                decoded_chunk.replace(decoded_chunk.find(android_unk), 3, "");
            }

            while (!decoded_chunk.empty() && (decoded_chunk[0] == '?' || decoded_chunk[0] == ' ' || decoded_chunk[0] == ',')) {
                decoded_chunk.erase(0, 1);
            }

            final_stitched_text += decoded_chunk + " ";
        }
    } catch (const std::exception& e) {
        LOGE("Translation Exception in Engine 1: %s", e.what());
        final_stitched_text = std::string("Error: ") + e.what();
    }

    env->ReleaseStringUTFChars(text, native_text);
    env->ReleaseStringUTFChars(src_lang, native_src);
    env->ReleaseStringUTFChars(tgt_lang, native_tgt);

    if (!final_stitched_text.empty() && final_stitched_text.back() == ' ') {
        final_stitched_text.pop_back();
    }

    return env->NewStringUTF(final_stitched_text.c_str());
}