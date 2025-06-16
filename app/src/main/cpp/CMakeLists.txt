 cmake_minimum_required(VERSION 3.18.1)

 project(stellar_eyes_native LANGUAGES CXX)

 set(ncnn_DIR "${CMAKE_SOURCE_DIR}/ncnn-20250503-android-vulkan-shared/${CMAKE_ANDROID_ARCH_ABI}/lib/cmake/ncnn")

 find_package(ncnn REQUIRED)

 add_library(
         stellar_eyes_native
         SHARED
         main.cpp
#         StellarEyes.cpp
 )

 target_link_libraries(
         stellar_eyes_native
         PRIVATE # Or PUBLIC/INTERFACE as needed
         ncnn  # This is a common way imported targets are named
         # protobuf-lite might be automatically handled by ncnn::ncnn
         # If not, and you have a libprotobuf-lite.so, you'd find and link it too.
         android
         log
 )