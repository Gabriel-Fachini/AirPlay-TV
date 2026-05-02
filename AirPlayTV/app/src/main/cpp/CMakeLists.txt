# CMake configuration for AirPlay native library
# Fase 4: Servidor RTSP/AirPlay simplificado

cmake_minimum_required(VERSION 3.22.1)

project("airplay-native")

# Habilitar C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Biblioteca nativa AirPlay
add_library(
    airplay-native
    SHARED
    native-lib.cpp
    airplay_server.cpp
)

# Bibliotecas do sistema
find_library(log-lib log)

# Linkar bibliotecas
target_link_libraries(
    airplay-native
    ${log-lib}
)

# Include directories
target_include_directories(
    airplay-native
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)
