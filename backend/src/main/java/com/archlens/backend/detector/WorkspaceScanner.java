package com.archlens.backend.detector;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class WorkspaceScanner {

    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git",
            "node_modules",
            "target",
            "build",
            ".gradle",
            "vendor",
            "__pycache__",
            ".venv",
            "venv",
            ".idea",
            ".vscode",
            ".archlens"
    );

    private WorkspaceScanner() {}

    /**
     * Finds files in the workspace matching the specified predicate, searching up to maxDepth.
     */
    public static List<Path> findFiles(Path workspaceRoot, int maxDepth, Predicate<Path> matcher) {
        List<Path> matchingFiles = new ArrayList<>();
        if (workspaceRoot == null || !Files.exists(workspaceRoot)) {
            return matchingFiles;
        }

        try {
            Files.walkFileTree(workspaceRoot, Set.of(), maxDepth, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (!dir.equals(workspaceRoot) && IGNORED_DIRECTORIES.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && matcher.test(file)) {
                        matchingFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Ignore visit errors during scanning
        }

        return matchingFiles;
    }

    /**
     * Finds files matching an exact filename (case-insensitive or exact).
     */
    public static List<Path> findByFileName(Path workspaceRoot, String filename, int maxDepth) {
        return findFiles(workspaceRoot, maxDepth, path -> {
            Path file = path.getFileName();
            return file != null && file.toString().equalsIgnoreCase(filename);
        });
    }

    /**
     * Returns a normalized relative path from workspace root using forward slashes.
     */
    public static String getRelativePath(Path workspaceRoot, Path file) {
        if (workspaceRoot == null || file == null) {
            return "";
        }
        try {
            return workspaceRoot.relativize(file).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return file.getFileName().toString();
        }
    }
}
