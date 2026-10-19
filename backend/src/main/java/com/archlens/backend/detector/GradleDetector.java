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
public class GradleDetector implements ManifestDetector {

    private static final Logger log = LoggerFactory.getLogger(GradleDetector.class);
    private static final Pattern ROOT_PROJECT_PATTERN = Pattern.compile("rootProject\\.name\\s*=\\s*['\"]([^'\"]+)['\"]");

    @Override
    public String getName() {
        return "gradle-detector";
    }

    @Override
    public boolean canDetect(Path workspaceRoot) {
        return !WorkspaceScanner.findFiles(workspaceRoot, 3, path -> {
            String name = path.getFileName().toString();
            return "build.gradle".equalsIgnoreCase(name) || "build.gradle.kts".equalsIgnoreCase(name);
        }).isEmpty();
    }

    @Override
    public List<DetectedNode> detect(Path workspaceRoot) {
        List<DetectedNode> nodes = new ArrayList<>();
        List<Path> buildFiles = WorkspaceScanner.findFiles(workspaceRoot, 3, path -> {
            String name = path.getFileName().toString();
            return "build.gradle".equalsIgnoreCase(name) || "build.gradle.kts".equalsIgnoreCase(name);
        });

        for (Path buildPath : buildFiles) {
            try {
                boolean isKotlin = buildPath.getFileName().toString().endsWith(".kts");
                String name = findProjectName(buildPath, workspaceRoot);

                String type = "SERVICE";
                String technology = isKotlin ? "Kotlin (Gradle)" : "Java (Gradle)";
                double confidence = 0.40;
                String relativePath = WorkspaceScanner.getRelativePath(workspaceRoot, buildPath);
                String id = "service:" + name;

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("manifest", buildPath.getFileName().toString());
                metadata.put("dsl", isKotlin ? "kotlin" : "groovy");

                nodes.add(new DetectedNode(id, name, type, technology, confidence, relativePath, metadata));

            } catch (Exception e) {
                log.warn("Failed to inspect gradle build at {}: {}", buildPath, e.getMessage());
            }
        }

        return nodes;
    }

    private String findProjectName(Path buildPath, Path workspaceRoot) {
        Path dir = buildPath.getParent();
        if (dir != null) {
            Path settings = dir.resolve("settings.gradle");
            if (!Files.exists(settings)) {
                settings = dir.resolve("settings.gradle.kts");
            }
            if (Files.exists(settings)) {
                try {
                    String content = Files.readString(settings);
                    Matcher matcher = ROOT_PROJECT_PATTERN.matcher(content);
                    if (matcher.find()) {
                        return matcher.group(1).trim();
                    }
                } catch (IOException ignored) {}
            }
            if (dir.getFileName() != null) {
                return dir.getFileName().toString();
            }
        }
        return "gradle-service";
    }
}
