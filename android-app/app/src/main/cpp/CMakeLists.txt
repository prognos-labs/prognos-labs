cmake_minimum_required(VERSION 3.22.1)

project("prognos_whisper")

# On-device speech library (whisper.cpp v1.7.6), built from source at build
# time with a pinned tag. The model file is bundled in app/src/main/assets/,
# copied to app-private storage on first use, and verified by size and
# SHA-256 in WhisperModel.kt before it is loaded.
include(FetchContent)
FetchContent_Declare(
    whisper
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG v1.7.6
    GIT_SHALLOW ON
)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(whisper)

add_library(prognos_whisper SHARED prognos_whisper_jni.cpp)

find_library(log-lib log)

target_link_libraries(prognos_whisper whisper ${log-lib} android)

