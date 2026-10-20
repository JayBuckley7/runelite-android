cmake_minimum_required(VERSION 3.22.1)
project(hdmemory C CXX)
add_library(hdmemory SHARED hd_memory.c)
target_link_libraries(hdmemory GLESv3 EGL)
target_compile_options(hdmemory PRIVATE -Wall -Wextra -Werror)

# 117 HD Vulkan backend. Shaders are compiled at runtime (117 HD generates them from config),
# so shaderc is linked in. Its static libraries are built from the NDK's own sources by the
# :android:buildShaderc task into android/.shaderc.
set(SHADERC_LIB_DIR ${CMAKE_SOURCE_DIR}/../../../.shaderc/obj/local/${ANDROID_ABI})
if(NOT EXISTS ${SHADERC_LIB_DIR}/libshaderc.a)
    message(FATAL_ERROR "shaderc not built for ${ANDROID_ABI}; run :android:buildShaderc (expected ${SHADERC_LIB_DIR})")
endif()

add_library(hdvk SHARED
    hdvk/context.cpp
    hdvk/resources.cpp
    hdvk/pipeline.cpp
    hdvk/record.cpp
    hdvk/jni.cpp)
target_include_directories(hdvk PRIVATE ${ANDROID_NDK}/sources/third_party/shaderc/libshaderc/include)
target_compile_features(hdvk PRIVATE cxx_std_17)
target_compile_options(hdvk PRIVATE -Wall -Wno-unused-parameter -Wno-missing-field-initializers -fno-exceptions -O2)
target_compile_definitions(hdvk PRIVATE VK_USE_PLATFORM_ANDROID_KHR)
target_link_libraries(hdvk
    -Wl,--start-group
    ${SHADERC_LIB_DIR}/libshaderc.a
    ${SHADERC_LIB_DIR}/libshaderc_util.a
    ${SHADERC_LIB_DIR}/libglslang.a
    ${SHADERC_LIB_DIR}/libOSDependent.a
    ${SHADERC_LIB_DIR}/libOGLCompiler.a
    ${SHADERC_LIB_DIR}/libHLSL.a
    ${SHADERC_LIB_DIR}/libSPIRV.a
    ${SHADERC_LIB_DIR}/libSPIRV-Tools-opt.a
    ${SHADERC_LIB_DIR}/libSPIRV-Tools.a
    -Wl,--end-group
    vulkan android log)
# Keep only the JNI entry points exported
target_link_options(hdvk PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
