package org.example;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

import static org.example.ConfigLoader.getOllamaUrl;

public class OllamaService {

    private static final String OLLAMA_URL = getOllamaUrl() + "/api/generate";

    public static String sendPrompt(String prompt) throws IOException {
        URL url = URI.create(OLLAMA_URL).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        // Экранируем все спецсимволы
        String escapedPrompt = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");

        // Формирование JSON строки для запроса
        String jsonInput = String.format("""
            {
                "model": "codestral",
                "prompt": "%s",
                "stream": false
            }
            """, escapedPrompt);

        System.out.println("Отправляем в Ollama:");
        System.out.println(jsonInput);

        // Отправка запроса
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInput.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int status = connection.getResponseCode();
        if (status != 200) {
            try (InputStream err = connection.getErrorStream()) {
                if (err != null) {
                    System.err.println(new String(err.readAllBytes()));
                }
            }
            throw new IOException("Ошибка Ollama: HTTP " + status);
        }

        // Чтение ответа от сервера
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line.trim());
            }
            System.out.println("Ответ от Ollama:");
            System.out.println(response.toString());

            JSONObject jsonResponse = new JSONObject(response.toString());
            return jsonResponse.getString("response");
        }
    }
}
