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
        // In a real app, this would be a specific log directory
        try {
            return Files.readString(Paths.get(path));
        } catch (Exception e) {
            return "File " + name + " not found.";
        }
    }
}
