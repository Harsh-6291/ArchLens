package com.archlens.backend.detector;

import com.archlens.backend.detector.model.DetectedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Component
public class DockerComposeDetector implements ManifestDetector {

    private static final Logger log = LoggerFactory.getLogger(DockerComposeDetector.class);

    @Override
    public String getName() {
        return "docker-compose-detector";
    }

    @Override
    public boolean canDetect(Path workspaceRoot) {
        return !WorkspaceScanner.findFiles(workspaceRoot, 3, path -> {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            return name.equals("docker-compose.yml") || name.equals("docker-compose.yaml")
                    || name.equals("compose.yml") || name.equals("compose.yaml");
        }).isEmpty();
    }

    @Override
    public List<DetectedNode> detect(Path workspaceRoot) {
        List<DetectedNode> nodes = new ArrayList<>();
        List<Path> composeFiles = WorkspaceScanner.findFiles(workspaceRoot, 3, path -> {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            return name.equals("docker-compose.yml") || name.equals("docker-compose.yaml")
                    || name.equals("compose.yml") || name.equals("compose.yaml");
        });

        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

        for (Path composePath : composeFiles) {
            try (InputStream is = Files.newInputStream(composePath)) {
                Object loaded = yaml.load(is);
                if (!(loaded instanceof Map<?, ?> rootMap)) {
                    continue;
                }

                Object servicesObj = rootMap.get("services");
                if (!(servicesObj instanceof Map<?, ?> servicesMap)) {
                    continue;
                }

                String relativePath = WorkspaceScanner.getRelativePath(workspaceRoot, composePath);

                for (Map.Entry<?, ?> entry : servicesMap.entrySet()) {
                    if (entry.getKey() == null || !(entry.getValue() instanceof Map<?, ?> serviceDef)) {
                        continue;
                    }
                    String serviceName = entry.getKey().toString();
                    Object imageObj = serviceDef.get("image");
                    String image = imageObj != null ? imageObj.toString() : null;
                    boolean hasBuild = serviceDef.containsKey("build");

                    ComponentClassification classification = classifyService(serviceName, image, hasBuild);

                    String id = classification.type().toLowerCase(Locale.ROOT) + ":" + serviceName;
                    double confidence = classification.confidence();

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("manifest", composePath.getFileName().toString());
                    metadata.put("serviceName", serviceName);
                    if (image != null) {
                        metadata.put("image", image);
                    }

                    // Extract ports safely without exposing any secret environment variables
                    Object portsObj = serviceDef.get("ports");
                    if (portsObj instanceof List<?> portsList) {
                        List<String> ports = portsList.stream()
                                .filter(Objects::nonNull)
                                .map(Object::toString)
                                .toList();
                        metadata.put("ports", ports);
                    }

                    nodes.add(new DetectedNode(
                            id,
                            serviceName,
                            classification.type(),
                            classification.technology(),
                            confidence,
                            relativePath,
                            metadata
                    ));
                }

            } catch (Exception e) {
                log.warn("Failed to parse docker-compose at {}: {}", composePath, e.getMessage());
            }
        }

        return nodes;
    }

    private ComponentClassification classifyService(String serviceName, String image, boolean hasBuild) {
        String lowerImage = (image != null) ? image.toLowerCase(Locale.ROOT) : "";
        String lowerName = serviceName.toLowerCase(Locale.ROOT);

        // SQL Databases
        if (lowerImage.contains("postgres") || lowerName.contains("postgres") || lowerName.contains("pgsql")) {
            return new ComponentClassification("SQL_DATABASE", "PostgreSQL", 0.50);
        }
        if (lowerImage.contains("mysql") || lowerName.contains("mysql")) {
            return new ComponentClassification("SQL_DATABASE", "MySQL", 0.50);
        }
        if (lowerImage.contains("mariadb") || lowerName.contains("mariadb")) {
            return new ComponentClassification("SQL_DATABASE", "MariaDB", 0.50);
        }

        // NoSQL Databases
        if (lowerImage.contains("mongo") || lowerName.contains("mongo")) {
            return new ComponentClassification("NOSQL_DATABASE", "MongoDB", 0.50);
        }
        if (lowerImage.contains("cassandra") || lowerName.contains("cassandra")) {
            return new ComponentClassification("NOSQL_DATABASE", "Cassandra", 0.50);
        }

        // Cache
        if (lowerImage.contains("redis") || lowerName.contains("redis") || lowerImage.contains("valkey")) {
            return new ComponentClassification("CACHE", "Redis", 0.50);
        }
        if (lowerImage.contains("memcached") || lowerName.contains("memcached")) {
            return new ComponentClassification("CACHE", "Memcached", 0.50);
        }

        // Message Brokers
        if (lowerImage.contains("rabbitmq") || lowerName.contains("rabbitmq")) {
            return new ComponentClassification("MESSAGE_BROKER", "RabbitMQ", 0.50);
        }
        if (lowerImage.contains("kafka") || lowerName.contains("kafka")) {
            return new ComponentClassification("MESSAGE_BROKER", "Kafka", 0.50);
        }
        if (lowerImage.contains("nats") || lowerName.contains("nats")) {
            return new ComponentClassification("MESSAGE_BROKER", "NATS", 0.50);
        }

        // Search Engines
        if (lowerImage.contains("elasticsearch") || lowerName.contains("elasticsearch")) {
            return new ComponentClassification("SEARCH_ENGINE", "Elasticsearch", 0.50);
        }

        // Generic application services
        if (hasBuild) {
            return new ComponentClassification("SERVICE", "Custom Container Service", 0.45);
        }

        return new ComponentClassification("CONTAINER", (image != null ? image : "Docker Container"), 0.40);
    }

    private record ComponentClassification(String type, String technology, double confidence) {}
}
