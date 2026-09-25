cmake_minimum_required(VERSION 3.22.1)
project(schoolhub_llama C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
# ggml в Debug работает в десятки раз медленнее — всегда собираем Release
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
add_compile_options(-O3 -DNDEBUG)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

include(FetchContent)
FetchContent_Declare(
    llama
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        b6550
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(llama)

add_library(schoolhub_llama SHARED llama_jni.cpp)
target_link_libraries(schoolhub_llama PRIVATE llama log android)
