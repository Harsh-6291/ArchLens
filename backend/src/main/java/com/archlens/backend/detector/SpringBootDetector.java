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

/**
 * Tier 2 deep detector for Spring Boot repositories.
 *
 * Detects:
 *  - @SpringBootApplication / @EnableAutoConfiguration (entry point)
 *  - @RestController / @Controller  → service nodes, controller metadata
 *  - @Service                       → service-layer components
 *  - @Repository / JpaRepository extends → data-access components
 *
 * Route extraction (M2.1):
 *  For every @RestController class the detector captures all HTTP endpoint
 *  handler methods annotated with @RequestMapping, @GetMapping, @PostMapping,
 *  @PutMapping, @DeleteMapping, @PatchMapping, producing one "route" entry per
 *  method with: httpMethod, path (base + method), handlerMethod, lineNumber.
 *
 *  The routes list is attached to the DetectedNode's metadata under the key
 *  "routes" so that ApiExtractionService can persist them to api_definitions.
 */
@Component
public class SpringBootDetector implements ManifestDetector {

    private static final Logger log = LoggerFactory.getLogger(SpringBootDetector.class);

    // --- Class-level annotation patterns ---
    private static final Pattern SPRING_BOOT_APP_PATTERN =
            Pattern.compile("@SpringBootApplication|@EnableAutoConfiguration");
    private static final Pattern REST_CONTROLLER_PATTERN =
            Pattern.compile("@(?:Rest)?Controller\\b");
    private static final Pattern SERVICE_PATTERN =
            Pattern.compile("@Service\\b");
    private static final Pattern REPOSITORY_ANNOTATION_PATTERN =
            Pattern.compile("@Repository\\b");
    private static final Pattern REPOSITORY_EXTENDS_PATTERN =
            Pattern.compile("extends\\s+[A-Za-z0-9_.]*(?:CrudRepository|JpaRepository|PagingAndSortingRepository|Repository)\\b");

    private static final Pattern CLASS_NAME_PATTERN =
            Pattern.compile("(?:public\\s+)?(?:class|interface)\\s+([A-Za-z0-9_]+)");

    // Class-level @RequestMapping — captures base path
    private static final Pattern CLASS_REQUEST_MAPPING_PATTERN =
            Pattern.compile("@RequestMapping\\s*\\(\\s*(?:(?:value|path)\\s*=\\s*)?[\"']([^\"']+)[\"']");

    // Method-level mapping annotations.
    // Group 1 = annotation name (GetMapping|PostMapping|…|RequestMapping)
    // Group 2 = path value (optional — may be absent for bare @GetMapping)
    private static final Pattern METHOD_MAPPING_PATTERN =
            Pattern.compile(
                    "@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)" +
                    "(?:\\s*\\(\\s*(?:(?:value|path)\\s*=\\s*)?(?:\"([^\"]*)\"|'([^']*)'|\\{[^}]*\"([^\"]*)\"))?",
                    Pattern.CASE_INSENSITIVE);

    // Method declaration: captures method name (public ReturnType methodName(
    private static final Pattern METHOD_DECL_PATTERN =
            Pattern.compile("(?:public|protected|private)(?:\\s+\\S+)+\\s+([A-Za-z0-9_]+)\\s*\\(");

    // @RequestMapping method attribute: method = RequestMethod.GET
    private static final Pattern REQUEST_METHOD_ATTR_PATTERN =
            Pattern.compile("method\\s*=\\s*RequestMethod\\.([A-Z]+)");

    @Override
    public String getName() {
        return "spring-boot-tier2-detector";
    }

