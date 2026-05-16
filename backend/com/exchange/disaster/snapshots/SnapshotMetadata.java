package com.exchange.disaster.snapshots;

import java.io.Serializable;

/**
 * Metadata stored securely inside the snapshot binary to ensure deterministic
 * recovery and strict integrity verification.
 */
public class SnapshotMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long sequenceId;
    private final long timestamp;
    private final int engineVersion;
    private final long checksum;
    private final String compatibilityMarker;
    private final int symbolsCount;

    public SnapshotMetadata(long sequenceId, long timestamp, int engineVersion, 
                            long checksum, String compatibilityMarker, int symbolsCount) {
        this.sequenceId = sequenceId;
        this.timestamp = timestamp;
        this.engineVersion = engineVersion;
        this.checksum = checksum;
        this.compatibilityMarker = compatibilityMarker;
        this.symbolsCount = symbolsCount;
    }

    public long getSequenceId() { return sequenceId; }
    public long getTimestamp() { return timestamp; }
    public int getEngineVersion() { return engineVersion; }
    public long getChecksum() { return checksum; }
    public String getCompatibilityMarker() { return compatibilityMarker; }
    public int getSymbolsCount() { return symbolsCount; }
}
