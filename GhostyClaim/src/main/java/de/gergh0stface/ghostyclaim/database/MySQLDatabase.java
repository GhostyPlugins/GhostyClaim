package de.gergh0stface.ghostyclaim.database;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import de.gergh0stface.ghostyclaim.model.Claim;
import de.gergh0stface.ghostyclaim.model.ClaimFlag;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * MySQL database implementation.
 */
public class MySQLDatabase implements Database {

    private final GhostyClaim plugin;
    private Connection connection;
    private final String host, database, username, password;
    private final int port;

    public MySQLDatabase(GhostyClaim plugin) {
        this.plugin = plugin;
        this.host = plugin.getConfig().getString("database.mysql.host", "localhost");
        this.port = plugin.getConfig().getInt("database.mysql.port", 3306);
        this.database = plugin.getConfig().getString("database.mysql.database", "ghostyclaim");
        this.username = plugin.getConfig().getString("database.mysql.username", "root");
        this.password = plugin.getConfig().getString("database.mysql.password", "");
    }

    @Override
    public void init() {
        try {
            openConnection();
            createTables();
            plugin.getLogger().info("MySQL database initialized.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize MySQL database!", e);
        }
    }

    private void openConnection() throws SQLException {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&autoReconnect=true&characterEncoding=utf8";
        connection = DriverManager.getConnection(url, username, password);
    }

