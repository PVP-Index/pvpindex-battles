# Setup Guide: Lobby Mode

Lobby mode allows Paper servers to connect directly to Redis for all global features without requiring a proxy plugin for cross-server communication. In this architecture, proxies (Velocity/BungeeCord) are simplified to authentication, routing, and player transfers only, whilst lobby servers handle player lists, challenges, presence, invites, parties, and routing natively.

## When to Use Lobby Mode

Use lobby mode when:

- You want global features (challenges, parties, player lists) without proxy plugin complexity
- Your Paper servers need direct peer-to-peer communication via Redis
- You are running multiple lobby servers that players can freely move between
- You want to reduce the proxy's responsibility to authentication and transfers only

Do **not** use lobby mode on dedicated battle/arena servers. Those servers receive players from lobby servers and run battles locally.

## Prerequisites

| Requirement | Details |
|-------------|---------|
| Redis | 6.x+ (shared across all lobby servers) |
| Paper plugin | `PvPIndexBattles-1.0.2.jar` on every lobby server |
| Network access | All lobby servers must reach the same Redis instance |

## Step 1: Enable Lobby Mode

Edit `plugins/PvPIndexBattles/config.yml` on each lobby server:

```yaml
lobby:
  enabled: true
  node_id: "lobby-us-1"
  region: "us-east"
```

Each lobby server **must** have a unique `node_id`. The `region` value is shared across all servers in the same geographical area.

## Step 2: Configure Redis

Add the Redis connection block to `config.yml`:

```yaml
lobby:
  enabled: true
  node_id: "lobby-us-1"
  region: "us-east"

  redis:
    host: "redis.internal"
    port: 6379
    password: "your-redis-password"
    database: 0
    pool_size: 8
```

### Redis Configuration Reference

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `lobby.redis.host` | String | `localhost` | Redis server hostname or IP |
| `lobby.redis.port` | int | `6379` | Redis server port |
| `lobby.redis.password` | String | *(empty)* | Redis AUTH password (leave empty for no auth) |
| `lobby.redis.database` | int | `0` | Redis database index (0-15) |
| `lobby.redis.pool_size` | int | `8` | Jedis connection pool size |

## Step 3: Set Node Identity

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `lobby.node_id` | String | `lobby-1` | Unique identifier for this lobby server |
| `lobby.region` | String | `default` | Region label for routing decisions |

Choose descriptive node IDs that encode purpose and region, e.g. `lobby-eu-1`, `lobby-us-east-2`, `lobby-asia-3`.

## Step 4: Verify Connectivity

Start the server. On successful connection you will see:

```
[PvPIndexBattles] Lobby mode enabled (node: lobby-us-1, region: us-east)
[PvPIndexBattles] Redis connected: redis.internal:6379/0
[PvPIndexBattles] Global features active: player_list, challenges, presence, invites, parties, routing
```

If Redis is unreachable, the plugin logs a warning and retries:

```
[PvPIndexBattles] [WARN] Redis connection failed: Connection refused (redis.internal:6379)
[PvPIndexBattles] [WARN] Retrying in 5 seconds...
```

## Full Example Configuration

```yaml
language: "en"

api:
  api_key: "your-api-key"

server:
  id: "lobby-us-1"

lobby:
  enabled: true
  node_id: "lobby-us-1"
  region: "us-east"

  redis:
    host: "redis.internal"
    port: 6379
    password: "s3cur3-p4ssw0rd"
    database: 0
    pool_size: 8

proxy:
  enabled: true
  secret: "shared-proxy-secret"
  heartbeat_interval_ticks: 200
```

> **Note:** `proxy.enabled` and `lobby.enabled` can both be `true`. The proxy handles authentication and transfers whilst the lobby server communicates directly with Redis for global features.

## Troubleshooting

| Problem | Fix |
|---------|-----|
| "Redis connection failed: Connection refused" | Verify Redis is running and accessible from the server. Check host, port, and firewall rules. |
| "Redis connection failed: NOAUTH" | Set the correct password in `lobby.redis.password`. |
| "Redis connection failed: invalid database" | Database index must be 0-15. |
| "Node ID conflict detected" | Two servers share the same `lobby.node_id`. Each must be unique. |
| Global player list empty | Confirm other lobby servers are connected to the same Redis instance and database. |
| Challenges not routing | Check that both the challenger's and target's lobby servers have `lobby.enabled: true` and share the same Redis connection. |
| High Redis latency warnings | Increase `lobby.redis.pool_size` or move Redis closer to your servers. |

Enable `debug: true` in `config.yml` to see detailed Redis message routing, presence updates, and challenge lifecycle logs.
