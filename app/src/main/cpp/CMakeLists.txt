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

if(NOT DEFINED LLAMA_TAG)
    set(LLAMA_TAG b8720)
endif()
option(SCHOOLHUB_VISION "Build mtmd (vision) support" ON)

include(FetchContent)
FetchContent_Declare(
    llama
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        ${LLAMA_TAG}
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(llama)

add_library(schoolhub_llama SHARED llama_jni.cpp)
target_link_libraries(schoolhub_llama PRIVATE llama log android)

# Зрение: собираем libmtmd прямо из исходников llama.cpp/tools/mtmd (без CLI и common).
set(MTMD_DIR ${llama_SOURCE_DIR}/tools/mtmd)
if(SCHOOLHUB_VISION AND EXISTS ${MTMD_DIR}/mtmd.cpp)
    file(GLOB MTMD_SRC ${MTMD_DIR}/*.cpp ${MTMD_DIR}/models/*.cpp)
    list(FILTER MTMD_SRC EXCLUDE REGEX ".*(cli|deprecation|test).*\\.cpp$")
    add_library(schoolhub_mtmd STATIC ${MTMD_SRC})
    target_include_directories(schoolhub_mtmd PUBLIC ${MTMD_DIR} ${llama_SOURCE_DIR}/vendor ${llama_SOURCE_DIR}/include)
    target_link_libraries(schoolhub_mtmd PUBLIC llama ggml)
    target_compile_definitions(schoolhub_llama PRIVATE SCHOOLHUB_VISION=1)
    target_link_libraries(schoolhub_llama PRIVATE schoolhub_mtmd)
    message(STATUS "SchoolHub: vision (mtmd) ON")
endif()
