package com.pvpindex.database.sql;

import com.pvpindex.database.DatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.SQLException;

public final class MysqlProvider extends SqlDatabaseProvider {

    private final DatabaseConfig config;
    private HikariDataSource hikari;

    public MysqlProvider(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public void connect() throws Exception {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:mysql://" + config.mysqlHost() + ":" + config.mysqlPort() + "/" + config.mysqlDatabase()
                + "?useSSL=" + config.mysqlSsl() + "&allowPublicKeyRetrieval=true&autoReconnect=true");
        hc.setUsername(config.mysqlUsername());
        hc.setPassword(config.mysqlPassword());
        hc.setMaximumPoolSize(config.mysqlPoolSize());
        hc.setMinimumIdle(1);
        hc.setPoolName("pvpindex-mysql");
        hc.setConnectionTestQuery("SELECT 1");

        hikari = new HikariDataSource(hc);
        this.dataSource = hikari;
        initSchema();
    }

    @Override
    public void disconnect() {
        if (hikari != null && !hikari.isClosed()) {
            hikari.close();
        }
    }

    @Override
    public boolean isConnected() {
        return hikari != null && !hikari.isClosed();
    }

    @Override
    public String type() { return "mysql"; }
}
