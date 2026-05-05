#include "dnssd.h"
#include "dnssdint.h"

#include <algorithm>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <cstdio>
#include <string>
#include <vector>

struct dnssd_s {
    std::string name;
    std::string hwAddrRaw;
    std::string hwAddrText;
    std::string pk;
    uint64_t features = 0;
    unsigned short raopPort = 0;
    unsigned short airplayPort = 0;
    std::vector<char> raopTxt;
    std::vector<char> airplayTxt;
};

static void append_txt(std::vector<char>& out, const std::string& entry) {
    const auto length = static_cast<unsigned char>(std::min<size_t>(entry.size(), 255));
    out.push_back(static_cast<char>(length));
    out.insert(out.end(), entry.begin(), entry.begin() + length);
}

static uint64_t parse_features() {
    const auto high = static_cast<uint64_t>(strtoull(FEATURES_1, nullptr, 0));
    const auto low = static_cast<uint64_t>(strtoull(FEATURES_2, nullptr, 0));
    return (low << 32U) | high;
}

static void rebuild_raop_txt(dnssd_t* dnssd) {
    dnssd->raopTxt.clear();
    append_txt(dnssd->raopTxt, std::string("ch=") + RAOP_CH);
    append_txt(dnssd->raopTxt, std::string("cn=") + RAOP_CN);
    append_txt(dnssd->raopTxt, std::string("da=") + RAOP_DA);
    append_txt(dnssd->raopTxt, std::string("et=") + RAOP_ET);
    append_txt(dnssd->raopTxt, std::string("vv=") + RAOP_VV);
    append_txt(dnssd->raopTxt, std::string("ft=") + FEATURES_1 + "," + FEATURES_2);
    append_txt(dnssd->raopTxt, std::string("am=") + GLOBAL_MODEL);
    append_txt(dnssd->raopTxt, std::string("md=") + RAOP_MD);
    append_txt(dnssd->raopTxt, std::string("rhd=") + RAOP_RHD);
    append_txt(dnssd->raopTxt, "pw=false");
    append_txt(dnssd->raopTxt, std::string("sr=") + RAOP_SR);
    append_txt(dnssd->raopTxt, std::string("ss=") + RAOP_SS);
    append_txt(dnssd->raopTxt, std::string("sv=") + RAOP_SV);
    append_txt(dnssd->raopTxt, std::string("tp=") + RAOP_TP);
    append_txt(dnssd->raopTxt, std::string("txtvers=") + RAOP_TXTVERS);
    append_txt(dnssd->raopTxt, std::string("sf=") + RAOP_SF);
    append_txt(dnssd->raopTxt, std::string("vs=") + RAOP_VS);
    append_txt(dnssd->raopTxt, std::string("vn=") + RAOP_VN);
    if (!dnssd->pk.empty()) {
        append_txt(dnssd->raopTxt, std::string("pk=") + dnssd->pk);
    }
}

static void rebuild_airplay_txt(dnssd_t* dnssd) {
    dnssd->airplayTxt.clear();
    append_txt(dnssd->airplayTxt, std::string("deviceid=") + dnssd->hwAddrText);
    append_txt(dnssd->airplayTxt, std::string("features=") + FEATURES_1 + "," + FEATURES_2);
    append_txt(dnssd->airplayTxt, std::string("flags=") + AIRPLAY_FLAGS);
    append_txt(dnssd->airplayTxt, std::string("model=") + GLOBAL_MODEL);
    append_txt(dnssd->airplayTxt, std::string("pi=") + AIRPLAY_PI);
    append_txt(dnssd->airplayTxt, std::string("pk=") + dnssd->pk);
    append_txt(dnssd->airplayTxt, std::string("srcvers=") + AIRPLAY_SRCVERS);
    append_txt(dnssd->airplayTxt, std::string("vv=") + AIRPLAY_VV);
}

extern "C" {

dnssd_t* dnssd_init(
    const char* name,
    int name_len,
    const char* hw_addr,
    int hw_addr_len,
    int* error,
    unsigned char /* pin_pw */) {
    auto* dnssd = new dnssd_s();
    dnssd->name.assign(name, static_cast<size_t>(name_len));
    dnssd->hwAddrRaw.assign(hw_addr, static_cast<size_t>(hw_addr_len));
    char buffer[3 * 6] = {};
    int offset = 0;
    for (int index = 0; index < hw_addr_len; ++index) {
        const auto value = static_cast<unsigned char>(hw_addr[index]);
        offset += std::snprintf(
            buffer + offset,
            sizeof(buffer) - static_cast<size_t>(offset),
            index == 0 ? "%02X" : ":%02X",
            value);
    }
    dnssd->hwAddrText.assign(buffer, static_cast<size_t>(offset));
    dnssd->features = parse_features();
    rebuild_raop_txt(dnssd);
    rebuild_airplay_txt(dnssd);
    if (error != nullptr) {
        *error = DNSSD_ERROR_NOERROR;
    }
    return dnssd;
}

int dnssd_register_raop(dnssd_t* dnssd, unsigned short port) {
    dnssd->raopPort = port;
    rebuild_raop_txt(dnssd);
    return DNSSD_ERROR_NOERROR;
}

int dnssd_register_airplay(dnssd_t* dnssd, unsigned short port) {
    dnssd->airplayPort = port;
    rebuild_airplay_txt(dnssd);
    return DNSSD_ERROR_NOERROR;
}

void dnssd_unregister_raop(dnssd_t* dnssd) {
    dnssd->raopPort = 0;
}

void dnssd_unregister_airplay(dnssd_t* dnssd) {
    dnssd->airplayPort = 0;
}

const char* dnssd_get_raop_txt(dnssd_t* dnssd, int* length) {
    if (length != nullptr) {
        *length = static_cast<int>(dnssd->raopTxt.size());
    }
    return dnssd->raopTxt.empty() ? nullptr : dnssd->raopTxt.data();
}

const char* dnssd_get_airplay_txt(dnssd_t* dnssd, int* length) {
    if (length != nullptr) {
        *length = static_cast<int>(dnssd->airplayTxt.size());
    }
    return dnssd->airplayTxt.empty() ? nullptr : dnssd->airplayTxt.data();
}

const char* dnssd_get_name(dnssd_t* dnssd, int* length) {
    if (length != nullptr) {
        *length = static_cast<int>(dnssd->name.size());
    }
    return dnssd->name.c_str();
}

const char* dnssd_get_hw_addr(dnssd_t* dnssd, int* length) {
    if (length != nullptr) {
        *length = static_cast<int>(dnssd->hwAddrRaw.size());
    }
    return dnssd->hwAddrRaw.data();
}

void dnssd_set_airplay_features(dnssd_t* dnssd, int bit, int val) {
    if (bit < 0 || bit > 63) {
        return;
    }
    const auto mask = uint64_t{1} << static_cast<uint64_t>(bit);
    if (val) {
        dnssd->features |= mask;
    } else {
        dnssd->features &= ~mask;
    }
}

uint64_t dnssd_get_airplay_features(dnssd_t* dnssd) {
    return dnssd->features;
}

void dnssd_set_pk(dnssd_t* dnssd, char* pk_str) {
    dnssd->pk = pk_str != nullptr ? pk_str : "";
    rebuild_raop_txt(dnssd);
    rebuild_airplay_txt(dnssd);
}

void dnssd_destroy(dnssd_t* dnssd) {
    delete dnssd;
}

}
