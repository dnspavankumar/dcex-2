package com.exchange.order.journal;

import com.exchange.common.config.ExchangeConfig;
import com.exchange.matching.models.Order;
import com.exchange.observability.metrics.LatencyObservabilityFramework;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class WriteAheadLog {
    private final FileChannel fileChannel;
    private MappedByteBuffer mappedBuffer;
    private final LatencyObservabilityFramework latencyMetrics;
    
    private final int mappedSize;
    private final boolean fsyncEnabled;

    public WriteAheadLog(ExchangeConfig config, LatencyObservabilityFramework latencyMetrics) throws IOException {
        this.latencyMetrics = latencyMetrics;
        this.mappedSize = config.getWal().getMappedSizeMb() * 1024 * 1024;
        this.fsyncEnabled = config.getWal().isFsyncEnabled();
        
        File dir = new File(config.getWal().getDirectory());
        if (!dir.exists()) dir.mkdirs();
        
        File file = new File(dir, "exchange_wal.dat");
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        this.fileChannel = raf.getChannel();
        this.mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, fileChannel.size(), mappedSize);
    }

    public synchronized void append(Order order) throws IOException {
        long startTime = System.nanoTime();
        
        String entry = String.format("%d|%d|%d|%s|%s|%s|%s|%d\n",
                order.getSequenceId(), order.getOrderId(), order.getUserId(),
                order.getSymbol(), order.getSide().name(), order.getType().name(),
                order.getPrice().toString(), order.getQuantity().toString(),
                order.getTimestamp());

        byte[] bytes = entry.getBytes();
        if (mappedBuffer.remaining() < bytes.length) {
            long newPosition = fileChannel.size() + mappedBuffer.position();
            mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, newPosition, mappedSize);
        }

        mappedBuffer.put(bytes);
        
        if (fsyncEnabled) {
            mappedBuffer.force(); 
        }
        
        latencyMetrics.recordWalFsyncLatency(System.nanoTime() - startTime);
    }

    public List<Order> readSince(long fromSequenceId) {
        return new ArrayList<>();
    }
}
