package de.gergh0stface.ghostyclaim.database;

import de.gergh0stface.ghostyclaim.model.Claim;

import java.util.List;
import java.util.UUID;

/**
 * Database abstraction for storing and retrieving claims.
 */
public interface Database {

    /**
     * Initialize the database (create tables, load data, etc.).
     */
    void init();

    /**
     * Save or update a claim.
     */
    void saveClaim(Claim claim);

    /**
     * Delete a claim by its UUID.
     */
    void deleteClaim(UUID claimId);

    /**
     * Get a claim by its UUID.
     */
    Claim getClaim(UUID claimId);

    /**
     * Get all claims owned by a player.
     */
    List<Claim> getClaimsForPlayer(UUID playerUUID);

    /**
     * Get all claims in the database.
     */
    List<Claim> getAllClaims();

    /**
     * Close the database connection / save data.
     */
    void close();
}
