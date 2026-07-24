package com.pgc.gpbucketbypass;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
            // --- ADVANCED UPDATE schema additions ---
            s.executeUpdate("CREATE TABLE IF NOT EXISTS region_flags (region TEXT NOT NULL, flag TEXT NOT NULL, mode TEXT NOT NULL, PRIMARY KEY (region, flag))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bypass_codes (code TEXT PRIMARY KEY, created_by TEXT NOT NULL, created_at INTEGER NOT NULL, redeemed_by TEXT, redeemed_at INTEGER)");
            addColumnIfMissing("protected_regions", "priority", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing("player_rules", "expires_at", "INTEGER NOT NULL DEFAULT 0");
            // --- NEXT UPDATE schema additions (101-120) ---
            s.executeUpdate("CREATE TABLE IF NOT EXISTS player_notes (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, author TEXT NOT NULL, note TEXT NOT NULL, created_at INTEGER NOT NULL)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS player_notes_uuid_idx ON player_notes(uuid)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS warnings (uuid TEXT PRIMARY KEY, count INTEGER NOT NULL DEFAULT 0, last_reason TEXT, updated_at INTEGER NOT NULL)");
            addColumnIfMissing("player_rules", "reason", "TEXT");
            addColumnIfMissing("protected_regions", "tags", "TEXT NOT NULL DEFAULT ''");
        }
    }
    /** Feature 76/78: additive schema migration for databases created by earlier plugin versions. */
    private void addColumnIfMissing(String table, String column, String definition) {
        try (Statement s = connection.createStatement()) { s.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition); }
        catch (SQLException ignored) { /* column already exists */ }
    }
    public enum Rule { INHERIT, EXEMPT, BLOCKED }
    public Rule rule(UUID uuid) {
        try (PreparedStatement p = connection.prepareStatement("SELECT mode, expires_at FROM player_rules WHERE uuid = ?")) {
            p.setString(1, uuid.toString()); ResultSet rs = p.executeQuery();
            if (!rs.next()) return Rule.INHERIT;
            // Feature 78: a timed rule silently reverts to INHERIT once expired, without needing the sweep task to have run yet.
            long expiresAt = rs.getLong(2);
            if (expiresAt > 0 && System.currentTimeMillis() > expiresAt) return Rule.INHERIT;
            return Rule.valueOf(rs.getString(1));
        } catch (Exception ignored) { return Rule.INHERIT; }
    }
    /** Feature 78/79: seconds until a player's timed rule expires, or -1 if it never expires / there is none. */
    public long ruleExpiresInSeconds(UUID uuid) {
        try (PreparedStatement p = connection.prepareStatement("SELECT expires_at FROM player_rules WHERE uuid = ?")) {
            p.setString(1, uuid.toString()); ResultSet rs = p.executeQuery();
            if (!rs.next()) return -1; long expiresAt = rs.getLong(1);
            return expiresAt <= 0 ? -1 : Math.max(0, (expiresAt - System.currentTimeMillis()) / 1000L);
        } catch (SQLException ignored) { return -1; }
    }
    public void setRule(UUID uuid, Rule rule, String by) throws SQLException { setRule(uuid, rule, by, 0, null); }
    /** @param durationMinutes 0 = permanent (feature 78: timed exempt/block rules). */
    public void setRule(UUID uuid, Rule rule, String by, int durationMinutes) throws SQLException { setRule(uuid, rule, by, durationMinutes, null); }
    /** @param reason feature 101: optional free-text reason, shown in /gpbucket whois. */
    public void setRule(UUID uuid, Rule rule, String by, int durationMinutes, String reason) throws SQLException {
        if (rule == Rule.INHERIT) try (PreparedStatement p = connection.prepareStatement("DELETE FROM player_rules WHERE uuid = ?")) { p.setString(1, uuid.toString()); p.executeUpdate(); }
        else try (PreparedStatement p = connection.prepareStatement("INSERT OR REPLACE INTO player_rules(uuid, mode, changed_by, changed_at, expires_at, reason) VALUES (?, ?, ?, ?, ?, ?)")) {
            long expiresAt = durationMinutes > 0 ? System.currentTimeMillis() + durationMinutes * 60_000L : 0;
            p.setString(1, uuid.toString()); p.setString(2, rule.name()); p.setString(3, by); p.setLong(4, System.currentTimeMillis()); p.setLong(5, expiresAt); p.setString(6, reason); p.executeUpdate();
        }
    }
    /** Feature 94: background sweep that clears rows whose timed rule has expired. Returns rows cleared. */
    public int expireTimedRules() {
        try (PreparedStatement p = connection.prepareStatement("DELETE FROM player_rules WHERE expires_at > 0 AND expires_at < ?")) { p.setLong(1, System.currentTimeMillis()); return p.executeUpdate(); } catch (SQLException ignored) { return 0; }
    }
    /** Feature 84: returns the new total audit row count so callers can detect milestone crossings without an extra query. */
    public long audit(UUID uuid, String name, String action, String world, int x, int y, int z) {
        try (PreparedStatement p = connection.prepareStatement("INSERT INTO audit(uuid, player_name, action, world, x, y, z, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) { p.setString(1, uuid.toString()); p.setString(2, name); p.setString(3, action); p.setString(4, world); p.setInt(5, x); p.setInt(6, y); p.setInt(7, z); p.setLong(8, System.currentTimeMillis()); p.executeUpdate(); } catch (SQLException ignored) { }
        return auditCount();
    }
    public long blockedCount(UUID uuid) { try (PreparedStatement p = connection.prepareStatement("SELECT COUNT(*) FROM audit WHERE uuid = ?")) { p.setString(1, uuid.toString()); ResultSet r = p.executeQuery(); return r.next() ? r.getLong(1) : 0; } catch (SQLException ignored) { return 0; } }
    public List<String> recentAudit(UUID uuid, int limit) {
        List<String> entries = new ArrayList<>();
        try (PreparedStatement p = connection.prepareStatement("SELECT action, world, x, y, z, created_at FROM audit WHERE uuid = ? ORDER BY id DESC LIMIT ?")) { p.setString(1, uuid.toString()); p.setInt(2, Math.max(1, Math.min(limit, 20))); ResultSet r = p.executeQuery(); while (r.next()) entries.add(r.getString(1) + " at " + r.getString(2) + " " + r.getInt(3) + "," + r.getInt(4) + "," + r.getInt(5) + " [" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(r.getLong(6))) + "]"); }
        catch (SQLException ignored) { }
        return entries;
    }
    public void saveRegion(ProtectedRegion region, String by) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement("INSERT OR REPLACE INTO protected_regions(name, world, min_x, min_y, min_z, max_x, max_y, max_z, created_by, created_at, priority) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE((SELECT priority FROM protected_regions WHERE name = ?), 0))")) {
            p.setString(1, region.name()); p.setString(2, region.world()); p.setInt(3, region.minX()); p.setInt(4, region.minY()); p.setInt(5, region.minZ()); p.setInt(6, region.maxX()); p.setInt(7, region.maxY()); p.setInt(8, region.maxZ()); p.setString(9, by); p.setLong(10, System.currentTimeMillis()); p.setString(11, region.name()); p.executeUpdate();
        }
    }
    /** Feature 77: explicit priority write, used by /gpbucket region priority. */
    public void setRegionPriority(String name, int priority) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement("UPDATE protected_regions SET priority = ? WHERE name = ?")) { p.setInt(1, priority); p.setString(2, name); p.executeUpdate(); }
    }
    public boolean deleteRegion(String name) throws SQLException {
        try (PreparedStatement fp = connection.prepareStatement("DELETE FROM region_flags WHERE region = ?")) { fp.setString(1, name); fp.executeUpdate(); }
        try (PreparedStatement p = connection.prepareStatement("DELETE FROM protected_regions WHERE name = ?")) { p.setString(1, name); return p.executeUpdate() > 0; }
    }
    public List<ProtectedRegion> loadRegions() throws SQLException {
        Map<String, Map<String, String>> flagsByRegion = new HashMap<>();
        try (PreparedStatement p = connection.prepareStatement("SELECT region, flag, mode FROM region_flags"); ResultSet r = p.executeQuery()) {
            while (r.next()) flagsByRegion.computeIfAbsent(r.getString(1), k -> new HashMap<>()).put(r.getString(2), r.getString(3));
        }
        List<ProtectedRegion> result = new ArrayList<>();
        try (PreparedStatement p = connection.prepareStatement("SELECT name, world, min_x, min_y, min_z, max_x, max_y, max_z, priority, tags FROM protected_regions ORDER BY priority DESC, name"); ResultSet r = p.executeQuery()) {
            while (r.next()) { String name = r.getString(1); result.add(new ProtectedRegion(name, r.getString(2), r.getInt(3), r.getInt(4), r.getInt(5), r.getInt(6), r.getInt(7), r.getInt(8), r.getInt(9), Map.copyOf(flagsByRegion.getOrDefault(name, Map.of())), r.getString(10) == null ? "" : r.getString(10))); }
        }
        return result;
    }
    // --- Feature 104: comma-separated tags for grouping/bulk lookup ---
    public void setRegionTags(String region, String tags) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement("UPDATE protected_regions SET tags = ? WHERE name = ?")) { p.setString(1, tags); p.setString(2, region); p.executeUpdate(); }
    }
    // --- Feature 77: per-region flag overrides (e.g. "allow water in this region even though CLAIMS scope blocks it") ---
    public void setRegionFlag(String region, String flag, String mode) throws SQLException {
        if (mode.equalsIgnoreCase("DEFAULT")) { try (PreparedStatement p = connection.prepareStatement("DELETE FROM region_flags WHERE region = ? AND flag = ?")) { p.setString(1, region); p.setString(2, flag); p.executeUpdate(); } return; }
        try (PreparedStatement p = connection.prepareStatement("INSERT OR REPLACE INTO region_flags(region, flag, mode) VALUES (?, ?, ?)")) { p.setString(1, region); p.setString(2, flag.toUpperCase(Locale.ROOT)); p.setString(3, mode.toUpperCase(Locale.ROOT)); p.executeUpdate(); }
    }
    // --- Feature 91: single-use staff bypass codes ---
    public void createBypassCode(String code, String by) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement("INSERT INTO bypass_codes(code, created_by, created_at) VALUES (?, ?, ?)")) { p.setString(1, code); p.setString(2, by); p.setLong(3, System.currentTimeMillis()); p.executeUpdate(); }
    }
    /** Returns true if the code existed and was unredeemed, atomically marking it redeemed. */
    public boolean redeemBypassCode(String code, String by) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement("UPDATE bypass_codes SET redeemed_by = ?, redeemed_at = ? WHERE code = ? AND redeemed_by IS NULL")) {
            p.setString(1, by); p.setLong(2, System.currentTimeMillis()); p.setString(3, code); return p.executeUpdate() > 0;
        }
    }
    // --- Feature 95: severity-weighted risk score (lava griefing weighs more than water/snow) ---
    public long riskScore(UUID uuid) {
        try (PreparedStatement p = connection.prepareStatement("SELECT action FROM audit WHERE uuid = ?")) {
            p.setString(1, uuid.toString()); ResultSet r = p.executeQuery(); long score = 0;
            while (r.next()) { String a = r.getString(1); score += a.contains("LAVA") ? 3 : a.contains("WATER") ? 1 : 2; }
            return score;
        } catch (SQLException ignored) { return 0; }
    }
    // --- Feature 87: search the global audit log by player name, action, or world ---
    public List<String> searchAudit(String keyword, int limit) {
        List<String> entries = new ArrayList<>(); String like = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
        try (PreparedStatement p = connection.prepareStatement("SELECT player_name, action, world, x, y, z, created_at FROM audit WHERE lower(player_name) LIKE ? OR lower(action) LIKE ? OR lower(world) LIKE ? ORDER BY id DESC LIMIT ?")) {
            p.setString(1, like); p.setString(2, like); p.setString(3, like); p.setInt(4, Math.max(1, Math.min(limit, 25))); ResultSet r = p.executeQuery();
            while (r.next()) entries.add(r.getString(1) + " " + r.getString(2) + " at " + r.getString(3) + " " + r.getInt(4) + "," + r.getInt(5) + "," + r.getInt(6) + " [" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(r.getLong(7))) + "]");
        } catch (SQLException ignored) { }
        return entries;
    }
    /** Feature 101: the stored reason for a player's current rule, or null if none was given. */
    public String ruleReason(UUID uuid) {
        try (PreparedStatement p = connection.prepareStatement("SELECT reason FROM player_rules WHERE uuid = ?")) { p.setString(1, uuid.toString()); ResultSet rs = p.executeQuery(); return rs.next() ? rs.getString(1) : null; } catch (SQLException ignored) { return null; }
    }
    // --- Feature 102: staff notes attached to a player ---
    public void addNote(UUID uuid, String author, String note) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement("INSERT INTO player_notes(uuid, author, note, created_at) VALUES (?, ?, ?, ?)")) { p.setString(1, uuid.toString()); p.setString(2, author); p.setString(3, note); p.setLong(4, System.currentTimeMillis()); p.executeUpdate(); }
    }
    public List<String> notes(UUID uuid, int limit) {
        List<String> result = new ArrayList<>();
        try (PreparedStatement p = connection.prepareStatement("SELECT author, note, created_at FROM player_notes WHERE uuid = ? ORDER BY id DESC LIMIT ?")) {
            p.setString(1, uuid.toString()); p.setInt(2, limit); ResultSet r = p.executeQuery();
            while (r.next()) result.add(r.getString(1) + ": " + r.getString(2) + " [" + new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(r.getLong(3))) + "]");
        } catch (SQLException ignored) { }
        return result;
    }
    // --- Feature 103: escalating warning system ---
    /** Returns the player's new warning count. */
    public int warn(UUID uuid, String reason) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement("INSERT INTO warnings(uuid, count, last_reason, updated_at) VALUES (?, 1, ?, ?) ON CONFLICT(uuid) DO UPDATE SET count = count + 1, last_reason = excluded.last_reason, updated_at = excluded.updated_at")) {
            p.setString(1, uuid.toString()); p.setString(2, reason); p.setLong(3, System.currentTimeMillis()); p.executeUpdate();
        }
        return warningCount(uuid);
    }
    public int warningCount(UUID uuid) {
        try (PreparedStatement p = connection.prepareStatement("SELECT count FROM warnings WHERE uuid = ?")) { p.setString(1, uuid.toString()); ResultSet r = p.executeQuery(); return r.next() ? r.getInt(1) : 0; } catch (SQLException ignored) { return 0; }
    }
    public void resetWarnings(UUID uuid) throws SQLException { try (PreparedStatement p = connection.prepareStatement("DELETE FROM warnings WHERE uuid = ?")) { p.setString(1, uuid.toString()); p.executeUpdate(); } }
    // --- Feature 105: risk-sorted leaderboard ---
    public List<String> topRisk(int limit) {
        List<String> result = new ArrayList<>();
        // SQLite has no native regex/weighting function, so risk is computed per-row in Java rather than SQL.
        Map<String, Long> scores = new HashMap<>(); Map<String, String> names = new HashMap<>();
        try (PreparedStatement p = connection.prepareStatement("SELECT uuid, player_name, action FROM audit"); ResultSet r = p.executeQuery()) {
            while (r.next()) { String uuid = r.getString(1); names.put(uuid, r.getString(2)); String action = r.getString(3); long weight = action.contains("LAVA") ? 3 : action.contains("WATER") ? 1 : 2; scores.merge(uuid, weight, Long::sum); }
        } catch (SQLException ignored) { }
        scores.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(Math.max(1, limit))
                .forEach(entry -> result.add(names.get(entry.getKey()) + " — " + entry.getValue()));
        return result;
    }
    // --- Feature 114: how many WorldEdit regions a given creator currently owns ---
    public int countRegionsBy(String createdBy) {
        try (PreparedStatement p = connection.prepareStatement("SELECT COUNT(*) FROM protected_regions WHERE created_by = ?")) { p.setString(1, createdBy); ResultSet r = p.executeQuery(); return r.next() ? r.getInt(1) : 0; } catch (SQLException ignored) { return 0; }
    }
    // --- Feature 116: confirmed full audit wipe ---
    public int clearAudit() { try (Statement s = connection.createStatement()) { return s.executeUpdate("DELETE FROM audit"); } catch (SQLException ignored) { return 0; } }
    // --- Feature 111: rolling 24h count for the daily digest ---
    public long blockedInLastDay() { return blockedCountSince(System.currentTimeMillis() - 86_400_000L); }
    /** Feature 96: on-disk size of the SQLite file, for /gpbucket version diagnostics. */
    public long fileSizeBytes(File file) { return file.exists() ? file.length() : 0; }
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
        try (PreparedStatement fp = connection.prepareStatement("UPDATE region_flags SET region = ? WHERE region = ?")) { fp.setString(1, newName); fp.setString(2, oldName); fp.executeUpdate(); }
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
