# Scaling Guide

This guide covers scaling PvPIndex Battles from a simple two-region deployment to an N-region global network.

## Architecture Overview

```
                         ┌───────────────┐
                         │  Redis (shared)│
                         └───────┬───────┘
               ┌─────────────────┼─────────────────┐
               ▼                 ▼                 ▼
        ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
        │  Region: US  │   │  Region: EU  │   │ Region: Asia │
        └──────┬──────┘   └──────┬──────┘   └──────┬──────┘
               │                 │                 │
        ┌──────┼──────┐   ┌──────┼──────┐   ┌──────┼──────┐
        ▼      ▼      ▼   ▼      ▼      ▼   ▼      ▼      ▼
      Proxy  Lobby  Arena Proxy  Lobby  Arena Proxy  Lobby  Arena
             x2     x3          x2     x3          x2     x3
```

Each region contains:
- **1 Proxy** (Velocity or BungeeCord) handling authentication and transfers
- **N Lobby servers** running PvPIndex with `lobby.enabled: true`
- **N Arena/Battle servers** running PvPIndex in standard mode

All regions share a single Redis instance (or Redis Cluster) for global state synchronisation.

## Adding a New Region

### Step 1: Deploy Infrastructure

1. Provision a proxy server, at least one lobby server, and at least one arena server in the new region.
2. Ensure all servers can reach the shared Redis instance.

### Step 2: Configure the Proxy

Install the proxy plugin and configure it for authentication and routing only:

```properties
paper_secret=your-shared-secret

network.enabled=true
network.proxy_id=asia-1
network.region=asia

network.redis.host=redis.internal
network.redis.port=6379
network.redis.password=your-redis-password
network.redis.database=0
network.redis.pool_size=8

network.transfer_strategy=shared_server
network.shared_battle_servers=arena-asia-1,arena-asia-2
```

### Step 3: Configure Lobby Servers

Install the Paper plugin and enable lobby mode:

```yaml
lobby:
  enabled: true
  node_id: "lobby-asia-1"
  region: "asia"

  redis:
    host: "redis.internal"
    port: 6379
    password: "your-redis-password"
    database: 0
    pool_size: 8

  routing:
    strategy: "nearest"
```

### Step 4: Configure Arena Servers

Arena servers run in standard mode (no lobby mode). They receive players via proxy transfers:

```yaml
server:
  id: "arena-asia-1"

proxy:
  enabled: true
  secret: "your-shared-secret"
```

### Step 5: Verify

1. Start all servers in the new region.
2. Confirm lobby servers show `Global features active` in console output.
3. Test a cross-region challenge from an existing region to the new region.
4. Verify the player list includes players from the new region.

## Redis Requirements

### Single Instance

For most deployments (up to ~5000 concurrent players across all regions), a single Redis instance is sufficient.

| Metric | Recommendation |
|--------|---------------|
| Memory | 256 MB minimum, 1 GB recommended |
| CPU | 2 cores |
| Network | Low-latency connection from all regions (< 100ms RTT) |
| Persistence | `RDB` snapshots enabled, `AOF` optional |
| Version | 6.x or newer |

### Redis Cluster

For large deployments (5000+ concurrent players or strict latency requirements):

- Deploy a Redis Cluster with at least 3 master nodes.
- The plugin supports Redis Cluster automatically when the host resolves to a cluster.
- Configure the cluster address in `lobby.redis.host` and set `lobby.redis.cluster: true`.

```yaml
lobby:
  redis:
    host: "redis-cluster.internal"
    port: 6379
    password: "your-redis-password"
    cluster: true
    pool_size: 8
```

### Redis Sentinel

For high availability without full clustering:

```yaml
lobby:
  redis:
    sentinel:
      master: "pvpindex-master"
      nodes: "sentinel-1:26379,sentinel-2:26379,sentinel-3:26379"
    password: "your-redis-password"
    pool_size: 8
```

## Shared vs Dedicated Arena Servers

### Dedicated Arena Servers (per-region)

Each region has its own arena servers. Players within the same region always battle on local servers.

**Pros:** Lowest latency for same-region battles, isolated failure domains.
**Cons:** Cross-region battles require one player to connect to a remote arena.

```properties
network.shared_battle_servers=arena-us-1,arena-us-2
```

