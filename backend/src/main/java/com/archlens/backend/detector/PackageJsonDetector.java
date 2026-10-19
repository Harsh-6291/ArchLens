package com.archlens.backend.detector;

import com.archlens.backend.detector.model.DetectedNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Component
public class PackageJsonDetector implements ManifestDetector {

    private static final Logger log = LoggerFactory.getLogger(PackageJsonDetector.class);
    private static final Set<String> FRONTEND_FRAMEWORKS = Set.of(
            "react", "react-dom", "next", "vue", "nuxt", "@angular/core", "svelte", "gatsby", "solid-js", "remix"
    );

    private final ObjectMapper objectMapper;

    public PackageJsonDetector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "package-json-detector";
    }

    @Override
    public boolean canDetect(Path workspaceRoot) {
        return !WorkspaceScanner.findByFileName(workspaceRoot, "package.json", 3).isEmpty();
    }

    @Override
    public List<DetectedNode> detect(Path workspaceRoot) {
        List<DetectedNode> nodes = new ArrayList<>();
        List<Path> manifestFiles = WorkspaceScanner.findByFileName(workspaceRoot, "package.json", 3);

        for (Path packageJsonPath : manifestFiles) {
            try {
                byte[] bytes = Files.readAllBytes(packageJsonPath);
                JsonNode root = objectMapper.readTree(bytes);

                String name = root.hasNonNull("name") ? root.get("name").asText() : null;
                if (name == null || name.isBlank()) {
                    Path parent = packageJsonPath.getParent();
                    name = (parent != null && parent.getFileName() != null)
                            ? parent.getFileName().toString()
                            : "node-service";
                }

                Set<String> allDeps = new HashSet<>();
                extractDepNames(root.get("dependencies"), allDeps);
                extractDepNames(root.get("devDependencies"), allDeps);

                boolean isFrontend = allDeps.stream().anyMatch(FRONTEND_FRAMEWORKS::contains);
                boolean isTypeScript = allDeps.contains("typescript")
                        || (packageJsonPath.getParent() != null && Files.exists(packageJsonPath.getParent().resolve("tsconfig.json")));

                String type = isFrontend ? "WEB_APPLICATION" : "SERVICE";
                String technology = isTypeScript ? "TypeScript / Node.js" : "Node.js";
                double confidence = 0.40;

                String relativePath = WorkspaceScanner.getRelativePath(workspaceRoot, packageJsonPath);
                String id = (isFrontend ? "web:" : "service:") + name;

                Map<String, Object> metadata = new HashMap<>();
                if (root.hasNonNull("version")) {
                    metadata.put("version", root.get("version").asText());
                }
                metadata.put("manifest", "package.json");
                metadata.put("topDependencies", allDeps.stream().limit(15).toList());

                nodes.add(new DetectedNode(id, name, type, technology, confidence, relativePath, metadata));

            } catch (IOException e) {
                log.warn("Failed to parse package.json at {}: {}", packageJsonPath, e.getMessage());
            }
        }

        return nodes;
    }

    private void extractDepNames(JsonNode depNode, Set<String> target) {
        if (depNode != null && depNode.isObject()) {
            Iterator<String> fieldNames = depNode.fieldNames();
            while (fieldNames.hasNext()) {
                target.add(fieldNames.next().toLowerCase(Locale.ROOT));
            }
        }
    }
}
