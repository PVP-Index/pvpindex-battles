# Global Features

When lobby mode is enabled (`lobby.enabled: true`), Paper servers connect directly to Redis and gain access to a suite of global features. These features work across all connected lobby servers without requiring proxy-side logic.

## Activation

All global features activate automatically when:

```yaml
lobby:
  enabled: true
```

No per-feature toggles are needed. If lobby mode is enabled and Redis is connected, all features are active.

## Global Player List

Real-time visibility of every online player across all connected lobby servers.

### How It Works

- Each lobby server publishes player join/leave events to Redis Pub/Sub.
- All other lobby servers receive these events and update their local player cache.
- The player list is available for tab completion, GUIs, and commands within milliseconds of a player joining any server.

### Technical Details

| Aspect | Detail |
|--------|--------|
| Channel | `pvpindex:players` |
| Update mechanism | Redis Pub/Sub (real-time push) |
| Fallback | Periodic full-sync every 30 seconds via Redis hash |
| Data per player | UUID, username, node_id, region, current state |

### Usage

- `/battle challenge <player>` tab-completes all global players.
- GUIs display accurate online counts across the network.
- PlaceholderAPI `%pvpindex_online_global%` reflects the true network-wide count.

## Global Challenges

Players on any lobby server can challenge players on any other lobby server. Challenges route directly between lobbies via Redis.

### 2-Hop Flow

```
Challenger's Lobby → Redis → Target's Lobby
       ↑                          |
       └──────── Redis ←──────────┘
              (accept/decline)
```

1. **Hop 1**: Challenger issues `/battle challenge <player> [mode]`. The lobby publishes a `CHALLENGE_SEND` message to Redis targeting the recipient's node.
2. **Hop 2**: The target's lobby receives the message and presents a clickable accept/decline prompt. The response is published back to Redis targeting the challenger's node.
3. **Resolution**: On acceptance, both lobbies coordinate via Redis to determine the battle server. The proxy transfers both players to the designated arena server.

### Cross-Region Challenges

Challenges work across regions. The routing layer selects an appropriate battle server based on the configured strategy (see Global Routing below).

### Expiry

Pending challenges expire after 30 seconds. Both parties are notified on expiry.

## Global Presence

Every player has a presence state visible to all lobby servers in real-time.

### States

| State | Meaning |
|-------|---------|
| `ONLINE` | In a lobby, idle or browsing |
| `AWAY` | AFK (no input for configurable duration) |
| `IN_BATTLE` | Currently fighting in an arena |
| `IN_QUEUE` | Waiting in the matchmaking queue |

### How It Works

- Presence changes are published to Redis Pub/Sub immediately.
- Each lobby maintains a local presence cache for all global players.
- Presence is used by the challenge system (cannot challenge players who are `IN_BATTLE`) and displayed in GUIs and player lists.

### Configuration

Presence tracking has no additional configuration beyond enabling lobby mode. AFK detection uses the existing `player_state` settings.

## Global Invites

Three types of invites are supported across the network.

### Game Invites

Invite a player to spectate your current battle or join your next queue.

```
/battle invite <player>
```

The target receives a clickable message regardless of which lobby server they are on.

### Server Invites

Invite a player to join your current server.

```
/battle invite <player> --server
```

On acceptance, the proxy transfers the target to the inviter's server.

### Party Invites

Invite a player to join your party (see Global Parties below).

```
/party invite <player>
```

### Invite Behaviour

- Invites expire after 60 seconds.
- A player can have at most 5 pending inbound invites at once. Additional invites are rejected with a message to the sender.
- Invites are cancelled if either party disconnects.

## Global Parties

Players can form parties that persist across server transfers and work network-wide.

### Commands

| Command | Description |
|---------|-------------|
| `/party create` | Create a new party (you become leader) |
| `/party invite <player>` | Invite a player to your party |
| `/party join <player>` | Join a player's party (if invited) |
| `/party leave` | Leave your current party |
| `/party kick <player>` | Kick a member (leader only) |
| `/party disband` | Disband the party (leader only) |
| `/party list` | List current party members and their status |
| `/party chat <message>` | Send a message to party members only |
| `/pc <message>` | Shorthand for party chat |

### Behaviour

- Party state is stored in Redis and synchronised across all lobby servers.
- When the party leader queues for a battle, all party members are queued together.
- Party members on different servers see each other's presence updates.
- If the leader disconnects, leadership transfers to the longest-standing member.
- Parties disband automatically when the last member leaves or disconnects.
- Maximum party size: 8 players (configurable in `config.yml`).

### Party Chat

Party chat messages route through Redis Pub/Sub. Messages are delivered to all party members regardless of which server they are on. Party chat is not logged to the server console by default.

## Global Routing

When a cross-server battle is confirmed, the routing system selects the optimal battle server.

### Strategies

| Strategy | Behaviour |
|----------|-----------|
| `nearest` | Select the battle server in the same region as the challenger |
| `lowest_latency` | Select the server with the lowest average latency to both players |
| `least_loaded` | Select the server with the fewest active battles |
| `shared_server` | Route to a pre-configured shared battle server |

### Configuration

```yaml
lobby:
  enabled: true
  node_id: "lobby-us-1"
  region: "us-east"
  routing:
    strategy: "least_loaded"
```

### How Routing Works

1. A battle is confirmed between two players (possibly on different servers/regions).
2. The routing system queries Redis for available battle servers, their regions, and current load.
3. The selected strategy picks the optimal server.
4. Both players' proxies are notified to transfer them to the chosen server.

### Fallback

If the primary strategy cannot find a suitable server (e.g. all servers in a region are full), it falls back to `least_loaded` across all regions.

## Redis Channel Map

All global features communicate over dedicated Redis Pub/Sub channels:

| Channel | Purpose |
|---------|---------|
| `pvpindex:players` | Player join/leave/switch events |
| `pvpindex:presence` | Presence state changes |
| `pvpindex:challenges` | Challenge send/accept/decline/expire |
| `pvpindex:invites` | Game, server, and party invites |
| `pvpindex:parties` | Party state changes and chat |
| `pvpindex:routing` | Battle server selection and transfer coordination |
| `pvpindex:heartbeat` | Node heartbeats and health |

## Data Consistency

- All global state uses Redis as the single source of truth.
- Local caches are updated via Pub/Sub events and reconciled with periodic full-sync operations.
- If a lobby server loses Redis connectivity, it continues operating locally but cannot participate in global features until reconnected.
- Stale entries (from crashed servers) are cleaned up automatically based on heartbeat timeouts.
