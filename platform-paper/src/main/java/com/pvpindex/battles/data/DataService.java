package com.pvpindex.battles.data;

import com.pvpindex.battles.config.DatabaseSettings;
import com.pvpindex.database.DatabaseConfig;
import com.pvpindex.database.DatabaseFactory;
import com.pvpindex.database.DatabaseProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * Lifecycle manager for the optional database layer.
 * Only initialised when {@code database.enabled = true}.
 */
public final class DataService {

    private final JavaPlugin plugin;
    private final DatabaseSettings settings;
    private final Logger logger;
    private DatabaseProvider provider;

    public DataService(JavaPlugin plugin, DatabaseSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.logger = plugin.getLogger();
    }

    public void start() {
        DatabaseConfig dbConfig = new DatabaseConfig(
                settings.enabled(), settings.type(),
                settings.mysqlHost(), settings.mysqlPort(), settings.mysqlDatabase(),
                settings.mysqlUsername(), settings.mysqlPassword(), settings.mysqlPoolSize(), settings.mysqlSsl(),
                settings.sqliteFile(), settings.mongoUri(), settings.mongoDatabase());

        try {
            provider = DatabaseFactory.create(dbConfig, plugin.getDataFolder().toPath());
            provider.connect();
            logger.info("[PvPIndex Database] Connected to " + provider.type() + " database.");
        } catch (Exception e) {
            logger.severe("[PvPIndex Database] Failed to connect: " + e.getMessage());
            logger.severe("[PvPIndex Database] Persistent storage DISABLED. Stats will not be saved.");
            provider = null;
        }
    }

    public void shutdown() {
        if (provider != null) {
            provider.disconnect();
            logger.info("[PvPIndex Database] Disconnected.");
        }
    }

    public boolean isActive() {
        return provider != null && provider.isConnected();
    }

    public DatabaseProvider provider() { return provider; }
}
