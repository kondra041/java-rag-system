package org.example;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EmbeddingService {

    private static final String OLLAMA_URL = "http://localhost:11434/api/embeddings";
    private static final String MODEL = "nomic-embed-text";

    private static final ObjectMapper mapper = new ObjectMapper();

    public static float[] getEmbedding(String code) throws Exception {

        String payload = String.format("{\"model\": \"%s\", \"prompt\": %s}", MODEL, mapper.writeValueAsString(code));

        URL url = new URL(OLLAMA_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        // Отправляем запрос
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes());
            os.flush();
        }

        // Читаем ответ
        StringBuilder response = new StringBuilder();
        try (Scanner scanner = new Scanner(conn.getInputStream())) {
            while (scanner.hasNextLine()) {
                response.append(scanner.nextLine());
            }
        }

        // Парсим JSON-ответ
        JsonNode rootNode = mapper.readTree(response.toString());
        JsonNode embeddingNode = rootNode.get("embedding");

        // Проверка
        if (embeddingNode == null || !embeddingNode.isArray()) {
            throw new RuntimeException("Ошибка: Эмбеддинг не найден в ответе.");
        }

        // Преобразуем JsonNode в массив float[]
        float[] embedding = new float[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            embedding[i] = embeddingNode.get(i).floatValue();
        }

        // Проверка: размер вектора должен быть 768
        if (embedding.length != 768) {
            throw new RuntimeException("Ошибка: размер эмбеддинга != 768, а = " + embedding.length);
        }

        conn.disconnect();

        return embedding;
    }

}
