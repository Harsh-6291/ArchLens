package com.archlens.backend.dto;

/**
 * M1.13 — Patch request body for manually editing a graph node.
 * All fields are optional; null means "do not change".
 */
public record PatchNodeRequest(
        String name,
        String type,
        String technology,
        String sourceFile
) {}
