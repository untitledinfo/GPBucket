package com.pgc.gpbucketbypass;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/** Persistent local SQLite storage: explicit player rules, audit trail, and player totals. */
public final class DatabaseManager implements AutoCloseable {
    private Connection connection;
    public DatabaseManager(File file) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS player_rules (uuid TEXT PRIMARY KEY, mode TEXT NOT NULL, changed_by TEXT NOT NULL, changed_at INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS audit (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, player_name TEXT NOT NULL, action TEXT NOT NULL, world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, created_at INTEGER NOT NULL)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS audit_uuid_idx ON audit(uuid, created_at)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS protected_regions (name TEXT PRIMARY KEY, world TEXT NOT NULL, min_x INTEGER NOT NULL, min_y INTEGER NOT NULL, min_z INTEGER NOT NULL, max_x INTEGER NOT NULL, max_y INTEGER NOT NULL, max_z INTEGER NOT NULL, created_by TEXT NOT NULL, created_at INTEGER NOT NULL)");
        }
    }
    public enum Rule { INHERIT, EXEMPT, BLOCKED }
    public Rule rule(UUID uuid) {
        try (PreparedStatement p = connection.prepareStatement("SELECT mode FROM player_rules WHERE uuid = ?")) { p.setString(1, uuid.toString()); ResultSet rs = p.executeQuery(); return rs.next() ? Rule.valueOf(rs.getString(1)) : Rule.INHERIT; }
        catch (Exception ignored) { return Rule.INHERIT; }
    }
    public void setRule(UUID uuid, Rule rule, String by) throws SQLException {
        if (rule == Rule.INHERIT) try (PreparedStatement p = connection.prepareStatement("DELETE FROM player_rules WHERE uuid = ?")) { p.setString(1, uuid.toString()); p.executeUpdate(); }
        else try (PreparedStatement p = connection.prepareStatement("INSERT OR REPLACE INTO player_rules(uuid, mode, changed_by, changed_at) VALUES (?, ?, ?, ?)")) { p.setString(1, uuid.toString()); p.setString(2, rule.name()); p.setString(3, by); p.setLong(4, System.currentTimeMillis()); p.executeUpdate(); }
    }
    public void audit(UUID uuid, String name, String action, String world, int x, int y, int z) {
        try (PreparedStatement p = connection.prepareStatement("INSERT INTO audit(uuid, player_name, action, world, x, y, z, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) { p.setString(1, uuid.toString()); p.setString(2, name); p.setString(3, action); p.setString(4, world); p.setInt(5, x); p.setInt(6, y); p.setInt(7, z); p.setLong(8, System.currentTimeMillis()); p.executeUpdate(); } catch (SQLException ignored) { }
    }
    public long blockedCount(UUID uuid) { try (PreparedStatement p = connection.prepareStatement("SELECT COUNT(*) FROM audit WHERE uuid = ?")) { p.setString(1, uuid.toString()); ResultSet r = p.executeQuery(); return r.next() ? r.getLong(1) : 0; } catch (SQLException ignored) { return 0; } }
    public List<String> recentAudit(UUID uuid, int limit) {
        List<String> entries = new ArrayList<>();
        try (PreparedStatement p = connection.prepareStatement("SELECT action, world, x, y, z, created_at FROM audit WHERE uuid = ? ORDER BY id DESC LIMIT ?")) { p.setString(1, uuid.toString()); p.setInt(2, Math.max(1, Math.min(limit, 20))); ResultSet r = p.executeQuery(); while (r.next()) entries.add(r.getString(1) + " at " + r.getString(2) + " " + r.getInt(3) + "," + r.getInt(4) + "," + r.getInt(5) + " [" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(r.getLong(6))) + "]"); }
        catch (SQLException ignored) { }
        return entries;
    }
    public void saveRegion(ProtectedRegion region, String by) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement("INSERT OR REPLACE INTO protected_regions(name, world, min_x, min_y, min_z, max_x, max_y, max_z, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            p.setString(1, region.name()); p.setString(2, region.world()); p.setInt(3, region.minX()); p.setInt(4, region.minY()); p.setInt(5, region.minZ()); p.setInt(6, region.maxX()); p.setInt(7, region.maxY()); p.setInt(8, region.maxZ()); p.setString(9, by); p.setLong(10, System.currentTimeMillis()); p.executeUpdate();
        }
    }
    public boolean deleteRegion(String name) throws SQLException { try (PreparedStatement p = connection.prepareStatement("DELETE FROM protected_regions WHERE name = ?")) { p.setString(1, name); return p.executeUpdate() > 0; } }
    public List<ProtectedRegion> loadRegions() throws SQLException {
        List<ProtectedRegion> result = new ArrayList<>();
        try (PreparedStatement p = connection.prepareStatement("SELECT name, world, min_x, min_y, min_z, max_x, max_y, max_z FROM protected_regions ORDER BY name"); ResultSet r = p.executeQuery()) { while (r.next()) result.add(new ProtectedRegion(r.getString(1), r.getString(2), r.getInt(3), r.getInt(4), r.getInt(5), r.getInt(6), r.getInt(7), r.getInt(8))); }
        return result;
    }
    // --- HUGE UPDATE: feature 13, leaderboard of most-blocked players ---
    public List<String> topBlocked(int limit) {
        List<String> result = new ArrayList<>();
        try (PreparedStatement p = connection.prepareStatement("SELECT player_name, COUNT(*) c FROM audit GROUP BY uuid ORDER BY c DESC LIMIT ?")) {
            p.setInt(1, Math.max(1, Math.min(limit, 20)));
            ResultSet r = p.executeQuery();
            while (r.next()) result.add(r.getString(1) + " - " + r.getInt(2));
        } catch (SQLException ignored) { }
        return result;
    }
    // --- feature 14, global paginated audit history ---
    public List<String> allAudit(int page, int pageSize) {
        List<String> entries = new ArrayList<>();
        int offset = Math.max(0, (page - 1) * pageSize);
        try (PreparedStatement p = connection.prepareStatement("SELECT player_name, action, world, x, y, z, created_at FROM audit ORDER BY id DESC LIMIT ? OFFSET ?")) {
            p.setInt(1, pageSize); p.setInt(2, offset);
            ResultSet r = p.executeQuery();
            while (r.next()) entries.add(r.getString(1) + " " + r.getString(2) + " at " + r.getString(3) + " " + r.getInt(4) + "," + r.getInt(5) + "," + r.getInt(6) + " [" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(r.getLong(7))) + "]");
        } catch (SQLException ignored) { }
        return entries;
    }
    public int auditCount() {
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM audit")) { return r.next() ? r.getInt(1) : 0; } catch (SQLException ignored) { return 0; }
    }
    // --- feature 24, global blocked-action rate for /gpbucket status ---
    public long blockedCountSince(long sinceMillis) {
        try (PreparedStatement p = connection.prepareStatement("SELECT COUNT(*) FROM audit WHERE created_at >= ?")) { p.setLong(1, sinceMillis); ResultSet r = p.executeQuery(); return r.next() ? r.getLong(1) : 0; } catch (SQLException ignored) { return 0; }
    }
    // --- feature 12, audit retention purge ---
    public int purgeOlderThan(int days) {
        if (days <= 0) return 0;
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        try (PreparedStatement p = connection.prepareStatement("DELETE FROM audit WHERE created_at < ?")) { p.setLong(1, cutoff); return p.executeUpdate(); } catch (SQLException ignored) { return 0; }
    }
    // --- feature 15, region rename ---
    public boolean renameRegion(String oldName, String newName) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement("UPDATE protected_regions SET name = ? WHERE name = ?")) { p.setString(1, newName); p.setString(2, oldName); return p.executeUpdate() > 0; }
    }
    // --- feature 23, CSV export of the full audit log ---
    public void exportAudit(File target) throws SQLException, java.io.IOException {
        try (PreparedStatement p = connection.prepareStatement("SELECT player_name, action, world, x, y, z, created_at FROM audit ORDER BY id");
             ResultSet r = p.executeQuery();
             java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(target))) {
            writer.println("player,action,world,x,y,z,timestamp");
            while (r.next()) writer.println(r.getString(1) + "," + r.getString(2) + "," + r.getString(3) + "," + r.getInt(4) + "," + r.getInt(5) + "," + r.getInt(6) + "," + r.getLong(7));
        }
    }
    @Override public void close() { if (connection != null) try { connection.close(); } catch (SQLException ignored) { } }
}
