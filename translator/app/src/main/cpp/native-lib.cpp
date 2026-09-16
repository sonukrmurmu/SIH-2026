#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <android/log.h>
#include <ctranslate2/translator.h>
#include <sentencepiece_processor.h>

#define LOG_TAG "SIH_AI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static sentencepiece::SentencePieceProcessor g_spm_source;
static sentencepiece::SentencePieceProcessor g_spm_target;
static std::string g_model_dir = "";

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

    const char* native_model = env->GetStringUTFChars(model_dir, 0);
    g_model_dir = std::string(native_model);

    std::string src_path = g_model_dir + "/model.SRC";
    std::string tgt_path = g_model_dir + "/model.TGT";

    LOGI("Loading Source Dictionary: %s", src_path.c_str());
    if (!g_spm_source.Load(src_path).ok()) return 1;

    LOGI("Loading Target Dictionary: %s", tgt_path.c_str());
    if (!g_spm_target.Load(tgt_path).ok()) return 2;

    env->ReleaseStringUTFChars(model_dir, native_model);
    return 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_myapplication_MainActivity_translateNativeText(
        JNIEnv* env, jobject /* this */, jstring text, jstring tgt_lang) {

    const char* native_text = env->GetStringUTFChars(text, 0);
    const char* native_lang = env->GetStringUTFChars(tgt_lang, 0);

    if (g_model_dir.empty()) return env->NewStringUTF("Error: Engine paths not initialized.");

    // Auto-punctuation to stop the AI from outputting blank spaces for single words
    std::string input_text(native_text);
    if (!input_text.empty() && input_text.back() != '.' && input_text.back() != '?' && input_text.back() != '!') {
        input_text += ".";
    }

    std::string final_stitched_text = "";
    std::vector<std::string> sentences = split_into_chunks(input_text);

    try {
        ctranslate2::Translator translator(g_model_dir, ctranslate2::Device::CPU, ctranslate2::ComputeType::INT8);

        for (const auto& sentence : sentences) {
            std::vector<std::string> raw_tokens;
            g_spm_source.Encode(sentence, &raw_tokens);

            std::vector<std::string> source_tokens = {"eng_Latn", std::string(native_lang)};
            for (const auto& t : raw_tokens) source_tokens.push_back(t);
            source_tokens.push_back("</s>");

            std::vector<std::vector<std::string>> batch = {source_tokens};
            std::vector<std::vector<std::string>> target_prefix = {{std::string(native_lang)}};

            // ---> SPEED FIX: Greedy Search for instant translation speed
            ctranslate2::TranslationOptions options;
            options.beam_size = 1;
            options.max_decoding_length = 256;

            auto results = translator.translate_batch(batch, target_prefix, options);
            std::vector<std::string> output_tokens = results[0].hypotheses[0];

            if (!output_tokens.empty() && output_tokens[0] == native_lang) {
                output_tokens.erase(output_tokens.begin());
            }

            std::string decoded_chunk;
            g_spm_target.Decode(output_tokens, &decoded_chunk);

            // ---> NEW BULLETPROOF CLEANUP: Erase the UTF-8 SentencePiece Unknown Token (⁇) and standard question marks
            std::string utf8_unk = "\xE2\x81\x87";
            if (decoded_chunk.find(utf8_unk) == 0) {
                decoded_chunk.erase(0, 3);
            }
            while (!decoded_chunk.empty() && (decoded_chunk[0] == '?' || decoded_chunk[0] == ' ')) {
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

    // Strip the final trailing space added by the loop
    if (!final_stitched_text.empty() && final_stitched_text.back() == ' ') {
        final_stitched_text.pop_back();
    }

    return env->NewStringUTF(final_stitched_text.c_str());
}