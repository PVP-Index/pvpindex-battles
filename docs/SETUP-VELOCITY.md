# Setup Guide: Velocity

This guide covers setting up the PvPIndex Velocity proxy plugin and linking it to your Paper backend servers.

> **Note (1.0.2):** The Velocity proxy plugin is now a thin layer handling auth, routing, and transfers only. Global features (player sync, challenges, presence, invites, parties) are handled by Paper lobby servers connecting directly to Redis. See [SETUP-PAPER.md](SETUP-PAPER.md) for lobby mode setup.

## What the Velocity Plugin Does

- Authenticates plugin messages between the proxy and backends
- Routes players to the correct backend server
- Handles transfer packets for cross-server player movement
- Tracks which server each player is on
- Cancels battles when a player switches servers mid-fight
- Provides `/vpvpindex` commands for network-wide battle status
- Forwards plugin messages between the proxy and backends over the `pvpindex:proxy` channel

## Prerequisites

- A Velocity proxy running version **3.x**
- Java 21 installed
- The PvPIndex Paper plugin installed on each backend server (see [SETUP-PAPER.md](SETUP-PAPER.md))

## Step 1: Install

1. Download or build `PvPIndexBattles-velocity-1.1.0.jar`.
2. Place it in your Velocity proxy's `plugins/` folder.

## Step 2: First Run

Start the proxy. The plugin generates `plugins/pvpindex-proxy/config.properties`.

## Step 3: Generate a Shared Secret

The proxy and every Paper backend authenticate plugin messages using a shared secret. Generate a strong random string:

```bash
# Linux / macOS
openssl rand -hex 32

# Or use any password generator. Example output:
# a3f8c91b2d4e6f7081a2b3c4d5e6f708a1b2c3d4e5f60718
```

Keep this secret safe. Anyone with it can send fake plugin messages to your servers.

## Step 4: Configure the Velocity Proxy

Edit `plugins/pvpindex-proxy/config.properties`:

```properties
# Paste your generated secret here
paper_secret=a3f8c91b2d4e6f7081a2b3c4d5e6f708a1b2c3d4e5f60718

# Which backend servers to monitor
# Leave empty to monitor ALL servers registered in velocity.toml
# Or list specific ones (comma-separated, must match velocity.toml names)
monitored_servers=

# Enable for verbose logging during setup (disable in production)
debug=true
```

### `monitored_servers` explained

This controls which of your Velocity backends the PvPIndex plugin talks to.

| Value | Behaviour |
|-------|-----------|
| *(empty)* | Monitors every server in your `velocity.toml` |
| `pvp1,pvp2` | Only monitors `pvp1` and `pvp2` |
| `pvp1` | Only monitors `pvp1` |

Use this if you have non-PvP servers (lobby, creative, etc.) that do not run the Paper plugin.

## Step 5: Configure Each Paper Backend

On **every** Paper server that should connect to the proxy, edit `plugins/PvPIndexBattles/config.yml`:

```yaml
proxy:
  enabled: true
  secret: "a3f8c91b2d4e6f7081a2b3c4d5e6f708a1b2c3d4e5f60718"
  heartbeat_interval_ticks: 200

server:
  id: "pvp1"
```

Key points:

- `proxy.enabled` must be `true` (it defaults to `false`)
- `proxy.secret` must be **exactly the same** string as `paper_secret` in the Velocity config
- `server.id` must be **unique per backend** (e.g. `pvp1`, `pvp2`, `pvp3`)
- `heartbeat_interval_ticks` controls how often the backend pings the proxy (200 = every 10 seconds)

### Example: Three-Server Network

**Velocity** `plugins/pvpindex-proxy/config.properties`:
```properties
paper_secret=a3f8c91b2d4e6f7081a2b3c4d5e6f708a1b2c3d4e5f60718
monitored_servers=
debug=false
```

**Paper Server 1** `plugins/PvPIndexBattles/config.yml`:
```yaml
server:
  id: "pvp1"
proxy:
  enabled: true
  secret: "a3f8c91b2d4e6f7081a2b3c4d5e6f708a1b2c3d4e5f60718"
```

**Paper Server 2** `plugins/PvPIndexBattles/config.yml`:
```yaml
server:
  id: "pvp2"
proxy:
  enabled: true
  secret: "a3f8c91b2d4e6f7081a2b3c4d5e6f708a1b2c3d4e5f60718"
```

