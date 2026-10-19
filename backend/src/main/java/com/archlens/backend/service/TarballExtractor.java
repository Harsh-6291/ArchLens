package com.archlens.backend.service;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class TarballExtractor {

    private static final Logger log = LoggerFactory.getLogger(TarballExtractor.class);

    public void extractTarGz(byte[] tarballBytes, Path destinationDir) throws IOException {
        Files.createDirectories(destinationDir);
        Path normalizedDest = destinationDir.toAbsolutePath().normalize();

        try (ByteArrayInputStream bais = new ByteArrayInputStream(tarballBytes);
             GzipCompressorInputStream gzis = new GzipCompressorInputStream(bais);
             TarArchiveInputStream tais = new TarArchiveInputStream(gzis)) {

            TarArchiveEntry entry;
            String rootPrefix = null;

            while ((entry = tais.getNextTarEntry()) != null) {
                String entryName = entry.getName();

                // GitHub tarballs pack contents inside a root directory (e.g., owner-repo-sha/)
                if (rootPrefix == null) {
                    int slashIdx = entryName.indexOf('/');
                    if (slashIdx != -1) {
                        rootPrefix = entryName.substring(0, slashIdx + 1);
                    }
                }

                String relativePath = (rootPrefix != null && entryName.startsWith(rootPrefix))
                        ? entryName.substring(rootPrefix.length())
                        : entryName;

                if (relativePath.isBlank() || relativePath.equals(".")) {
                    continue;
                }

                Path targetPath = normalizedDest.resolve(relativePath).normalize();

                // Guard against ZipSlip / directory traversal vulnerabilities
                if (!targetPath.startsWith(normalizedDest)) {
                    throw new IOException("Malicious tar entry detected outside target directory: " + entryName);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    if (targetPath.getParent() != null) {
                        Files.createDirectories(targetPath.getParent());
                    }
                    Files.copy(tais, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        log.info("Successfully extracted tarball into {}", normalizedDest);
    }
}
