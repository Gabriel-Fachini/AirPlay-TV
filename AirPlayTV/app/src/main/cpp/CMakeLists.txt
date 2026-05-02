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
    # Protocol modules
    protocol/protocol_constants.cpp
    protocol/fairplay_handler.cpp
    protocol/rtsp_handler.cpp
    # Network modules
    network/network_utils.cpp
    network/rtp_receiver.cpp
    network/mirror_server.cpp
    network/ntp_client.cpp
    # Third party
    third_party/playfair/hand_garble.c
    third_party/playfair/modified_md5.c
    third_party/playfair/omg_hax.c
    third_party/playfair/playfair.c
    third_party/playfair/sap_hash.c
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
    ${CMAKE_CURRENT_SOURCE_DIR}/protocol
    ${CMAKE_CURRENT_SOURCE_DIR}/network
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/playfair
)