### Shared Arena Servers (cross-region)

A set of arena servers is accessible from all proxies. Used for cross-region battles.

**Pros:** Fair latency for both players in cross-region matches.
**Cons:** Requires geographically central hosting, slightly higher baseline latency.

```properties
network.shared_battle_servers=arena-global-1,arena-global-2
```

### Hybrid Approach

Use dedicated servers for same-region battles and shared servers for cross-region battles. The routing strategy handles this automatically:

```yaml
lobby:
  routing:
    strategy: "nearest"
    fallback: "shared_server"
```

## Routing Strategies

| Strategy | Behaviour | Best For |
|----------|-----------|----------|
| `nearest` | Picks the arena server in the same region as the challenger | Low latency same-region battles |
| `lowest_latency` | Picks the server with lowest combined latency to both players | Fair cross-region matches |
| `least_loaded` | Picks the server with fewest active battles | Even load distribution |
| `shared_server` | Always routes to the configured shared servers | Simple cross-region setups |

### Configuring Strategy per Region

Each lobby server can have its own routing strategy:

```yaml
lobby:
  routing:
    strategy: "nearest"
```

For cross-region battles where players are in different regions, the system negotiates: if both players' regions use `nearest`, the challenger's region wins. If either uses `lowest_latency`, that strategy takes precedence.

## Capacity Planning

### Players per Lobby Server

| Server Specs | Recommended Max Players |
|-------------|------------------------|
| 2 CPU, 4 GB RAM | 200 |
| 4 CPU, 8 GB RAM | 500 |
| 8 CPU, 16 GB RAM | 1000 |

### Arena Servers

Each active battle consumes one arena world. Plan arena server capacity based on:

- **Concurrent battles** = online players / 4 (rough estimate, assumes 50% queue at peak)
- **Memory per battle** = ~50 MB (world + player state + recording)
- **Arena pool warm size** = configured in `arena_pool.warm_size_per_template`

| Server Specs | Recommended Max Concurrent Battles |
|-------------|-----------------------------------|
| 4 CPU, 8 GB RAM | 30 |
| 8 CPU, 16 GB RAM | 75 |
| 16 CPU, 32 GB RAM | 150 |

### Redis Sizing

| Concurrent Players | Approximate Redis Memory |
|-------------------|------------------------|
| 500 | 64 MB |
| 2000 | 128 MB |
| 5000 | 256 MB |
| 10000+ | 512 MB+ (consider Redis Cluster) |

## Example: 3-Region Deployment

```
Regions: us-east, eu-west, asia

Per region:
  - 1 Velocity proxy
  - 2 lobby servers (lobby-{region}-1, lobby-{region}-2)
  - 3 arena servers (arena-{region}-1, arena-{region}-2, arena-{region}-3)
  - 1 shared arena server (arena-global-1, accessible from all proxies)

Redis: Single instance in us-east (or centrally hosted)

Total: 3 proxies, 6 lobbies, 9 dedicated arenas, 1 shared arena
Capacity: ~3000 concurrent players, ~225 concurrent battles
```

## Monitoring

Key metrics to track as you scale:

| Metric | Where to Find | Action Threshold |
|--------|--------------|-----------------|
| Redis memory usage | `redis-cli INFO memory` | > 80% of available RAM |
| Redis connected clients | `redis-cli INFO clients` | > pool_size * server_count |
| Pub/Sub message rate | `redis-cli INFO stats` | Sustained > 10k msg/sec |
| Lobby server TPS | Spark or `/tps` | Below 19.5 |
| Arena server concurrent battles | Plugin metrics | > 80% of recommended max |
| Challenge timeout rate | Plugin logs (debug mode) | > 5% of challenges timing out |

## Related Documentation

- [SETUP-LOBBY.md](SETUP-LOBBY.md) - Lobby server configuration
- [GLOBAL-FEATURES.md](GLOBAL-FEATURES.md) - Global feature reference
- [SETUP-MULTI-PROXY.md](SETUP-MULTI-PROXY.md) - Multi-proxy network setup
- [ARCHITECTURE.md](ARCHITECTURE.md) - Module and dependency structure
- [CONFIGURATION.md](CONFIGURATION.md) - Full configuration reference
