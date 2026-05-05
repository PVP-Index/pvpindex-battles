package com.pvpindex.database.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.nio.file.Path;

public final class SqliteProvider extends SqlDatabaseProvider {

    private final Path dbFile;
    private HikariDataSource hikari;

    public SqliteProvider(Path dbFile) {
        this.dbFile = dbFile;
    }

    @Override
    public void connect() throws Exception {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());
        hc.setMaximumPoolSize(1);
        hc.setPoolName("pvpindex-sqlite");

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
    public String type() { return "sqlite"; }
}
