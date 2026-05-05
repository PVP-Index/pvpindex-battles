package com.pvpindex.database;

import com.pvpindex.database.noop.NoopProvider;
import com.pvpindex.database.sql.MysqlProvider;
import com.pvpindex.database.sql.SqliteProvider;

import java.nio.file.Path;

public final class DatabaseFactory {

    private DatabaseFactory() {}

    public static DatabaseProvider create(DatabaseConfig config, Path dataFolder) {
        if (!config.enabled() || "none".equalsIgnoreCase(config.type())) {
            return new NoopProvider();
        }

        return switch (config.type().toLowerCase()) {
            case "mysql", "mariadb" -> new MysqlProvider(config);
            case "sqlite" -> new SqliteProvider(dataFolder.resolve(config.sqliteFile()));
            case "mongodb", "mongo" -> {
                try {
                    Class<?> clazz = Class.forName("com.pvpindex.database.mongo.MongoProvider");
                    yield (DatabaseProvider) clazz
                            .getConstructor(String.class, String.class)
                            .newInstance(config.mongoUri(), config.mongoDatabase());
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "MongoDB driver not found on classpath. Add the MongoDB driver to use MongoDB.", e);
                }
            }
            default -> throw new IllegalArgumentException("Unknown database type: " + config.type());
        };
    }
}
