package com.algolens.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Languages AlgoLens knows about. {@code supported} marks the ones that actually have a
 * {@code CodeExecutor} registered today; the rest exist so the API contract, database rows
 * and frontend language picker do not need to change when a new executor lands.
 */
public enum Language {

    JAVA("Java", "java", true),
    PYTHON("Python", "py", false),
    CPP("C++", "cpp", false),
    JAVASCRIPT("JavaScript", "js", false);

    private final String displayName;
    private final String fileExtension;
    private final boolean supported;

    Language(String displayName, String fileExtension, boolean supported) {
        this.displayName = displayName;
        this.fileExtension = fileExtension;
        this.supported = supported;
    }

    public String displayName() {
        return displayName;
    }

    public String fileExtension() {
        return fileExtension;
    }

    public boolean supported() {
        return supported;
    }

    public static Optional<Language> fromString(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalised = raw.trim().toUpperCase();
        if ("C++".equals(raw.trim())) {
            normalised = "CPP";
        }
        String target = normalised;
        return Arrays.stream(values()).filter(l -> l.name().equals(target)).findFirst();
    }
}
