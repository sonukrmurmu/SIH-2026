cmake_minimum_required(VERSION 3.18.1)
project("sihtranslator")

# ---> NEW FIX: Enforce Position Independent Code (-fPIC) across all dependencies
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
add_compile_options("-fPIC")

# ---> THE ULTIMATE FIX: Generate a compatibility file on the fly
# This forces the fix into EVERY file, bypassing CMake isolation and Bash errors.
file(WRITE ${CMAKE_CURRENT_BINARY_DIR}/android_compat.h "#pragma once\n#define pthread_setaffinity_np(a, b, c) 0\n")
add_compile_options("-include" "${CMAKE_CURRENT_BINARY_DIR}/android_compat.h")

# Globally link the Android log library to all sub-projects
link_libraries(log)

# 1. Download and build SentencePiece for Android
include(FetchContent)
FetchContent_Declare(
        sentencepiece
        GIT_REPOSITORY https://github.com/google/sentencepiece.git
        GIT_TAG        v0.2.0
)
set(SPM_ENABLE_SHARED OFF CACHE BOOL "Disable shared SPM" FORCE)
set(SPM_ENABLE_TCMALLOC OFF CACHE BOOL "Disable TCMalloc" FORCE)
FetchContent_MakeAvailable(sentencepiece)

# 2. Download and build CTranslate2 for Android
FetchContent_Declare(
        ctranslate2
        GIT_REPOSITORY https://github.com/OpenNMT/CTranslate2.git
        GIT_TAG        v4.4.0
)
set(WITH_RUY ON CACHE BOOL "Use ARM mobile math engine" FORCE)
set(WITH_MKL OFF CACHE BOOL "Disable Intel desktop math" FORCE)
set(WITH_OPENBLAS OFF CACHE BOOL "Disable desktop OpenBLAS" FORCE)
set(WITH_OPENMP OFF CACHE BOOL "Disable OpenMP to prevent NDK errors" FORCE)
set(OPENMP_RUNTIME "NONE" CACHE STRING "Strictly disable Intel OpenMP" FORCE)
set(WITH_CPU_DISPATCH OFF CACHE BOOL "Disable CPU dispatch" FORCE)
set(BUILD_CLI OFF CACHE BOOL "Do not build command line tools" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static libraries" FORCE)
FetchContent_MakeAvailable(ctranslate2)

# 3. Create your app's JNI bridge library
add_library(
        sihtranslator
        SHARED
        native-lib.cpp)

# Tell the compiler where the SentencePiece headers are
target_include_directories(sihtranslator PRIVATE
        ${sentencepiece_SOURCE_DIR}/src
        ${ctranslate2_SOURCE_DIR}/include
)

# 4. Link the AI engines and Android logging to your native bridge
target_link_libraries(
        sihtranslator
        sentencepiece-static
        ctranslate2
        log)

# 5. Create Engine 2: Indic-to-English JNI bridge
add_library(
        sih_indic_to_en
        SHARED
        native-indic-en.cpp)

target_include_directories(sih_indic_to_en PRIVATE
        ${sentencepiece_SOURCE_DIR}/src
        ${ctranslate2_SOURCE_DIR}/include
)

target_link_libraries(
        sih_indic_to_en
        sentencepiece-static
        ctranslate2
        log)