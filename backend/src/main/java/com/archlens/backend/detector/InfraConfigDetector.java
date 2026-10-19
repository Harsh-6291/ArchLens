package com.archlens.backend.detector;

import com.archlens.backend.detector.model.DetectedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class InfraConfigDetector implements ManifestDetector {

    private static final Logger log = LoggerFactory.getLogger(InfraConfigDetector.class);

    private static final Set<String> TARGET_FILES = Set.of(
            "application.properties",
            "application.yml",
            "application.yaml",
            ".env.example",
            ".env.template",
            "docker-compose.yml",
            "docker-compose.yaml"
    );

    private static final Pattern JDBC_URL_PATTERN = Pattern.compile("jdbc:([a-zA-Z0-9_-]+):\\/\\/([^:/\\s?]+)(?::(\\d+))?(?:\\/([a-zA-Z0-9_.-]+))?");
    private static final Pattern REDIS_URL_PATTERN = Pattern.compile("redis(?:s)?:\\/\\/(?:[^@\\s]+@)?([^:/\\s]+)(?::(\\d+))?");
    private static final Pattern AMQP_URL_PATTERN = Pattern.compile("amqp(?:s)?:\\/\\/(?:[^@\\s]+@)?([^:/\\s]+)(?::(\\d+))?");

    @Override
    public String getName() {
        return "infra-config-detector";
    }

    @Override
    public boolean canDetect(Path workspaceRoot) {
        if (workspaceRoot == null || !Files.exists(workspaceRoot)) {
            return false;
        }
        return !WorkspaceScanner.findFiles(workspaceRoot, 4, path -> {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            return TARGET_FILES.contains(name) || name.startsWith("application-");
        }).isEmpty();
    }

    @Override
    public List<DetectedNode> detect(Path workspaceRoot) {
        List<DetectedNode> nodes = new ArrayList<>();
        if (workspaceRoot == null || !Files.exists(workspaceRoot)) {
            return nodes;
        }

        List<Path> configFiles = WorkspaceScanner.findFiles(workspaceRoot, 4, path -> {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            return TARGET_FILES.contains(name) || (name.startsWith("application-") && (name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".properties")));
        });

        Map<String, DetectedNode> detected = new LinkedHashMap<>();

        for (Path configFile : configFiles) {
            try {
                String filename = configFile.getFileName().toString().toLowerCase(Locale.ROOT);
                // DockerCompose is already specialized in DockerComposeDetector, but we also inspect application properties/env files
                if (filename.startsWith("docker-compose")) {
                    continue;
                }

                String relativePath = WorkspaceScanner.getRelativePath(workspaceRoot, configFile);
                List<String> lines = Files.readAllLines(configFile);

                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    // 1. Check for JDBC URLs
                    Matcher jdbcMatcher = JDBC_URL_PATTERN.matcher(line);
                    if (jdbcMatcher.find()) {
                        String dialect = jdbcMatcher.group(1).toLowerCase(Locale.ROOT);
                        String host = jdbcMatcher.group(2);
                        String port = jdbcMatcher.group(3);
                        String dbName = jdbcMatcher.group(4);

                        String tech = switch (dialect) {
                            case "postgresql", "postgres" -> "PostgreSQL";
                            case "mysql" -> "MySQL";
                            case "mariadb" -> "MariaDB";
                            case "h2" -> "H2";
                            default -> dialect.toUpperCase(Locale.ROOT);
                        };

                        String nodeName = (dbName != null && !dbName.isBlank()) ? dbName : tech.toLowerCase(Locale.ROOT) + "-db";
                        String id = "database:" + nodeName;

                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("source", relativePath);
                        metadata.put("host", host);
                        if (port != null) metadata.put("port", port);
                        if (dbName != null) metadata.put("databaseName", dbName);

                        detected.put(id, new DetectedNode(
                                id,
                                nodeName,
                                "SQL_DATABASE",
                                tech,
                                0.60,
                                relativePath,
                                metadata
                        ));
                    }

                    // 2. Check for Redis config
                    if (line.contains("redis") || line.contains("REDIS")) {
                        Matcher redisMatcher = REDIS_URL_PATTERN.matcher(line);
                        String host = redisMatcher.find() ? redisMatcher.group(1) : "redis";
                        String id = "cache:redis";

                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("source", relativePath);
                        metadata.put("host", host);

                        detected.put(id, new DetectedNode(
                                id,
                                "redis-cache",
                                "CACHE",
                                "Redis",
                                0.60,
                                relativePath,
                                metadata
                        ));
                    }

                    // 3. Check for RabbitMQ / AMQP config
                    if (line.contains("rabbitmq") || line.contains("RABBITMQ") || line.contains("amqp")) {
                        Matcher amqpMatcher = AMQP_URL_PATTERN.matcher(line);
                        String host = amqpMatcher.find() ? amqpMatcher.group(1) : "rabbitmq";
                        String id = "broker:rabbitmq";

                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("source", relativePath);
                        metadata.put("host", host);

                        detected.put(id, new DetectedNode(
                                id,
                                "rabbitmq-broker",
                                "MESSAGE_BROKER",
                                "RabbitMQ",
                                0.60,
                                relativePath,
                                metadata
                        ));
                    }

                    // 4. Check for Kafka config
                    if (line.contains("kafka") || line.contains("KAFKA")) {
                        String id = "broker:kafka";
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("source", relativePath);

                        detected.put(id, new DetectedNode(
                                id,
                                "kafka-cluster",
                                "MESSAGE_BROKER",
                                "Kafka",
                                0.60,
                                relativePath,
                                metadata
                        ));
                    }
                }

            } catch (IOException e) {
                log.warn("Failed to parse config file at {}: {}", configFile, e.getMessage());
            }
        }

        nodes.addAll(detected.values());
        return nodes;
    }
}
