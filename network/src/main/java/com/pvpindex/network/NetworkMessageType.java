package com.pvpindex.network;

public enum NetworkMessageType {

    // Node lifecycle (generalized from PROXY_*)
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

    // Presence
    PRESENCE_UPDATE,

    // Invites
    INVITE_SEND,
    INVITE_ACCEPT,
    INVITE_DECLINE,

    // Parties
    PARTY_CREATE,
    PARTY_JOIN,
    PARTY_LEAVE,
    PARTY_DISBAND,
    PARTY_INVITE,
    PARTY_KICK,
    PARTY_UPDATE,
    PARTY_CHAT,

    // Routing
    ROUTE_REQUEST,
    ROUTE_RESPONSE,

    // Administrative
    PLAYER_LOOKUP_REQUEST,
    PLAYER_LOOKUP_RESPONSE,
    GLOBAL_BROADCAST,
    SERVER_LIST_REQUEST,
    SERVER_LIST_RESPONSE
}
