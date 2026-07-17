package com.pgc.gpbucketbypass;

import java.io.File;
import java.sql.*;
import java.util.UUID;

/** Small SQLite store for persistent staff exemptions and blocked-action audit records. */
public final class DatabaseManager implements AutoCloseable {
    private Connection connection;
    public DatabaseManager(File file) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS exemptions (uuid TEXT PRIMARY KEY, added_by TEXT NOT NULL, added_at INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS audit (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, player_name TEXT NOT NULL, action TEXT NOT NULL, world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, created_at INTEGER NOT NULL)");
        }
    }
    public boolean isExempt(UUID uuid) { return exists("SELECT 1 FROM exemptions WHERE uuid = ?", uuid); }
    public void setExempt(UUID uuid, String by, boolean exempt) throws SQLException {
        if (exempt) try (PreparedStatement p = connection.prepareStatement("INSERT OR REPLACE INTO exemptions(uuid, added_by, added_at) VALUES (?, ?, ?)")) { p.setString(1, uuid.toString()); p.setString(2, by); p.setLong(3, System.currentTimeMillis()); p.executeUpdate(); }
        else try (PreparedStatement p = connection.prepareStatement("DELETE FROM exemptions WHERE uuid = ?")) { p.setString(1, uuid.toString()); p.executeUpdate(); }
    }
    public void audit(UUID uuid, String name, String action, String world, int x, int y, int z) {
        try (PreparedStatement p = connection.prepareStatement("INSERT INTO audit(uuid, player_name, action, world, x, y, z, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) { p.setString(1, uuid.toString()); p.setString(2, name); p.setString(3, action); p.setString(4, world); p.setInt(5, x); p.setInt(6, y); p.setInt(7, z); p.setLong(8, System.currentTimeMillis()); p.executeUpdate(); } catch (SQLException ignored) { }
    }
    private boolean exists(String sql, UUID uuid) { try (PreparedStatement p = connection.prepareStatement(sql)) { p.setString(1, uuid.toString()); return p.executeQuery().next(); } catch (SQLException e) { return false; } }
    @Override public void close() { if (connection != null) try { connection.close(); } catch (SQLException ignored) { } }
}