**Paper Server 3** `plugins/PvPIndexBattles/config.yml`:
```yaml
server:
  id: "pvp3"
proxy:
  enabled: true
  secret: "a3f8c91b2d4e6f7081a2b3c4d5e6f708a1b2c3d4e5f60718"
```

## Step 6: Configure Velocity Modern Forwarding

This is a standard Velocity requirement (not specific to PvPIndex) but it must be set up for plugin messaging to work.

### On the Velocity proxy

Edit `velocity.toml`:

```toml
[forwarding]
mode = "modern"
secret = "your-velocity-forwarding-secret"
```

### On each Paper backend

Edit `config/paper-global.yml`:

```yaml
proxies:
  velocity:
    enabled: true
    online-mode: true
    secret: "your-velocity-forwarding-secret"
```

> **Important:** The Velocity **forwarding secret** (in `velocity.toml`) and the PvPIndex **plugin message secret** (in `config.properties` / `config.yml`) are two completely separate values. You need both, and they should be different strings.

## Step 7: Restart Everything

Order matters:

1. Stop all Paper backends.
2. Stop the Velocity proxy.
3. Start the Velocity proxy.
4. Start each Paper backend.

## Step 8: Verify

### On the Velocity proxy console

Look for:
```
[pvpindex-proxy] PvPIndex Velocity plugin enabled
```

### On each Paper backend console

Look for:
```
[PvPIndexBattles] Velocity proxy messaging enabled (channel=pvpindex:proxy)
```

### In-game (connected via the proxy)

```
/vpvpindex status    - lists connected backends and active battle counts
/vpvpindex list      - lists all tracked players and their current servers
```

If `status` shows your backends with heartbeat timestamps, the link is working.

## Transfer Handler

When lobby servers use `TransferRequester` to move players between backends, the Velocity plugin receives the transfer request and executes the server switch. The flow is:

1. Lobby server publishes a transfer request via Redis.
2. The Velocity plugin picks up the request and calls `player.createConnectionRequest(server).fireAndForget()`.
3. The target backend receives the player and processes the pending challenge or party join.

No additional configuration is needed. The transfer handler is active whenever the Velocity plugin is loaded.

## How It Works

| Event | What Happens |
|-------|-------------|
| Player joins via proxy | Velocity plugin tracks `UUID > server name` |
| Battle starts on a backend | Backend sends `BATTLE_START` to the proxy |
| Player switches server mid-battle | Proxy sends `CANCEL_BATTLE` to the backend |
| Battle ends | Backend sends `BATTLE_END` to the proxy |
| Backend heartbeat | Backend sends `HEARTBEAT` every N ticks so the proxy knows it is alive |

All messages are JSON-encoded, sent over the `pvpindex:proxy` Minecraft plugin channel, and validated against the shared secret.

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Proxy says "message rejected" | Secrets do not match. Check `paper_secret` and `proxy.secret` are identical. |
| Backend not showing in `/vpvpindex status` | Check `proxy.enabled: true` on that backend. Check the heartbeat is running. |
| Players not tracked after server switch | Ensure Velocity modern forwarding is configured and working. |
| `/vpvpindex` command not found | Make sure the Velocity JAR is in the proxy's `plugins/` folder and the proxy was restarted. |
| Messages working but battles not cancelling on switch | Check `monitored_servers` includes the relevant servers (or leave it empty for all). |
| Cross-server challenges not arriving | Check the console for `[BackendMessenger]` warnings. Common causes: no players on the target server (plugin messaging needs a conduit), server name not registered, or `monitored_servers` filtering out the sender. |
| "That player is not online on the network" | The target player is not connected to any server on the network. In single-proxy setups, the player must be behind the same proxy. In multi-proxy setups with Redis enabled, the proxy also checks the Redis player registry across all proxies. If the player is still not found, confirm they are online and that `network.enabled=true` is set on all proxies. |
| Tab completion not showing cross-server players | Velocity broadcasts the player list every 10 seconds. Wait a moment after the first player joins. Check `proxy.enabled: true` on the backend. |
| `debug: true` | Enable on both proxy and backends for verbose logging during setup. Shows all message routing, challenge flow, and player list broadcasts. Disable in production. |

## Multi-Proxy Networking

If you run multiple Velocity (or BungeeCord) proxies, see [SETUP-MULTI-PROXY.md](SETUP-MULTI-PROXY.md) for how to connect them via Redis so the entire network behaves as one system.
