# Setup Guide: Database

The database layer is optional. When configured, it persists player statistics, match history, Elo ratings, and player profiles to a durable store. Without a database, all data is held in memory and lost on restart.

## Choosing a Backend

| Backend | Best For | Notes |
|---------|----------|-------|
| **MySQL** | Production networks with multiple servers | Full ACID, connection pooling via HikariCP |
| **SQLite** | Single servers, development, small communities | Zero-config, file-based, no external process |
| **MongoDB** | Document-oriented workflows, flexible schemas | Async driver, horizontal scaling |
| **None** | Testing, temporary setups | Default. No persistence. |

## Configuration

Edit `plugins/PvPIndexBattles/config.yml` to add the `database` section.

### MySQL

```yaml
database:
  type: "mysql"
  mysql:
    host: "db.internal"
    port: 3306
    database: "pvpindex"
    username: "pvpindex"
    password: "s3cur3-p4ssw0rd"
    pool:
      maximum_pool_size: 10
      minimum_idle: 2
      connection_timeout_ms: 30000
      idle_timeout_ms: 600000
      max_lifetime_ms: 1800000
```

### SQLite

```yaml
database:
  type: "sqlite"
  sqlite:
    file: "pvpindex.db"
```

The file path is relative to `plugins/PvPIndexBattles/`. An absolute path is also accepted.

### MongoDB

```yaml
database:
  type: "mongodb"
  mongodb:
    uri: "mongodb://user:password@mongo.internal:27017"
    database: "pvpindex"
```

The `uri` follows standard [MongoDB connection string](https://www.mongodb.com/docs/manual/reference/connection-string/) format. Authentication, replica sets, and TLS are configured within the URI.

### Disabling Persistence

```yaml
database:
  type: "none"
```

This is the default when no `database` section is present.

## Configuration Reference

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `database.type` | String | `none` | Backend type: `mysql`, `sqlite`, `mongodb`, or `none` |
| `database.mysql.host` | String | `localhost` | MySQL server hostname |
| `database.mysql.port` | int | `3306` | MySQL server port |
| `database.mysql.database` | String | `pvpindex` | Database name |
| `database.mysql.username` | String | `root` | Database user |
| `database.mysql.password` | String | *(empty)* | Database password |
| `database.mysql.pool.maximum_pool_size` | int | `10` | HikariCP max connections |
| `database.mysql.pool.minimum_idle` | int | `2` | HikariCP min idle connections |
| `database.mysql.pool.connection_timeout_ms` | long | `30000` | Time to wait for a connection from the pool |
| `database.mysql.pool.idle_timeout_ms` | long | `600000` | Max idle time before connection is retired |
| `database.mysql.pool.max_lifetime_ms` | long | `1800000` | Max connection lifetime |
| `database.sqlite.file` | String | `pvpindex.db` | SQLite database file path |
| `database.mongodb.uri` | String | *(empty)* | MongoDB connection URI |
| `database.mongodb.database` | String | `pvpindex` | MongoDB database name |

## Schema Auto-Creation

On first startup with a configured backend, the plugin automatically creates all required tables (SQL) or collections (MongoDB). No manual schema setup is needed.

For SQL backends, the schema includes:

- `pvpindex_players` - player profiles and preferences
- `pvpindex_stats` - per-mode statistics (wins, losses, Elo)
- `pvpindex_matches` - match history with outcomes and metadata
- `pvpindex_elo_history` - Elo rating change log

Schema versioning is tracked internally. When the plugin updates, missing columns or tables are added automatically on startup. Existing data is never dropped.

## Connection Pooling

MySQL uses [HikariCP](https://github.com/brettwooldridge/HikariCP) for connection pooling. Default settings are suitable for most deployments. Adjust pool sizes based on your server count:

| Lobby Servers | Recommended `maximum_pool_size` |
|---------------|-------------------------------|
| 1-3 | 5 |
| 4-10 | 10 |
| 10+ | 15-20 |

SQLite does not use connection pooling (single-writer model with WAL mode enabled).

MongoDB uses its internal async connection pool configured via the connection URI (e.g. `?maxPoolSize=20`).

## Async Behaviour

All database operations run asynchronously. The game thread is never blocked by database I/O.

- Reads are dispatched to a dedicated thread pool and results are delivered via callbacks on the main thread when needed.
- Writes are batched and flushed periodically (every 200 ticks by default).
- If the database is temporarily unreachable, writes are queued in memory and retried.

```
[PvPIndexBattles] Database connected: MySQL (db.internal:3306/pvpindex, pool: 10)
[PvPIndexBattles] Schema is up to date (version 4)
```

## Persisted Data

| Category | Description |
|----------|-------------|
| **Player profiles** | UUID, username, first/last seen, preferences |
| **Statistics** | Per-mode wins, losses, draws, kill/death ratio |
| **Elo ratings** | Current rating per mode, peak rating, rating history |
| **Match history** | Full match records with participants, outcome, duration, mode |

Data is written after each battle ends and on player disconnect (for profile updates).

## Migration Notes

### Moving from None to a Backend

1. Add the `database` section to `config.yml`.
2. Restart the server.
3. The schema is created automatically. Historical data from the in-memory period is not retroactively persisted.

### Switching Between Backends

1. Export data from the current backend if needed (not handled by the plugin).
2. Change `database.type` and configure the new backend.
3. Restart. A fresh schema is created in the new backend.

### Upgrading the Plugin

Schema migrations run automatically on startup. Back up your database before major version upgrades as a precaution.

## Troubleshooting

| Problem | Fix |
|---------|-----|
| "Database connection failed" | Verify host, port, credentials, and network access. |
| "Access denied for user" | Check username/password and that the user has CREATE/INSERT/UPDATE/SELECT grants. |
| "Too many connections" | Reduce `maximum_pool_size` or increase MySQL's `max_connections`. |
| SQLite "database is locked" | Only one server should use a given SQLite file. |
| MongoDB "auth failed" | Verify credentials in the connection URI. |
| "Schema migration failed" | Check logs for the specific migration error. Restore from backup if needed. |
