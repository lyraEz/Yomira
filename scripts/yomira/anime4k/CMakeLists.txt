# Desktop build of the Anime4K engine (app/src/main/cpp/anime4k) for checking it against
# libplacebo/mpv on a PC: see README.md in this directory.
cmake_minimum_required(VERSION 3.16)
project(a4k_cli LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
set(ENGINE ${CMAKE_CURRENT_SOURCE_DIR}/../../../app/src/main/cpp/anime4k)
add_executable(a4k_cli a4k_cli.cpp ${ENGINE}/hook_shader.cpp ${ENGINE}/hook_pipeline.cpp)
target_include_directories(a4k_cli PRIVATE ${ENGINE})
target_link_libraries(a4k_cli EGL GLESv2)
