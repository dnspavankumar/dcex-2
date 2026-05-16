package com.exchange.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

/**
 * Strongly typed, validated, and immutable configuration schema for the exchange.
 * Loaded via Spring Boot @ConfigurationProperties after the DotenvEnvironmentPostProcessor injects secrets.
 */
@Component
@ConfigurationProperties(prefix = "exchange")
public class ExchangeConfig {
    private static final Logger LOGGER = Logger.getLogger(ExchangeConfig.class.getName());

    private final Database database = new Database();
    private final Wal wal = new Wal();
    private final Network network = new Network();
    private final Tuning tuning = new Tuning();
    private final Security security = new Security();

    public void validate() {
        if (database.getUrl() == null || database.getUrl().isBlank()) {
            throw new IllegalStateException("CRITICAL: Neon Database URL is missing or empty.");
        }
        if (security.getJwtSecret() == null || security.getJwtSecret().length() < 32) {
            throw new IllegalStateException("CRITICAL: JWT Secret must be present and securely sized.");
        }
        LOGGER.info("Exchange Configuration validated successfully.");
    }

    public Database getDatabase() { return database; }
    public Wal getWal() { return wal; }
    public Network getNetwork() { return network; }
    public Tuning getTuning() { return tuning; }
    public Security getSecurity() { return security; }

    // Nested Configuration Namespaces

    public static class Database {
        private String url;
        private String username;
        private String password;
        private int batchThreshold = 1000;
        private long flushIntervalMs = 500;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public int getBatchThreshold() { return batchThreshold; }
        public void setBatchThreshold(int batchThreshold) { this.batchThreshold = batchThreshold; }
        public long getFlushIntervalMs() { return flushIntervalMs; }
        public void setFlushIntervalMs(long flushIntervalMs) { this.flushIntervalMs = flushIntervalMs; }
    }

    public static class Wal {
        private String directory = "/data/wal";
        private int mappedSizeMb = 256;
        private boolean fsyncEnabled = true;

        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }
        public int getMappedSizeMb() { return mappedSizeMb; }
        public void setMappedSizeMb(int mappedSizeMb) { this.mappedSizeMb = mappedSizeMb; }
        public boolean isFsyncEnabled() { return fsyncEnabled; }
        public void setFsyncEnabled(boolean fsyncEnabled) { this.fsyncEnabled = fsyncEnabled; }
    }

    public static class Network {
        private int grpcPort = 9090;
        private int websocketPort = 8080;

        public int getGrpcPort() { return grpcPort; }
        public void setGrpcPort(int grpcPort) { this.grpcPort = grpcPort; }
        public int getWebsocketPort() { return websocketPort; }
        public void setWebsocketPort(int websocketPort) { this.websocketPort = websocketPort; }
    }

    public static class Tuning {
        private int ringBufferCapacity = 16384;
        private int snapshotIntervalSeconds = 10;
        private String gcProfile = "ZGC";

        public int getRingBufferCapacity() { return ringBufferCapacity; }
        public void setRingBufferCapacity(int ringBufferCapacity) { this.ringBufferCapacity = ringBufferCapacity; }
        public int getSnapshotIntervalSeconds() { return snapshotIntervalSeconds; }
        public void setSnapshotIntervalSeconds(int snapshotIntervalSeconds) { this.snapshotIntervalSeconds = snapshotIntervalSeconds; }
        public String getGcProfile() { return gcProfile; }
        public void setGcProfile(String gcProfile) { this.gcProfile = gcProfile; }
    }

    public static class Security {
        private String jwtSecret;

        public String getJwtSecret() { return jwtSecret; }
        public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
    }
}
