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
public class GoModDetector implements ManifestDetector {

    private static final Logger log = LoggerFactory.getLogger(GoModDetector.class);
    private static final Pattern MODULE_PATTERN = Pattern.compile("^module\\s+(\\S+)");
    private static final Pattern GO_VERSION_PATTERN = Pattern.compile("^go\\s+(\\S+)");

    @Override
    public String getName() {
        return "go-mod-detector";
    }

    @Override
    public boolean canDetect(Path workspaceRoot) {
        return !WorkspaceScanner.findByFileName(workspaceRoot, "go.mod", 3).isEmpty();
    }

    @Override
    public List<DetectedNode> detect(Path workspaceRoot) {
        List<DetectedNode> nodes = new ArrayList<>();
        List<Path> goModFiles = WorkspaceScanner.findByFileName(workspaceRoot, "go.mod", 3);

        for (Path goModPath : goModFiles) {
            try {
                List<String> lines = Files.readAllLines(goModPath);
                String moduleName = null;
                String goVersion = null;

                for (String line : lines) {
                    line = line.trim();
                    if (moduleName == null) {
                        Matcher m = MODULE_PATTERN.matcher(line);
                        if (m.find()) {
                            moduleName = m.group(1);
                        }
                    }
                    if (goVersion == null) {
                        Matcher m = GO_VERSION_PATTERN.matcher(line);
                        if (m.find()) {
                            goVersion = m.group(1);
                        }
                    }
                }

                String name = null;
                if (moduleName != null && !moduleName.isBlank()) {
                    int lastSlash = moduleName.lastIndexOf('/');
                    name = (lastSlash >= 0 && lastSlash < moduleName.length() - 1)
                            ? moduleName.substring(lastSlash + 1)
                            : moduleName;
                }
                if (name == null || name.isBlank()) {
                    Path parent = goModPath.getParent();
                    name = (parent != null && parent.getFileName() != null)
                            ? parent.getFileName().toString()
                            : "go-service";
                }

                String type = "SERVICE";
                String technology = "Go";
                double confidence = 0.40;
                String relativePath = WorkspaceScanner.getRelativePath(workspaceRoot, goModPath);
                String id = "service:" + name;

                Map<String, Object> metadata = new HashMap<>();
                if (moduleName != null) metadata.put("module", moduleName);
                if (goVersion != null) metadata.put("goVersion", goVersion);
                metadata.put("manifest", "go.mod");

                nodes.add(new DetectedNode(id, name, type, technology, confidence, relativePath, metadata));

            } catch (IOException e) {
                log.warn("Failed to parse go.mod at {}: {}", goModPath, e.getMessage());
            }
        }

        return nodes;
    }
}
