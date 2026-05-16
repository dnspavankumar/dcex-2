package com.exchange.testing.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BenchmarkReportWriter {
    private BenchmarkReportWriter() {
    }

    public static Path write(String suite, String name, Map<String, ?> metrics) throws IOException {
        Path reportDir = Path.of("target", "benchmark-reports", suite);
        Files.createDirectories(reportDir);

        Path reportPath = reportDir.resolve(name + ".json");
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("suite", suite);
        ordered.put("name", name);
        ordered.put("generatedAt", Instant.now().toString());
        ordered.putAll(metrics);

        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        int index = 0;
        for (Map.Entry<String, Object> entry : ordered.entrySet()) {
            builder.append("  \"").append(entry.getKey()).append("\": ");
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else {
                builder.append("\"").append(String.valueOf(value).replace("\"", "\\\"")).append("\"");
            }
            if (index++ < ordered.size() - 1) {
                builder.append(",");
            }
            builder.append("\n");
        }
        builder.append("}\n");

        Files.writeString(reportPath, builder.toString(), StandardCharsets.UTF_8);
        return reportPath;
    }
}
