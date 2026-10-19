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
public class PythonRequirementsDetector implements ManifestDetector {

    private static final Logger log = LoggerFactory.getLogger(PythonRequirementsDetector.class);
    private static final Pattern PKG_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+");

    @Override
    public String getName() {
        return "python-requirements-detector";
    }

    @Override
    public boolean canDetect(Path workspaceRoot) {
        return !WorkspaceScanner.findFiles(workspaceRoot, 3, path -> {
            String name = path.getFileName().toString();
            return "requirements.txt".equalsIgnoreCase(name) || "Pipfile".equalsIgnoreCase(name) || "pyproject.toml".equalsIgnoreCase(name);
        }).isEmpty();
    }

    @Override
    public List<DetectedNode> detect(Path workspaceRoot) {
        List<DetectedNode> nodes = new ArrayList<>();
        List<Path> manifestFiles = WorkspaceScanner.findFiles(workspaceRoot, 3, path -> {
            String name = path.getFileName().toString();
            return "requirements.txt".equalsIgnoreCase(name) || "Pipfile".equalsIgnoreCase(name) || "pyproject.toml".equalsIgnoreCase(name);
        });

        // Track visited parent directories so multiple files in same folder don't duplicate nodes
        Set<Path> processedDirectories = new HashSet<>();

        for (Path manifestPath : manifestFiles) {
            Path parent = manifestPath.getParent();
            if (parent != null && processedDirectories.contains(parent)) {
                continue;
            }
            if (parent != null) {
                processedDirectories.add(parent);
            }

            try {
                String name = (parent != null && parent.getFileName() != null && !parent.equals(workspaceRoot))
                        ? parent.getFileName().toString()
                        : (workspaceRoot.getFileName() != null ? workspaceRoot.getFileName().toString() : "python-service");

                List<String> lines = Files.readAllLines(manifestPath);
                List<String> topPackages = new ArrayList<>();
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("-")) continue;
                    Matcher m = PKG_PATTERN.matcher(line);
                    if (m.find()) {
                        topPackages.add(m.group().toLowerCase(Locale.ROOT));
                        if (topPackages.size() >= 15) break;
                    }
                }

                String type = "SERVICE";
                String technology = "Python";
                double confidence = 0.40;
                String relativePath = WorkspaceScanner.getRelativePath(workspaceRoot, manifestPath);
                String id = "service:" + name;

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("manifest", manifestPath.getFileName().toString());
                metadata.put("topPackages", topPackages);

                nodes.add(new DetectedNode(id, name, type, technology, confidence, relativePath, metadata));

            } catch (IOException e) {
                log.warn("Failed to parse Python manifest at {}: {}", manifestPath, e.getMessage());
            }
        }

        return nodes;
    }
}