    @Override
    public boolean canDetect(Path workspaceRoot) {
        if (workspaceRoot == null || !Files.exists(workspaceRoot)) return false;
        List<Path> javaFiles = WorkspaceScanner.findFiles(workspaceRoot, 10,
                path -> path.toString().endsWith(".java"));
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
        if (workspaceRoot == null || !Files.exists(workspaceRoot)) return nodes;

        List<Path> javaFiles = WorkspaceScanner.findFiles(workspaceRoot, 10,
                path -> path.toString().endsWith(".java"));
        if (javaFiles.isEmpty()) return nodes;

        String appName = null;
        String mainAppSource = null;
        List<Map<String, Object>> controllers = new ArrayList<>();
        List<Map<String, Object>> services    = new ArrayList<>();
        List<Map<String, Object>> repositories = new ArrayList<>();
        // All routes aggregated across all controllers in this service
        List<Map<String, Object>> allRoutes = new ArrayList<>();

        for (Path javaFile : javaFiles) {
            try {
                String content = Files.readString(javaFile);
                String relativePath = WorkspaceScanner.getRelativePath(workspaceRoot, javaFile);

                Matcher classMatcher = CLASS_NAME_PATTERN.matcher(content);
                String className = classMatcher.find()
                        ? classMatcher.group(1)
                        : javaFile.getFileName().toString().replace(".java", "");

                // @SpringBootApplication
                if (SPRING_BOOT_APP_PATTERN.matcher(content).find() && appName == null) {
                    appName = deriveServiceName(className, javaFile, workspaceRoot);
                    mainAppSource = relativePath;
                }

                // @RestController / @Controller
                if (REST_CONTROLLER_PATTERN.matcher(content).find()) {
                    String basePath = extractClassBasePath(content);

                    Map<String, Object> controllerInfo = new HashMap<>();
                    controllerInfo.put("className", className);
                    controllerInfo.put("baseRoute", basePath);
                    controllerInfo.put("sourceFile", relativePath);
                    controllers.add(controllerInfo);

                    // Route extraction for M2.1
                    List<Map<String, Object>> routes = extractRoutes(content, basePath, className, relativePath);
                    allRoutes.addAll(routes);
                }

                // @Service
                if (SERVICE_PATTERN.matcher(content).find()) {
                    Map<String, Object> serviceInfo = new HashMap<>();
                    serviceInfo.put("className", className);
                    serviceInfo.put("sourceFile", relativePath);
                    services.add(serviceInfo);
                }

                // @Repository
                if (REPOSITORY_ANNOTATION_PATTERN.matcher(content).find()
                        || REPOSITORY_EXTENDS_PATTERN.matcher(content).find()) {
                    Map<String, Object> repoInfo = new HashMap<>();
                    repoInfo.put("className", className);
                    repoInfo.put("sourceFile", relativePath);
                    repositories.add(repoInfo);
                }

            } catch (IOException e) {
                log.warn("Failed to read Java source file {}: {}", javaFile, e.getMessage());
            }
        }

        if (!controllers.isEmpty() || !services.isEmpty() || !repositories.isEmpty() || mainAppSource != null) {
            if (appName == null) {
                appName = workspaceRoot.getFileName() != null
                        ? workspaceRoot.getFileName().toString()
                        : "spring-boot-service";
            }
            if (mainAppSource == null && !controllers.isEmpty()) {
                mainAppSource = (String) controllers.get(0).get("sourceFile");
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("tier", 2);
            metadata.put("framework", "Spring Boot");
            metadata.put("controllers", controllers);
            metadata.put("services", services);
            metadata.put("repositories", repositories);
            metadata.put("routes", allRoutes);                // ← M2.1 route list

            Map<String, Object> layerSummary = new HashMap<>();
            layerSummary.put("controllerCount", controllers.size());
            layerSummary.put("serviceCount", services.size());
            layerSummary.put("repositoryCount", repositories.size());
            layerSummary.put("routeCount", allRoutes.size());
            metadata.put("layerSummary", layerSummary);

            nodes.add(new DetectedNode(
                    "service:" + appName, appName, "SERVICE", "Spring Boot",
                    0.85, mainAppSource, metadata));
        }

        return nodes;
    }

    // -------------------------------------------------------------------------
    // Route extraction helpers (M2.1)
    // -------------------------------------------------------------------------

    /**
     * Extracts the class-level base path from @RequestMapping on a controller.
     */
    private String extractClassBasePath(String content) {
        Matcher m = CLASS_REQUEST_MAPPING_PATTERN.matcher(content);
        if (m.find()) {
            return normalisePath(m.group(1));
        }
        return "";
    }

    /**
     * Scans the source file line-by-line and returns one map per discovered
     * handler method, containing: httpMethod, path, handlerClass, handlerMethod,
     * sourceFile, lineNumber.
     *
     * Strategy: maintain a small lookahead window. When we see a mapping
     * annotation we record it, then advance until the next non-annotation,
     * non-blank line which should be the method declaration.
     */
    private List<Map<String, Object>> extractRoutes(
            String content, String basePath, String className, String sourceFile) {

        List<Map<String, Object>> routes = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        boolean inClass = false;
        int i = 0;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (!inClass) {
                if (CLASS_NAME_PATTERN.matcher(line).find() || line.contains("class ")) {
                    inClass = true;
                }
                i++;
                continue;
            }

            Matcher mappingMatcher = METHOD_MAPPING_PATTERN.matcher(line);

            if (mappingMatcher.find()) {
                // Determine HTTP method
                String annotationName = mappingMatcher.group(1);
                String httpMethod = resolveHttpMethod(annotationName, line);

                // Determine method-level path (groups 2/3/4 are alternatives)
                String methodPath = firstNonNull(
                        mappingMatcher.group(2),
                        mappingMatcher.group(3),
                        mappingMatcher.group(4));
                if (methodPath == null) methodPath = "";

                String fullPath = combinePaths(basePath, methodPath);

                // Find the method declaration — scan ahead up to 5 lines
                String handlerMethodName = null;
                int handlerLine = -1;
                for (int j = i + 1; j < Math.min(i + 6, lines.length); j++) {
                    Matcher methodMatcher = METHOD_DECL_PATTERN.matcher(lines[j].trim());
                    if (methodMatcher.find()) {
                        handlerMethodName = methodMatcher.group(1);
                        handlerLine = j + 1; // 1-based
                        break;
                    }
                }

                if (handlerMethodName != null) {
                    Map<String, Object> route = new LinkedHashMap<>();
                    route.put("httpMethod",     httpMethod);
                    route.put("path",           fullPath);
                    route.put("handlerClass",   className);
                    route.put("handlerMethod",  handlerMethodName);
                    route.put("sourceFile",     sourceFile);
                    route.put("lineNumber",     handlerLine);
                    routes.add(route);
                }
            }
            i++;
        }

        return routes;
    }

