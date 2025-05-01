package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class TestFileService {

    private static final Pattern CODE_BLOCK = Pattern.compile(
            "(?s)```java\\s*(.*?)\\s*```"
    );

    public static Path getTestPath(Path controllerPath, Path projectDir) {
        // Преобразуем путь контроллера в строку с нормализованными слешами
        String fullPath = controllerPath.toString().replace("\\", "/");

        // Находим индекс папки в пути контроллера
        int srcIndex = fullPath.indexOf("src/main/java/");
        if (srcIndex == -1) {
            throw new IllegalArgumentException("Контроллер не находится в папке src/main/java");
        }

        // Обрезаем всё до src/main/java/
        String relative = fullPath.substring(srcIndex + "src/main/java/".length())
                .replace(".java", "Tests.java");

        // Формируем путь для теста внутри папки проекта API
        return projectDir.resolve("src/test/java").resolve(relative);
    }

    // Сохранение теста в файл
    public static void saveTest(Path testPath, String generatedTest) throws IOException {
        // Сначала вытаскиваем чистый Java-код
        System.out.println("generatedTest: " + generatedTest);
        String code = extractJavaCode(generatedTest);

        Files.createDirectories(testPath.getParent());
        Files.writeString(testPath, code);
        System.out.println("Тест сохранён в файл: " + testPath.toAbsolutePath());
    }

    // Чтение исходного кода контроллера и его очистка от табуляций
    public static String readSourceCode(Path path) throws IOException {
        String code = Files.readString(path);
        return cleanCode(code);
    }

    // Очистка от ненужных символов
    public static String cleanCode(String code) {
        return code.replace("\t", "    "); // Заменяем табуляции на пробелы
    }

    private static String extractJavaCode(String response) {
        StringBuilder sb = new StringBuilder();
        boolean insideCodeBlock = false;

        for (String line : response.split("\n")) {
            line = line.trim();

            // Пропускаем пустые строки
            if (line.isEmpty()) {
                continue;
            }

            // Ищем начало блока с кодом
            if (line.equals("```java")) {
                insideCodeBlock = true;
                continue; // пропускаем начало блока
            }

            // Ищем конец блока с кодом
            if (line.equals("```")) {
                insideCodeBlock = false;
                continue; // пропускаем конец блока
            }

            // Добавляем строки в код, если находимся внутри блока
            if (insideCodeBlock) {
                sb.append(line).append("\n");
            }
        }

        return sb.toString().trim();
    }



}
