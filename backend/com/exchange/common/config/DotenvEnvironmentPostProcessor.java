package com.exchange.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Executes during the absolute earliest phase of Spring Boot application startup.
 * Loads environment variables from .env files, prioritizing OS-level variables.
 * Enforces hierarchical config isolation (.env -> .env.local -> .env.production).
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "dotenvProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> dotenvMap = new HashMap<>();

        // Determine active profile for hierarchical loading
        String[] activeProfiles = environment.getActiveProfiles();
        String profile = activeProfiles.length > 0 ? activeProfiles[0] : "local";

        // Load hierarchy (Bottom to Top precedence in this loading array)
        String[] filesToLoad = {".env", ".env." + profile};

        for (String fileName : filesToLoad) {
            Path path = Paths.get(fileName);
            if (Files.exists(path)) {
                loadEnvFile(path, dotenvMap);
            }
        }

        // Environment variables override .env files natively in Spring Boot if we inject this 
        // behind systemEnvironment property source. However, to guarantee it, we explicitly merge
        // and ignore keys already present in System.getenv().
        Map<String, String> osEnv = System.getenv();
        Map<String, Object> finalProperties = new HashMap<>();

        for (Map.Entry<String, Object> entry : dotenvMap.entrySet()) {
            if (!osEnv.containsKey(entry.getKey())) {
                finalProperties.put(entry.getKey(), entry.getValue());
                
                // Also map snake_case to Spring Boot compatible dotted keys
                // e.g., EXCHANGE_DATABASE_URL -> exchange.database.url
                String springKey = entry.getKey().toLowerCase().replace("_", ".");
                finalProperties.put(springKey, entry.getValue());
            }
        }

        if (!finalProperties.isEmpty()) {
            MutablePropertySources propertySources = environment.getPropertySources();
            // Insert it after System Environment variables but before application.yml
            if (propertySources.contains("systemEnvironment")) {
                propertySources.addAfter("systemEnvironment", new MapPropertySource(PROPERTY_SOURCE_NAME, finalProperties));
            } else {
                propertySources.addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, finalProperties));
            }
        }
    }

    private void loadEnvFile(Path path, Map<String, Object> map) {
        try (Stream<String> lines = Files.lines(path)) {
            lines.filter(line -> line != null && !line.trim().isEmpty() && !line.trim().startsWith("#"))
                 .forEach(line -> {
                     int separatorIndex = line.indexOf('=');
                     if (separatorIndex > 0) {
                         String key = line.substring(0, separatorIndex).trim();
                         String value = line.substring(separatorIndex + 1).trim();
                         // Strip quotes if any
                         if (value.startsWith("\"") && value.endsWith("\"")) {
                             value = value.substring(1, value.length() - 1);
                         }
                         map.put(key, value);
                     }
                 });
        } catch (IOException e) {
            System.err.println("Failed to read environment file: " + path.getFileName());
        }
    }
}
