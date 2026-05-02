package com.pvpindex.battles.common.messaging;

/**
 * All message types that flow on the {@link PluginChannel#PROXY} channel.
 *
 * <p>Direction convention:</p>
 * <ul>
 *   <li><b>Paper → Velocity</b>: {@code BATTLE_START}, {@code BATTLE_END},
 *       {@code PLAYER_ENTER_BATTLE}, {@code PLAYER_LEAVE_BATTLE}, {@code HEARTBEAT}</li>
 *   <li><b>Velocity → Paper</b>: {@code PLAYER_SERVER_INFO}, {@code PLAYER_SWITCHED_SERVER},
 *       {@code CANCEL_BATTLE}, {@code SERVER_LIST}</li>
 * </ul>
 */
public enum MessageType {

    // ── Paper → Velocity ──────────────────────────────────────────────────

    /**
     * A new battle has started on this backend.
     * Data: {@code {battleUuid, serverId, participants:[{uuid,username}]}}
     */
    BATTLE_START,

    /**
     * A battle has ended (finished, cancelled, or disputed).
     * Data: {@code {battleUuid, status, winners:[uuid...]}}
     */
    BATTLE_END,

    /**
     * A player has joined an existing battle (e.g. late-join team battle).
     * Data: {@code {playerUuid, battleUuid}}
     */
    PLAYER_ENTER_BATTLE,

    /**
     * A player left a battle (disconnect, death, surrender).
     * Data: {@code {playerUuid, battleUuid, reason}}
     */
    PLAYER_LEAVE_BATTLE,

    /**
     * Periodic server heartbeat so Velocity knows the backend is alive.
     * Data: {@code {serverId, activeBattleCount, timestampEpochMs}}
     */
    HEARTBEAT,

    // ── Velocity → Paper ──────────────────────────────────────────────────

    /**
     * Response to a {@code PLAYER_ENTER_BATTLE}: tells this backend which proxy
     * server a given player is currently on.
     * Data: {@code {playerUuid, serverName}}
     */
    PLAYER_SERVER_INFO,

    /**
     * Alert: a player who is in an active battle has switched to a different
     * backend server. The source backend should cancel their battle.
     * Data: {@code {playerUuid, fromServer, toServer, battleUuid}}
     */
    PLAYER_SWITCHED_SERVER,

    /**
     * Velocity instructs a backend to cancel a specific battle (e.g. the
     * arena server went down, or the player can no longer be reached).
     * Data: {@code {battleUuid, reason}}
     */
    CANCEL_BATTLE,

    /**
     * Velocity sends the list of all registered backend server names to a backend.
     * Useful for cross-server targeting.
     * Data: {@code {servers:[name...]}}
     */
    SERVER_LIST,

    // ── Challenge system (bidirectional) ─────────────────────────────────

    /**
     * Paper → Velocity: a player wants to challenge another player.
     * Data: {@code {challengerUuid, challengerName, targetName, modeId}}
     * {@code modeId} may be null if the challenger wants the target to pick.
     */
    CHALLENGE_SEND,

    /**
     * Paper → Velocity: challenge target accepted the challenge.
     * Data: {@code {challengeId, accepterUuid}}
     */
    CHALLENGE_ACCEPT,

    /**
     * Paper → Velocity: challenge target declined.
     * Data: {@code {challengeId, declinerUuid}}
     */
    CHALLENGE_DECLINE,

    /**
     * Velocity → Paper: forwards a challenge to the target player's backend.
     * Data: {@code {challengeId, challengerName, challengerUuid, modeId, targetUuid}}
     */
    CHALLENGE_FORWARD,

    /**
     * Velocity → Paper: the target accepted — tells the challenger's (hosting) backend.
     * Data: {@code {challengeId, challengerUuid, targetUuid, modeId}}
     */
    CHALLENGE_CONFIRMED,

    /**
     * Velocity → Paper: challenge was declined or timed out.
     * Data: {@code {challengeId, reason}}
     */
    CHALLENGE_REJECTED,

    /**
     * Velocity → Paper (target's server only, cross-server): the challenge was accepted
     * and the target is being transferred to the host server. The target's server should
     * just remove the pending challenge — no battle attempt.
     * Data: {@code {challengeId}}
     */
    CHALLENGE_CLEANUP,

    // ── Network awareness ────────────────────────────────────────────────

    /**
     * Velocity → Paper: periodic broadcast of all online player names across
     * the proxy network. Used by Paper backends for cross-server tab completion.
     * Data: {@code {players:[{name, uuid, server}]}}
     */
    NETWORK_PLAYER_LIST,
}
