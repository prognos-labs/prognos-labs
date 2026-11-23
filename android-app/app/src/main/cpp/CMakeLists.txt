cmake_minimum_required(VERSION 3.22.1)

project("prognos_whisper")

# The speech library (v1.1.13): the open-source speech library,
# built from its own sources at build time, so the app tree stays
# small and the library version is pinned. The model file is NOT
# bundled: it downloads on first use and is checked by size and
# hash before it is used.
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
