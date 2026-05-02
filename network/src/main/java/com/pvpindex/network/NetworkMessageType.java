package com.pvpindex.network;

public enum NetworkMessageType {

    // Proxy lifecycle
    PROXY_REGISTER,
    PROXY_HEARTBEAT,
    PROXY_SHUTDOWN,

    // Player events
    PLAYER_JOIN,
    PLAYER_LEAVE,
    PLAYER_SWITCH_SERVER,

    // Challenge flow
    CHALLENGE_SEND,
    CHALLENGE_ACCEPT,
    CHALLENGE_DENY,
    CHALLENGE_CANCEL,
    CHALLENGE_EXPIRED,

    // Battle lifecycle
    BATTLE_START,
    BATTLE_END,
    BATTLE_CANCEL,

    // Transfer
    TRANSFER_REQUEST,
    TRANSFER_READY,
    TRANSFER_FAILED,

    // Administrative
    PLAYER_LOOKUP_REQUEST,
    PLAYER_LOOKUP_RESPONSE,
    GLOBAL_BROADCAST,
    SERVER_LIST_REQUEST,
    SERVER_LIST_RESPONSE
}
