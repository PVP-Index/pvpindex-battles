# Multi-Proxy Network Setup

This guide covers running PvPIndex across **multiple Velocity and/or BungeeCord proxy instances** so the entire network behaves as one connected system.

> **Note (1.0.2):** Global synchronisation (player lists, challenges, presence, invites, parties) is now handled by Paper lobby servers connecting directly to Redis. Proxies in a multi-proxy setup are responsible for auth, routing, and transfers only. Lobby servers share the same Redis instance as the multi-proxy network layer.

## Prerequisites

| Requirement | Details |
|-------------|---------|
| Redis | 6.x+ (used for cross-proxy messaging) |
| Proxy plugin | `PvPIndexBattles-velocity-1.0.2.jar` or `PvPIndexBattles-bungeecord-1.0.2.jar` on every proxy |
| Paper plugin | `PvPIndexBattles-1.0.2.jar` on every backend server |
| Network access | All proxies must be able to reach the same Redis instance |

## Architecture

```
              [Redis]
             / | | | \
            v  v v v  v
[Velocity US] [Velocity EU] [BungeeCord Asia]
  |  |  |       |  |  |       |  |  |
 s1 s2 s3      s4 s5 s6      s7 s8 s9
  (lobby+SMP)   (lobby+SMP)   (lobby+SMP)
```

Each proxy connects to a shared Redis instance for cross-proxy coordination. Lobby Paper servers also connect to this same Redis instance for global features (player sync, challenges, presence, invites, parties). Proxies handle auth, routing, and transfers only.

## Configuration

Edit `config.properties` on each proxy. The only values that **must differ** between proxies are `network.proxy_id` and `network.region`.

### US Proxy Example

```properties
paper_secret=your-shared-secret
debug=false

network.enabled=true
network.proxy_id=us-east-1
network.region=us-east

network.redis.host=redis.internal
network.redis.port=6379
network.redis.password=your-redis-password
network.redis.database=0
network.redis.pool_size=8

network.reconnect_interval=5
network.message_timeout=10
network.heartbeat_interval=15
network.proxy_timeout=45

network.transfer_strategy=shared_server
network.shared_battle_servers=arena-global
```

### EU Proxy Example

```properties
paper_secret=your-shared-secret
debug=false

network.enabled=true
network.proxy_id=eu-west-1
network.region=eu-west

network.redis.host=redis.internal
network.redis.port=6379
network.redis.password=your-redis-password
network.redis.database=0
network.redis.pool_size=8

network.reconnect_interval=5
network.message_timeout=10
network.heartbeat_interval=15
network.proxy_timeout=45

network.transfer_strategy=shared_server
network.shared_battle_servers=arena-global
```

## Configuration Reference

| Key | Default | Description |
|-----|---------|-------------|
| `network.enabled` | `false` | Enable/disable the multi-proxy layer |
| `network.proxy_id` | `proxy-1` | Unique identifier for this proxy instance |
| `network.region` | `default` | Region label (informational) |
| `network.redis.host` | `localhost` | Redis server hostname |
| `network.redis.port` | `6379` | Redis server port |
| `network.redis.password` | (empty) | Redis password (leave empty for no auth) |
| `network.redis.database` | `0` | Redis database index |
| `network.redis.pool_size` | `8` | Jedis connection pool size |
| `network.reconnect_interval` | `5` | Seconds between reconnection attempts |
| `network.message_timeout` | `10` | Max message age in seconds before discard |
| `network.heartbeat_interval` | `15` | Seconds between heartbeat broadcasts |
| `network.proxy_timeout` | `45` | Seconds without heartbeat before proxy marked offline |
| `network.transfer_strategy` | `shared_server` | How cross-proxy battles connect players |
| `network.shared_battle_servers` | (empty) | Comma-separated server names shared by all proxies |

## Transfer Strategies

### Shared Server (`shared_server`)

Both players are routed to a battle server that is registered on all proxies (e.g. `arena-global`). This requires a backend server accessible from every proxy.

### Transfer Packet (`transfer_packet`)

Uses Minecraft 1.20.5+ transfer packets to redirect one player to the other proxy entirely. Requires clients on 1.20.5+ and the target proxy to be directly reachable by the client.

### Both (`both`)

Tries `shared_server` first. Falls back to `transfer_packet` if no shared server is available.

## How it Works

1. **Proxy Registration**: On startup each proxy broadcasts a `PROXY_REGISTER` message via Redis. Other proxies add it to their registry.
2. **Heartbeats**: Every proxy sends periodic heartbeats. If a proxy misses heartbeats for `proxy_timeout` seconds, it is marked offline and its players are cleaned up.
3. **Player Tracking**: Lobby Paper servers handle player sync via `PlayerSyncService` and `PresenceService`, publishing join/leave events directly to Redis. Proxies track local player locations for routing and also consult the Redis player registry for cross-proxy lookups (e.g. when a backend server challenges a player connected to a different proxy).
4. **Cross-Proxy Challenges**: Lobby servers route challenges via `ChallengeSyncService` through Redis. The target lobby server presents the challenge to the target player. Proxies only execute the resulting transfer.
5. **Battle Events**: Battle start/end events are broadcast so all servers can report accurate global battle counts.
6. **Parties**: `PartySyncService` on lobby servers synchronises party state (create, invite, join, leave, kick, disband) across the network via Redis.
7. **Invites**: `InviteService` on lobby servers manages cross-server battle invitations via Redis.

## Migration from Single-Proxy

1. Add the `network.*` keys to your existing `config.properties`.
2. Set `network.enabled=true`.
3. Set a unique `network.proxy_id`.
4. Deploy Redis and configure the connection.
5. Restart the proxy.

When `network.enabled=false` (the default), the plugin behaves exactly as before. No existing functionality is changed.

## Troubleshooting

| Symptom | Likely cause |
|---------|-------------|
| "Failed to initialise network layer" | Redis is unreachable or password is wrong |
| Proxy shows as offline | Heartbeat interval too long or Redis latency |
| Cross-proxy challenges fail | Redis is down or target proxy is offline |
| "Config error: proxy_id must not be blank" | `network.proxy_id` is missing |

Enable `debug=true` in `config.properties` to see detailed message routing logs.
