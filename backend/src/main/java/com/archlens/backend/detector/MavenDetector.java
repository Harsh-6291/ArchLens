package com.archlens.backend.detector;

import com.archlens.backend.detector.model.DetectedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Component
public class MavenDetector implements ManifestDetector {

    private static final Logger log = LoggerFactory.getLogger(MavenDetector.class);

    @Override
    public String getName() {
        return "maven-detector";
    }

    @Override
    public boolean canDetect(Path workspaceRoot) {
        return !WorkspaceScanner.findByFileName(workspaceRoot, "pom.xml", 3).isEmpty();
    }

    @Override
    public List<DetectedNode> detect(Path workspaceRoot) {
        List<DetectedNode> nodes = new ArrayList<>();
        List<Path> pomFiles = WorkspaceScanner.findByFileName(workspaceRoot, "pom.xml", 3);

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(false);
        try {
            // Disable DTD / external entities to prevent XXE
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignored) {}

        for (Path pomPath : pomFiles) {
            try (InputStream is = Files.newInputStream(pomPath)) {
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(is);
                doc.getDocumentElement().normalize();

                Element root = doc.getDocumentElement();
                String artifactId = getDirectChildText(root, "artifactId");
                String groupId = getDirectChildText(root, "groupId");
                String packaging = getDirectChildText(root, "packaging");

                if (groupId == null || groupId.isBlank()) {
                    NodeList parentList = root.getElementsByTagName("parent");
                    if (parentList.getLength() > 0 && parentList.item(0) instanceof Element parentEl) {
                        groupId = getDirectChildText(parentEl, "groupId");
                    }
                }

                String name = (artifactId != null && !artifactId.isBlank()) ? artifactId : null;
                if (name == null) {
                    Path parent = pomPath.getParent();
                    name = (parent != null && parent.getFileName() != null)
                            ? parent.getFileName().toString()
                            : "maven-service";
                }

                String type = "SERVICE";
                String technology = "Java (Maven)";
                double confidence = 0.40;
                String relativePath = WorkspaceScanner.getRelativePath(workspaceRoot, pomPath);
                String id = "service:" + name;

                Map<String, Object> metadata = new HashMap<>();
                if (groupId != null && !groupId.isBlank()) metadata.put("groupId", groupId);
                if (artifactId != null && !artifactId.isBlank()) metadata.put("artifactId", artifactId);
                if (packaging != null && !packaging.isBlank()) metadata.put("packaging", packaging);
                metadata.put("manifest", "pom.xml");

                nodes.add(new DetectedNode(id, name, type, technology, confidence, relativePath, metadata));

            } catch (Exception e) {
                log.warn("Failed to parse pom.xml at {}: {}", pomPath, e.getMessage());
            }
        }

        return nodes;
    }

    private String getDirectChildText(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el && tagName.equals(el.getTagName())) {
                return el.getTextContent().trim();
            }
        }
        return null;
    }
}
