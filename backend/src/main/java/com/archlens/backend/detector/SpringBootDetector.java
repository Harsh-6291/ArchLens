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
public class SpringBootDetector implements ManifestDetector {

    private static final Logger log = LoggerFactory.getLogger(SpringBootDetector.class);

    private static final Pattern SPRING_BOOT_APP_PATTERN = Pattern.compile("@SpringBootApplication|@EnableAutoConfiguration");
    private static final Pattern REST_CONTROLLER_PATTERN = Pattern.compile("@(?:Rest)?Controller\\b");
    private static final Pattern SERVICE_PATTERN = Pattern.compile("@Service\\b");
    private static final Pattern REPOSITORY_ANNOTATION_PATTERN = Pattern.compile("@Repository\\b");
    private static final Pattern REPOSITORY_EXTENDS_PATTERN = Pattern.compile("extends\\s+[A-Za-z0-9_.]*(?:CrudRepository|JpaRepository|PagingAndSortingRepository|Repository)\\b");

    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("(?:public\\s+)?(?:class|interface)\\s+([A-Za-z0-9_]+)");
    private static final Pattern REQUEST_MAPPING_PATH_PATTERN = Pattern.compile("@RequestMapping\\s*\\(\\s*(?:(?:value|path)\\s*=\\s*)?[\"']([^\"']+)[\"']");

    @Override
    public String getName() {
        return "spring-boot-tier2-detector";
    }

    @Override
    public boolean canDetect(Path workspaceRoot) {
        if (workspaceRoot == null || !Files.exists(workspaceRoot)) {
            return false;
        }

        List<Path> javaFiles = WorkspaceScanner.findFiles(workspaceRoot, 10, path -> path.toString().endsWith(".java"));
        for (Path javaFile : javaFiles) {
            try {
                String content = Files.readString(javaFile);
                if (SPRING_BOOT_APP_PATTERN.matcher(content).find()
                        || REST_CONTROLLER_PATTERN.matcher(content).find()
                        || SERVICE_PATTERN.matcher(content).find()) {
                    return true;
                }
            } catch (IOException ignored) {}
        }
        return false;
    }

    @Override
    public List<DetectedNode> detect(Path workspaceRoot) {
        List<DetectedNode> nodes = new ArrayList<>();
        if (workspaceRoot == null || !Files.exists(workspaceRoot)) {
            return nodes;
        }

        List<Path> javaFiles = WorkspaceScanner.findFiles(workspaceRoot, 10, path -> path.toString().endsWith(".java"));
        if (javaFiles.isEmpty()) {
            return nodes;
        }

        String appName = null;
        String mainAppSource = null;
        List<Map<String, Object>> controllers = new ArrayList<>();
        List<Map<String, Object>> services = new ArrayList<>();
        List<Map<String, Object>> repositories = new ArrayList<>();

        for (Path javaFile : javaFiles) {
            try {
                String content = Files.readString(javaFile);
                String relativePath = WorkspaceScanner.getRelativePath(workspaceRoot, javaFile);

                Matcher classMatcher = CLASS_NAME_PATTERN.matcher(content);
                String className = classMatcher.find() ? classMatcher.group(1) : javaFile.getFileName().toString().replace(".java", "");

                // Check for @SpringBootApplication
                if (SPRING_BOOT_APP_PATTERN.matcher(content).find()) {
                    if (appName == null) {
                        appName = deriveServiceName(className, javaFile, workspaceRoot);
                        mainAppSource = relativePath;
                    }
                }

                // Check for Controller
                if (REST_CONTROLLER_PATTERN.matcher(content).find()) {
                    String baseRoute = null;
                    Matcher routeMatcher = REQUEST_MAPPING_PATH_PATTERN.matcher(content);
                    if (routeMatcher.find()) {
                        baseRoute = routeMatcher.group(1);
                    }
                    Map<String, Object> controllerInfo = new HashMap<>();
                    controllerInfo.put("className", className);
                    controllerInfo.put("baseRoute", baseRoute != null ? baseRoute : "/");
                    controllerInfo.put("sourceFile", relativePath);
                    controllers.add(controllerInfo);
                }

                // Check for Service
                if (SERVICE_PATTERN.matcher(content).find()) {
                    Map<String, Object> serviceInfo = new HashMap<>();
                    serviceInfo.put("className", className);
                    serviceInfo.put("sourceFile", relativePath);
                    services.add(serviceInfo);
                }

                // Check for Repository
                if (REPOSITORY_ANNOTATION_PATTERN.matcher(content).find() || REPOSITORY_EXTENDS_PATTERN.matcher(content).find()) {
                    Map<String, Object> repoInfo = new HashMap<>();
                    repoInfo.put("className", className);
                    repoInfo.put("sourceFile", relativePath);
                    repositories.add(repoInfo);
                }

            } catch (IOException e) {
                log.warn("Failed to read Java source file {}: {}", javaFile, e.getMessage());
            }
        }

        // If at least one Spring component was detected
        if (!controllers.isEmpty() || !services.isEmpty() || !repositories.isEmpty() || mainAppSource != null) {
            if (appName == null) {
                appName = (workspaceRoot.getFileName() != null)
                        ? workspaceRoot.getFileName().toString()
                        : "spring-boot-service";
            }
            if (mainAppSource == null && !controllers.isEmpty()) {
                mainAppSource = (String) controllers.get(0).get("sourceFile");
            }

            String id = "service:" + appName;
            String type = "SERVICE";
            String technology = "Spring Boot";
            double confidence = 0.85; // High confidence for Tier 2 deep analysis

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("tier", 2);
            metadata.put("framework", "Spring Boot");
            metadata.put("controllers", controllers);
            metadata.put("services", services);
            metadata.put("repositories", repositories);

            Map<String, Object> layerSummary = new HashMap<>();
            layerSummary.put("controllerCount", controllers.size());
            layerSummary.put("serviceCount", services.size());
            layerSummary.put("repositoryCount", repositories.size());
            metadata.put("layerSummary", layerSummary);

            nodes.add(new DetectedNode(id, appName, type, technology, confidence, mainAppSource, metadata));
        }

        return nodes;
    }

    private String deriveServiceName(String className, Path appFile, Path workspaceRoot) {
        // If className is e.g. DemoApplication -> demo-service or check directory
        Path parent = appFile.getParent();
        while (parent != null && !parent.equals(workspaceRoot)) {
            Path pom = parent.resolve("pom.xml");
            if (Files.exists(pom)) {
                Path dirName = parent.getFileName();
                if (dirName != null) return dirName.toString();
            }
            parent = parent.getParent();
        }
        if (workspaceRoot.getFileName() != null) {
            return workspaceRoot.getFileName().toString();
        }
        return className.replace("Application", "").toLowerCase(Locale.ROOT) + "-service";
    }
}
