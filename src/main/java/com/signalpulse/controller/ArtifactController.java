package com.signalpulse.controller;

import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/v1/artifacts")
@CrossOrigin(origins = "*")
public class ArtifactController {

    @GetMapping("/{name}")
    public String getArtifact(@PathVariable String name) throws IOException {
        String path = name.endsWith(".log") || name.endsWith(".txt") ? name : name + ".txt";
        java.nio.file.Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            return "File " + name + " not found.";
        }

        try {
            // Read last 500 lines for efficiency if it's a log file
            if (name.endsWith(".log")) {
                java.util.List<String> allLines = Files.readAllLines(filePath);
                int start = Math.max(0, allLines.size() - 500);
                return String.join("\n", allLines.subList(start, allLines.size()));
            }
            return Files.readString(filePath);
        } catch (Exception e) {
            return "Error reading " + name + ": " + e.getMessage();
        }
    }
}
