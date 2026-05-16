package com.exchange.storage.postgres;

import com.exchange.common.config.ExchangeConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles database connectivity using centrally managed configuration.
 */
public class NeonDbConnectionManager {

    private static final Logger LOGGER = Logger.getLogger(NeonDbConnectionManager.class.getName());
    
    private final String dbUrl;
    private final String dbUser;
    private final String dbPass;

    public NeonDbConnectionManager(ExchangeConfig config) {
        this.dbUrl = config.getDatabase().getUrl();
        this.dbUser = config.getDatabase().getUsername();
        this.dbPass = config.getDatabase().getPassword();
    }

    public Connection getConnection() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
            // If the Neon DB URL already contains ?user=...&password=...
            // we can just use the 1-argument connection method.
            if (dbUser != null && !dbUser.isBlank() && !dbUser.equals("exchange_admin") 
                    && dbPass != null && !dbPass.isBlank() && !dbPass.equals("your-secure-password")) {
                return DriverManager.getConnection(dbUrl, dbUser, dbPass);
            } else {
                return DriverManager.getConnection(dbUrl);
            }
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "PostgreSQL JDBC Driver not found.", e);
            throw new SQLException("PostgreSQL JDBC Driver not found.", e);
        }
    }
}
