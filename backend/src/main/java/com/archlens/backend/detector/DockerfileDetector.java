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
public class DockerfileDetector implements ManifestDetector {

    private static final Logger log = LoggerFactory.getLogger(DockerfileDetector.class);
    private static final Pattern FROM_PATTERN = Pattern.compile("(?i)^FROM\\s+([a-zA-Z0-9_./:-]+)");

    @Override
    public String getName() {
        return "dockerfile-detector";
    }

    @Override
    public boolean canDetect(Path workspaceRoot) {
        return !WorkspaceScanner.findFiles(workspaceRoot, 3, path -> {
            String name = path.getFileName().toString();
            return "Dockerfile".equalsIgnoreCase(name) || name.startsWith("Dockerfile.");
        }).isEmpty();
    }

    @Override
    public List<DetectedNode> detect(Path workspaceRoot) {
        List<DetectedNode> nodes = new ArrayList<>();
        List<Path> dockerfiles = WorkspaceScanner.findFiles(workspaceRoot, 3, path -> {
            String name = path.getFileName().toString();
            return "Dockerfile".equalsIgnoreCase(name) || name.startsWith("Dockerfile.");
        });

        for (Path dockerfilePath : dockerfiles) {
            try {
                List<String> lines = Files.readAllLines(dockerfilePath);
                String baseImage = null;

                for (String line : lines) {
                    line = line.trim();
                    Matcher m = FROM_PATTERN.matcher(line);
                    if (m.find()) {
                        baseImage = m.group(1);
                        // keep the last FROM in multi-stage builds as the runtime base image
                    }
                }

                Path parent = dockerfilePath.getParent();
                String folderName = (parent != null && parent.getFileName() != null && !parent.equals(workspaceRoot))
                        ? parent.getFileName().toString()
                        : (workspaceRoot.getFileName() != null ? workspaceRoot.getFileName().toString() : "app");

                String filename = dockerfilePath.getFileName().toString();
                String name = "Dockerfile".equalsIgnoreCase(filename)
                        ? folderName + "-container"
                        : folderName + "-" + filename.toLowerCase(Locale.ROOT);

                String type = "CONTAINER";
                String technology = "Docker";
                double confidence = 0.40;
                String relativePath = WorkspaceScanner.getRelativePath(workspaceRoot, dockerfilePath);
                String id = "container:" + name;

                Map<String, Object> metadata = new HashMap<>();
                if (baseImage != null) metadata.put("baseImage", baseImage);
                metadata.put("manifest", filename);

                nodes.add(new DetectedNode(id, name, type, technology, confidence, relativePath, metadata));

            } catch (IOException e) {
                log.warn("Failed to parse Dockerfile at {}: {}", dockerfilePath, e.getMessage());
            }
        }

        return nodes;
    }
}
