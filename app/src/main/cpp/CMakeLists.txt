cmake_minimum_required(VERSION 3.22.1)
project(meetmate_whisper)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(WHISPER_CPP_DIR "${MEETMATE_WHISPER_CPP_DIR}" CACHE PATH "Path to a whisper.cpp checkout")
set(WHISPER_HEADER "${WHISPER_CPP_DIR}/include/whisper.h")

# Clone whisper.cpp into third_party/whisper.cpp (or pass -PWHISPER_CPP_DIR=...) for the real engine.
# The JNI shim still builds without it so Android Studio can import and run the fallback while native
# sources are being installed. When the checkout exists, whisper.cpp supplies the actual on-device model.
if(EXISTS "${WHISPER_HEADER}")
    set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    add_subdirectory("${WHISPER_CPP_DIR}" whisper-cpp EXCLUDE_FROM_ALL)
    add_library(meetmate_whisper SHARED whisper_jni.cpp)
    target_include_directories(meetmate_whisper PRIVATE "${WHISPER_CPP_DIR}" "${WHISPER_CPP_DIR}/include" "${WHISPER_CPP_DIR}/src" "${WHISPER_CPP_DIR}/ggml/include" "${WHISPER_CPP_DIR}/ggml/src")
    if(TARGET whisper)
        target_link_libraries(meetmate_whisper whisper)
    elseif(TARGET whisper::whisper)
        target_link_libraries(meetmate_whisper whisper::whisper)
    endif()
    target_compile_definitions(meetmate_whisper PRIVATE MEETMATE_HAS_WHISPER=1)
else()
    add_library(meetmate_whisper SHARED whisper_jni.cpp)
endif()

find_library(log-lib log)
find_library(android-lib android)
target_link_libraries(meetmate_whisper ${log-lib} ${android-lib})