    private synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            openConnection();
        }
        return connection;
    }

    private void createTables() throws SQLException {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS gc_claims (" +
                "  id VARCHAR(36) NOT NULL PRIMARY KEY," +
                "  owner_uuid VARCHAR(36) NOT NULL," +
                "  owner_name VARCHAR(64) NOT NULL," +
                "  world VARCHAR(64) NOT NULL," +
                "  min_x INT NOT NULL," +
                "  min_z INT NOT NULL," +
                "  max_x INT NOT NULL," +
                "  max_z INT NOT NULL," +
                "  name VARCHAR(64) NOT NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS gc_flags (" +
                "  claim_id VARCHAR(36) NOT NULL," +
                "  flag_key VARCHAR(32) NOT NULL," +
                "  flag_value TINYINT(1) NOT NULL," +
                "  PRIMARY KEY (claim_id, flag_key)," +
                "  FOREIGN KEY (claim_id) REFERENCES gc_claims(id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS gc_trusted (" +
                "  claim_id VARCHAR(36) NOT NULL," +
                "  player_uuid VARCHAR(36) NOT NULL," +
                "  player_name VARCHAR(64) NOT NULL," +
                "  PRIMARY KEY (claim_id, player_uuid)," +
                "  FOREIGN KEY (claim_id) REFERENCES gc_claims(id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
            );
        }
    }

    @Override
    public synchronized void saveClaim(Claim claim) {
        try {
            // Upsert claim
            String sql = "INSERT INTO gc_claims (id, owner_uuid, owner_name, world, min_x, min_z, max_x, max_z, name) " +
                         "VALUES (?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE " +
                         "owner_uuid=VALUES(owner_uuid), owner_name=VALUES(owner_name), " +
                         "world=VALUES(world), min_x=VALUES(min_x), min_z=VALUES(min_z), " +
                         "max_x=VALUES(max_x), max_z=VALUES(max_z), name=VALUES(name)";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, claim.getId().toString());
                ps.setString(2, claim.getOwnerUUID().toString());
                ps.setString(3, claim.getOwnerName());
                ps.setString(4, claim.getWorldName());
                ps.setInt(5, claim.getMinX());
                ps.setInt(6, claim.getMinZ());
                ps.setInt(7, claim.getMaxX());
                ps.setInt(8, claim.getMaxZ());
                ps.setString(9, claim.getName());
                ps.executeUpdate();
            }

            // Save flags
            String deleteFlags = "DELETE FROM gc_flags WHERE claim_id=?";
            try (PreparedStatement ps = getConnection().prepareStatement(deleteFlags)) {
                ps.setString(1, claim.getId().toString());
                ps.executeUpdate();
            }
            String insertFlag = "INSERT INTO gc_flags (claim_id, flag_key, flag_value) VALUES (?,?,?)";
            try (PreparedStatement ps = getConnection().prepareStatement(insertFlag)) {
                for (Map.Entry<ClaimFlag, Boolean> entry : claim.getFlags().entrySet()) {
                    ps.setString(1, claim.getId().toString());
                    ps.setString(2, entry.getKey().getKey());
                    ps.setBoolean(3, entry.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // Save trusted players
            String deleteTrusted = "DELETE FROM gc_trusted WHERE claim_id=?";
            try (PreparedStatement ps = getConnection().prepareStatement(deleteTrusted)) {
                ps.setString(1, claim.getId().toString());
                ps.executeUpdate();
            }
            String insertTrusted = "INSERT INTO gc_trusted (claim_id, player_uuid, player_name) VALUES (?,?,?)";
            try (PreparedStatement ps = getConnection().prepareStatement(insertTrusted)) {
                for (Map.Entry<UUID, String> entry : claim.getTrustedPlayers().entrySet()) {
                    ps.setString(1, claim.getId().toString());
                    ps.setString(2, entry.getKey().toString());
                    ps.setString(3, entry.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save claim " + claim.getId(), e);
        }
    }

    @Override
    public synchronized void deleteClaim(UUID claimId) {
        try {
            String sql = "DELETE FROM gc_claims WHERE id=?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, claimId.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete claim " + claimId, e);
        }
    }

    @Override
    public Claim getClaim(UUID claimId) {
        try {
            String sql = "SELECT * FROM gc_claims WHERE id=?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, claimId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return buildClaim(rs);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get claim " + claimId, e);
        }
        return null;
    }

    @Override
    public List<Claim> getClaimsForPlayer(UUID playerUUID) {
        List<Claim> result = new ArrayList<>();
        try {
            String sql = "SELECT * FROM gc_claims WHERE owner_uuid=?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, playerUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(buildClaim(rs));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get claims for " + playerUUID, e);
        }
        return result;
    }

    @Override
    public List<Claim> getAllClaims() {
        List<Claim> result = new ArrayList<>();
        try {
            String sql = "SELECT * FROM gc_claims";
            try (PreparedStatement ps = getConnection().prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(buildClaim(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get all claims", e);
        }
        return result;
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error closing MySQL connection", e);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────

    private Claim buildClaim(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID ownerUUID = UUID.fromString(rs.getString("owner_uuid"));
        String ownerName = rs.getString("owner_name");
        String world = rs.getString("world");
        int minX = rs.getInt("min_x");
        int minZ = rs.getInt("min_z");
        int maxX = rs.getInt("max_x");
        int maxZ = rs.getInt("max_z");
        String name = rs.getString("name");

        Claim claim = new Claim(id, ownerUUID, ownerName, world, minX, minZ, maxX, maxZ, name);

        // Load flags
        try {
            String flagSql = "SELECT flag_key, flag_value FROM gc_flags WHERE claim_id=?";
            try (PreparedStatement ps = getConnection().prepareStatement(flagSql)) {
                ps.setString(1, id.toString());
                try (ResultSet frs = ps.executeQuery()) {
                    while (frs.next()) {
                        ClaimFlag flag = ClaimFlag.fromKey(frs.getString("flag_key"));
                        if (flag != null) {
                            claim.setFlag(flag, frs.getBoolean("flag_value"));
                        }
                    }
                }
            }
        } catch (SQLException ignored) {}

        // Load trusted
        try {
            String trustSql = "SELECT player_uuid, player_name FROM gc_trusted WHERE claim_id=?";
            try (PreparedStatement ps = getConnection().prepareStatement(trustSql)) {
                ps.setString(1, id.toString());
                try (ResultSet trs = ps.executeQuery()) {
                    while (trs.next()) {
                        claim.addTrusted(
                            UUID.fromString(trs.getString("player_uuid")),
                            trs.getString("player_name")
                        );
                    }
                }
            }
        } catch (SQLException ignored) {}

        return claim;
    }
}
