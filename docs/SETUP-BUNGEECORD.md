# BungeeCord Setup

PvPIndex supports BungeeCord as an alternative to Velocity. The BungeeCord plugin provides the same cross-server battle tracking, challenge routing, and network coordination.

## Requirements

| Requirement | Version |
|-------------|---------|
| Java | 21+ |
| BungeeCord | 1.21+ |
| Paper backends | 1.21.x or 26.1.x |

## Installation

1. Build the project:

```bash
mvn clean package
```

2. Copy `bootstrap-bungeecord/target/PvPIndexBattles-bungeecord-1.0.0.jar` into your BungeeCord `plugins/` folder.

3. Copy `bootstrap-paper/target/PvPIndexBattles-1.0.0.jar` into each Paper backend's `plugins/` folder.

4. Start BungeeCord once to generate the default config at `plugins/PvPIndex-Proxy/config.properties`.

5. Edit the config:

```properties
# Must match proxy.secret in each Paper backend's config.yml
paper_secret=your-shared-secret

# Optional: limit which servers are monitored
monitored_servers=

debug=false
```

6. On each Paper backend, edit `plugins/PvPIndexBattles/config.yml`:

```yaml
proxy:
  enabled: true
  secret: "your-shared-secret"
```

7. Restart BungeeCord and all backends.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/pvpindex` | `pvpindex.admin` | Show plugin info |
| `/pvpindex network` | `pvpindex.admin` | Show multi-proxy network status |
| `/pvpindex reload` | `pvpindex.admin` | Reload config (restart required on BungeeCord) |

## Multi-Proxy with BungeeCord

BungeeCord proxies can participate in the multi-proxy network alongside Velocity proxies. See [SETUP-MULTI-PROXY.md](SETUP-MULTI-PROXY.md) for the full guide.

Add the `network.*` keys to your `config.properties`:

```properties
network.enabled=true
network.proxy_id=bungee-asia-1
network.region=asia
network.redis.host=redis.internal
network.redis.port=6379
```

## Differences from Velocity

| Feature | Velocity | BungeeCord |
|---------|----------|------------|
| Plugin messaging | `ServerConnection.sendPluginMessage()` | `ServerInfo.sendData()` |
| Config reload | Hot reload via `/pvpindex reload` | Requires proxy restart |
| Annotation processing | `@Plugin` annotation | `bungee.yml` descriptor |
| Modern forwarding | Built-in | Requires additional setup |

## Troubleshooting

- **Plugin messages not arriving**: Ensure `paper_secret` matches on both sides. Check that at least one player is on the target server (BungeeCord requires a player conduit for plugin messaging).
- **Commands not registered**: Verify the JAR is loaded with `/bungee plugins`.
- **Cross-server challenges failing**: Enable `debug=true` and check the BungeeCord console for routing logs.
