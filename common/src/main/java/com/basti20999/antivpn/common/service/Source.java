package com.basti20999.antivpn.common.service;

/** Where a screening decision came from. */
public enum Source {
    CACHE,
    API,
    FAIL_MODE,
    IP_BLACKLIST,
    IP_WHITELIST,
    NAME_WHITELIST,
    LOCAL_IP
}
