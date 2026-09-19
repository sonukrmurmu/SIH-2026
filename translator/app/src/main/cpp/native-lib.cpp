#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <chrono>
#include <android/log.h>
#include <ctranslate2/translator.h>
#include <ctranslate2/replica_pool.h>
#include <sentencepiece_processor.h>
#include <thread>
#include <algorithm>

#define LOG_TAG "SIH_AI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static sentencepiece::SentencePieceProcessor* g_spm_source = nullptr;
static sentencepiece::SentencePieceProcessor* g_spm_target = nullptr;
static std::string g_model_dir = "";
static ctranslate2::Translator* g_translator = nullptr;

std::vector<std::string> split_into_chunks(const std::string& text) {
    std::vector<std::string> chunks;
    std::stringstream ss(text);
    std::string chunk;
    while (std::getline(ss, chunk, '.')) {
        if (!chunk.empty()) chunks.push_back(chunk + ".");
    }
    if (chunks.empty()) chunks.push_back(text);
    return chunks;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_myapplication_MainActivity_initNativeTranslator(
        JNIEnv* env, jobject /* this */, jstring model_dir, jstring /* unused_spm_path */) {

    if (g_spm_source != nullptr) { delete g_spm_source; g_spm_source = nullptr; }
    if (g_spm_target != nullptr) { delete g_spm_target; g_spm_target = nullptr; }

    const char* native_model = env->GetStringUTFChars(model_dir, 0);
    g_model_dir = std::string(native_model);

    std::string src_path = g_model_dir + "/model.SRC";
    std::string tgt_path = g_model_dir + "/model.TGT";

    g_spm_source = new sentencepiece::SentencePieceProcessor();
    g_spm_target = new sentencepiece::SentencePieceProcessor();

    LOGI("Loading Source Dictionary: %s", src_path.c_str());
    if (!g_spm_source->Load(src_path).ok()) return 1;

    LOGI("Loading Target Dictionary: %s", tgt_path.c_str());
    if (!g_spm_target->Load(tgt_path).ok()) return 2;

    g_translator = new ctranslate2::Translator(
            g_model_dir,
            ctranslate2::Device::CPU,
            ctranslate2::ComputeType::INT8
    );
    env->ReleaseStringUTFChars(model_dir, native_model);
    return 0;
}


extern "C" JNIEXPORT void JNICALL
Java_com_example_myapplication_MainActivity_unloadNativeTranslator(JNIEnv* env, jobject /* this */) {
    if (g_spm_source != nullptr) { delete g_spm_source; g_spm_source = nullptr; }
    if (g_spm_target != nullptr) { delete g_spm_target; g_spm_target = nullptr; }
    g_model_dir = "";
    LOGI("Native RAM wiped successfully.");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_myapplication_MainActivity_translateNativeText(
        JNIEnv* env, jobject /* this */, jstring text, jstring tgt_lang) {

    const char* native_text = env->GetStringUTFChars(text, 0);
    const char* native_lang = env->GetStringUTFChars(tgt_lang, 0);
    LOGI("LAYER 2 [C++ ENTRY]: Received string from Kotlin: %s", native_text);

    if (g_model_dir.empty() || g_spm_source == nullptr || g_spm_target == nullptr) {
        return env->NewStringUTF("Error: Engine paths not initialized.");
    }

    std::string input_text(native_text);
    if (!input_text.empty() && input_text.back() != '.' && input_text.back() != '?' && input_text.back() != '!') {
        input_text += ".";
    }

    std::string final_stitched_text = "";
    std::vector<std::string> sentences = split_into_chunks(input_text);


    try {
        for (const auto& sentence : sentences) {
            std::vector<std::string> raw_tokens;
            g_spm_source->Encode(sentence, &raw_tokens);

            std::vector<std::string> source_tokens = {"eng_Latn"};
            for (const auto& t : raw_tokens) source_tokens.push_back(t);
            source_tokens.push_back("</s>");
            source_tokens.push_back(std::string(native_lang));

            std::vector<std::vector<std::string>> batch = {source_tokens};
            std::vector<std::vector<std::string>> target_prefix = {{std::string(native_lang)}};

            ctranslate2::TranslationOptions options;
            options.beam_size = 3;
            options.max_decoding_length = 256;
            options.end_token = "</s>";
            options.repetition_penalty = 1.5;
            options.replace_unknowns = true;

            LOGI("LAYER 3 [ENGINE START]: Passing data to AI Engine now...");
            auto start_time = std::chrono::high_resolution_clock::now();

            auto results = g_translator->translate_batch(batch, target_prefix, options);

            auto end_time = std::chrono::high_resolution_clock::now();
            auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
            LOGI("LAYER 4 [ENGINE STOP]: Output generated in %lld ms.", (long long)duration);
            std::vector<std::string> output_tokens = results[0].hypotheses[0];

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

            // Bulletproof Cleanup
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
        LOGE("Translation Exception: %s", e.what());
        final_stitched_text = std::string("Error: ") + e.what();
    }

    env->ReleaseStringUTFChars(text, native_text);
    env->ReleaseStringUTFChars(tgt_lang, native_lang);

    if (!final_stitched_text.empty() && final_stitched_text.back() == ' ') {
        final_stitched_text.pop_back();
    }

    return env->NewStringUTF(final_stitched_text.c_str());
}