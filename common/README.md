# PvPIndex Common - Shared Messaging Protocol

Platform-independent module containing the messaging protocol used between the Paper backend plugin and the proxy plugins (Velocity and BungeeCord). No Bukkit, Velocity, or BungeeCord dependencies - just Jackson for JSON encoding.

This module is shaded (embedded) into the Paper, Velocity, and BungeeCord JARs at build time. It is never deployed as a standalone plugin.

---

## Channel

All messages travel on a single bidirectional Minecraft plugin messaging channel:

```
pvpindex:proxy
```

Defined in `PluginChannel.PROXY`. Both the Paper and Velocity plugins register this channel.

---

## Wire Format

Every message is a single UTF-8 JSON object:

```json
{
  "type": "BATTLE_START",
  "secret": "your-shared-secret",
  "data": {
    "battleUuid": "a3f8e1b2-...",
    "serverId": "pvp1",
    "participants": [
      { "uuid": "069a79f4-...", "username": "Notch" }
    ]
  }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | String | yes | One of the `MessageType` enum values |
| `secret` | String | yes | Shared secret for authentication (empty string if not configured) |
| `data` | Object | yes | Message-type-specific payload (empty `{}` if no data) |

---

## Message Types

### Paper → Velocity

| Type | Data Fields | Description |
|------|-------------|-------------|
| `BATTLE_START` | `battleUuid: String`<br>`serverId: String`<br>`participants: [{uuid, username}]` | A new battle started on this backend |
| `BATTLE_END` | `battleUuid: String`<br>`status: String`<br>`winners: [uuid...]` | A battle ended (finished, cancelled, disputed) |
| `PLAYER_ENTER_BATTLE` | `playerUuid: String`<br>`battleUuid: String` | A player joined an existing battle |
| `PLAYER_LEAVE_BATTLE` | `playerUuid: String`<br>`battleUuid: String`<br>`reason: String` | A player left a battle (disconnect, death, surrender) |
| `HEARTBEAT` | `serverId: String`<br>`activeBattleCount: int`<br>`timestampEpochMs: long` | Periodic ping so the proxy knows the backend is alive |

### Velocity → Paper

| Type | Data Fields | Description |
|------|-------------|-------------|
| `PLAYER_SWITCHED_SERVER` | `playerUuid: String`<br>`fromServer: String`<br>`toServer: String`<br>`battleUuid: String` | A battling player switched servers - cancel their battle |
| `CANCEL_BATTLE` | `battleUuid: String`<br>`reason: String` | Proxy instructs backend to cancel a specific battle |
| `PLAYER_SERVER_INFO` | `playerUuid: String`<br>`serverName: String` | Tells backend which server a player is currently on |
| `SERVER_LIST` | `servers: [name...]` | All registered backend server names |

---

## Classes

### `PluginChannel`

```java
public final class PluginChannel {
    public static final String PROXY = "pvpindex:proxy";
}
```

Channel identifier constant. Both plugins use this to register their channel.

### `MessageType`

```java
public enum MessageType {
    // Paper → Velocity
    BATTLE_START,
    BATTLE_END,
    PLAYER_ENTER_BATTLE,
    PLAYER_LEAVE_BATTLE,
    HEARTBEAT,

    // Velocity → Paper
    PLAYER_SERVER_INFO,
    PLAYER_SWITCHED_SERVER,
    CANCEL_BATTLE,
    SERVER_LIST,
}
```

### `BattleMessage`

Immutable record that represents a single message on the wire.

```java
public record BattleMessage(
    MessageType type,
    String secret,
    Map<String, Object> data
) { ... }
```

#### Encoding (sending side)

```java
ObjectMapper mapper = new ObjectMapper();

// One-liner
byte[] bytes = BattleMessage.encode(mapper, MessageType.BATTLE_START, "mysecret",
    Map.of("battleUuid", uuid.toString(), "serverId", "pvp1"));

// Or construct first, then encode
BattleMessage msg = new BattleMessage(MessageType.HEARTBEAT, "mysecret",
    Map.of("serverId", "pvp1", "activeBattleCount", 3));
byte[] bytes = msg.encode(mapper);
```

#### Decoding (receiving side)

```java
BattleMessage msg = BattleMessage.decode(mapper, rawBytes);

if (!msg.isValid("mysecret")) {
    // reject - invalid secret
    return;
}

switch (msg.type()) {
    case BATTLE_START -> {
        String battleUuid = (String) msg.data().get("battleUuid");
        // ...
    }
}
```

#### Authentication

```java
boolean valid = msg.isValid("expected-secret");
```

- If `expectedSecret` is null or blank, validation is **disabled** (all messages accepted).
- Otherwise, the message's `secret` field must exactly match `expectedSecret`.
- This prevents rogue backends from injecting false battle events.

---

## Usage in Paper

The Paper plugin uses:
- `PaperMessenger` - encodes and sends messages to the proxy via any online player as a conduit.
- `ProxyMessageListener` - decodes incoming messages from the proxy and acts on them (cancel battles, log info).

## Usage in Velocity

The Velocity plugin uses:
- `BackendMessenger` - encodes and sends messages to specific backend servers via connected players.
- `ProxyMessageHandler` - decodes incoming messages from backends and updates registries.

---

## Dependencies

| Library | Version | Scope |
|---------|---------|-------|
| Jackson Databind | 2.18.2 | compile (shaded by consumers) |
| Jackson JSR310 | 2.18.2 | compile |
| JUnit 5 | 5.11.4 | test |
