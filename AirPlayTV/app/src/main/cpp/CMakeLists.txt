# CMake configuration for AirPlay native library
# (Stub - será implementado na Fase 4)

cmake_minimum_required(VERSION 3.22.1)

project("airplay-native")

# Placeholder native library
# TODO: Fase 4 - Adicionar biblioteca AirPlay (RPiPlay/UxPlay)
# TODO: Fase 4 - Criar JNI bridge

add_library(
    airplay-native
    SHARED
    native-lib.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    airplay-native
    ${log-lib}
)
