package com.signalpulse.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/artifacts")
public class ArtifactController {

    private static final Set<String> ALLOWED_ARTIFACTS = Set.of(
            "signalPulse.log"
    );

    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final Path artifactRoot;

    public ArtifactController() {
        String root = System.getProperty("signalpulse.artifact.root",
                System.getenv().getOrDefault("SIGNALPULSE_ARTIFACT_ROOT", "."));
        this.artifactRoot = Paths.get(root).toAbsolutePath().normalize();
    }

    @GetMapping("/{name}")
    @PreAuthorize("hasAuthority('OP_READ_ALL')")
    public String getArtifact(@PathVariable String name) throws IOException {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid artifact name");
        }
        if (!ALLOWED_ARTIFACTS.contains(name)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Artifact not allowed");
        }

        Path filePath = artifactRoot.resolve(name).normalize();
        if (!filePath.startsWith(artifactRoot) || !Files.exists(filePath)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Artifact not found");
        }

        if (name.endsWith(".log")) {
            java.util.List<String> allLines = Files.readAllLines(filePath);
            int start = Math.max(0, allLines.size() - 500);
            return String.join("\n", allLines.subList(start, allLines.size()));
        }
        return Files.readString(filePath);
    }
}
