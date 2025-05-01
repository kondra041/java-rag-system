package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
    private static final Properties properties = new Properties();

    static {
        try (InputStream input = ConfigLoader.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new RuntimeException("Config file not found!");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    public static String getProjectDir() {
        return properties.getProperty("project.dir");
    }

    public static String getOllamaUrl() {
        return properties.getProperty("ollama.url");
    }

    public static String getOllamaModelName() {
        return properties.getProperty("ollama.model.name");
    }

    public static String getMilvusEndpoint() {
        return properties.getProperty("milvus.endpoint");
    }

    public static String getMilvusUsername() {
        return properties.getProperty("milvus.username");
    }

    public static String getMilvusPassword() {
        return properties.getProperty("milvus.password");
    }
}