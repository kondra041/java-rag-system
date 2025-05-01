package org.example;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class GitService {

    public static List<Path> getModifiedJavaControllers(Path projectDir) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("git", "diff", "--name-only", "HEAD")
                .directory(projectDir.toFile())  // <--  папка проекта!
                .start();

        List<String> modifiedFiles;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            modifiedFiles = reader.lines().toList();
        }

        process.waitFor();

        return modifiedFiles.stream()
                .filter(f -> f.startsWith("src/main/java/") && f.endsWith("Controller.java"))
                .map(projectDir::resolve) // <-- путь абсолютный
                .collect(Collectors.toList());
    }
}
