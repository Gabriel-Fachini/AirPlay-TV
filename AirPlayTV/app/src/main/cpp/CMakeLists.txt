# CMake configuration for AirPlay native library
# Fase 4: Servidor RTSP/AirPlay simplificado

cmake_minimum_required(VERSION 3.22.1)

project("airplay-native")

# Habilitar C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

set(THIRD_PARTY_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/vendor)
set(UXPLAY_DIR ${THIRD_PARTY_DIR}/uxplay_lib)

add_library(
    playfair_vendor
    STATIC
    third_party/playfair/hand_garble.c
    third_party/playfair/modified_md5.c
    third_party/playfair/omg_hax.c
    third_party/playfair/playfair.c
    third_party/playfair/sap_hash.c
)

target_include_directories(
    playfair_vendor
    PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/playfair
)

add_library(
    llhttp_vendor
    STATIC
    ${UXPLAY_DIR}/lib/llhttp/api.c
    ${UXPLAY_DIR}/lib/llhttp/http.c
    ${UXPLAY_DIR}/lib/llhttp/llhttp.c
)

target_include_directories(
    llhttp_vendor
    PUBLIC
    ${UXPLAY_DIR}/lib/llhttp
)

add_library(
    plist_vendor
    STATIC
    third_party/vendor/libplist/libcnary/cnary.c
    third_party/vendor/libplist/libcnary/node.c
    third_party/vendor/libplist/libcnary/node_list.c
    third_party/vendor/libplist/src/base64.c
    third_party/vendor/libplist/src/bplist.c
    third_party/vendor/libplist/src/bytearray.c
    third_party/vendor/libplist/src/hashtable.c
    third_party/vendor/libplist/src/jsmn.c
    third_party/vendor/libplist/src/jplist.c
    third_party/vendor/libplist/src/oplist.c
    third_party/vendor/libplist/src/out-default.c
    third_party/vendor/libplist/src/out-limd.c
    third_party/vendor/libplist/src/out-plutil.c
    third_party/vendor/libplist/src/plist.c
    third_party/vendor/libplist/src/ptrarray.c
    third_party/vendor/libplist/src/time64.c
    third_party/vendor/libplist/src/xplist.c
)

target_compile_definitions(
    plist_vendor
    PUBLIC
    LIBPLIST_STATIC
    HAVE_STRNDUP
    PACKAGE_VERSION="2.3.0"
)

target_include_directories(
    plist_vendor
    PUBLIC
    ${THIRD_PARTY_DIR}/libplist/include
    ${THIRD_PARTY_DIR}/libplist/src
    ${THIRD_PARTY_DIR}/libplist/libcnary/include
)

set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
add_subdirectory(${THIRD_PARTY_DIR}/boringssl ${CMAKE_CURRENT_BINARY_DIR}/boringssl EXCLUDE_FROM_ALL)

# Biblioteca nativa AirPlay
add_library(
    airplay-native
    SHARED
    jni/native-lib.cpp
    jni/JniBridge.cpp
    ux_raop_core/src/UxRaopCore.cpp
    ux_raop_core/android/dnssd_stub.cpp
    core/AirPlayServer.cpp
    core/SessionManager.cpp
    core/MediaOrchestrator.cpp
    # Protocol modules
    protocol/protocol_constants.cpp
    protocol/fairplay_handler.cpp
    protocol/rtsp_handler.cpp
    protocol/routes/RouteDispatcher.cpp
    protocol/routes/ServerInfoHandler.cpp
    protocol/routes/PhotoRouteHandler.cpp
    protocol/routes/VideoRouteHandler.cpp
    # Network modules
    network/network_utils.cpp
    network/rtp_receiver.cpp
    network/mirror_server.cpp
    network/ntp_client.cpp
    # UxPlay core
    ${UXPLAY_DIR}/lib/airplay_video.c
    ${UXPLAY_DIR}/lib/byteutils.c
    ${UXPLAY_DIR}/lib/compat.c
    ${UXPLAY_DIR}/lib/crypto.c
    ${UXPLAY_DIR}/lib/fairplay_playfair.c
    ${UXPLAY_DIR}/lib/http_request.c
    ${UXPLAY_DIR}/lib/http_response.c
    ${UXPLAY_DIR}/lib/httpd.c
    ${UXPLAY_DIR}/lib/logger.c
    ${UXPLAY_DIR}/lib/mirror_buffer.c
    ${UXPLAY_DIR}/lib/netutils.c
    ${UXPLAY_DIR}/lib/pairing.c
    ${UXPLAY_DIR}/lib/raop.c
    ${UXPLAY_DIR}/lib/raop_buffer.c
    ${UXPLAY_DIR}/lib/raop_ntp.c
    ${UXPLAY_DIR}/lib/raop_rtp.c
    ${UXPLAY_DIR}/lib/raop_rtp_mirror.c
    ${UXPLAY_DIR}/lib/srp.c
    ${UXPLAY_DIR}/lib/utils.c
)

# Bibliotecas do sistema
find_library(log-lib log)
find_library(android-lib android)

# Linkar bibliotecas
target_link_libraries(
    airplay-native
    ${log-lib}
    ${android-lib}
    crypto
    plist_vendor
    llhttp_vendor
    playfair_vendor
)

# Include directories
target_include_directories(
    airplay-native
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/ux_raop_core/include
    ${UXPLAY_DIR}/lib
    ${UXPLAY_DIR}/lib/playfair
    ${UXPLAY_DIR}/lib/llhttp
    ${CMAKE_CURRENT_SOURCE_DIR}/protocol
    ${CMAKE_CURRENT_SOURCE_DIR}/network
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/playfair
    ${THIRD_PARTY_DIR}/libplist/include
    ${THIRD_PARTY_DIR}/libplist/src
    ${THIRD_PARTY_DIR}/libplist/libcnary/include
    ${THIRD_PARTY_DIR}/boringssl/include
)