    /**
     * Maps annotation name to HTTP verb. For @RequestMapping falls back to
     * explicit method= attribute or defaults to GET.
     */
    private String resolveHttpMethod(String annotationName, String annotationLine) {
        return switch (annotationName.toLowerCase(Locale.ROOT)) {
            case "getmapping"    -> "GET";
            case "postmapping"   -> "POST";
            case "putmapping"    -> "PUT";
            case "deletemapping" -> "DELETE";
            case "patchmapping"  -> "PATCH";
            default -> {
                // @RequestMapping — look for method= attribute on the same line
                Matcher m = REQUEST_METHOD_ATTR_PATTERN.matcher(annotationLine);
                yield m.find() ? m.group(1).toUpperCase(Locale.ROOT) : "GET";
            }
        };
    }

    private String combinePaths(String base, String method) {
        String b = normalisePath(base);
        String m = normalisePath(method);
        if (b.isEmpty()) return m.isEmpty() ? "/" : m;
        if (m.isEmpty()) return b;
        return b.endsWith("/") ? b + m.substring(1) : b + m;
    }

    private String normalisePath(String path) {
        if (path == null || path.isBlank()) return "";
        path = path.trim();
        if (!path.startsWith("/")) path = "/" + path;
        return path;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) if (v != null) return v;
        return null;
    }

    // -------------------------------------------------------------------------

    private String deriveServiceName(String className, Path appFile, Path workspaceRoot) {
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
