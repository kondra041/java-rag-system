package org.example;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalTime;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.time.Duration;
import java.util.Properties;

import static org.example.FactExtractor.*;
import static org.example.MilvusV2.*;


public class Main {
    public static void main(String[] args) throws Exception {
        try {
            // Путь к папке с проектом API
            Path projectDir = Paths.get(ConfigLoader.getProjectDir());
            checkConnection();

            // Проверяем, существует ли коллекция в Milvus
            if (!MilvusV2.hasCollection()) {
                System.out.println("Коллекции не найдены");
                return;
            }

            if(getRowCount(PROJECT_KNOWLEDGE_COLLECTION) == 0) {
                System.out.println("Парсим проект и сохраняем данные...");
                parseProject(Paths.get(ConfigLoader.getProjectDir() + "/src/main/").toFile());
                System.out.println("Данные о проекте сохранены.");
            }

            List<Path> modifiedControllers = GitService.getModifiedJavaControllers(projectDir);

            if (modifiedControllers.isEmpty()) {
                System.out.println("Нет изменений в контроллерах.");
                return;
            }

            System.out.println("Измененные контроллеры:");
            modifiedControllers.forEach(System.out::println);
            LocalTime startTime = LocalTime.now();
            System.out.println("Тест запущен в " + startTime);
            for (Path path : modifiedControllers) {
                System.out.println("Генерация тестов для: " + path);
                FactExtractor.parseAndSaveFacts(path.toFile(), "");
                Path testPath = TestFileService.getTestPath(path, projectDir);
                System.out.println("Путь теста: " + testPath.toAbsolutePath());

                String controllerCode = TestFileService.readSourceCode(path);
                String className = path.getFileName().toString().replace(".java", "");
                String prompt = PromptBuilder.buildPromptWithContext(controllerCode, className);

                String generatedTest = OllamaService.sendPrompt(prompt);

                TestFileService.saveTest(testPath, generatedTest);
                RAGService.saveTestToMilvus(
                        path.getFileName().toString(),         // sourceFileName, "OwnerController.java"
                        testPath.toString(),                   // testFilePath, путь к файлу теста
                        generatedTest                          // сгенерированный код теста
                );
                LocalTime endTime = LocalTime.now();
                System.out.println("Тест создан в " + endTime);
                Duration duration = Duration.between(startTime, endTime);
        
        // Выводим разницу в разных форматах
        System.out.println("Время выполнения:" + duration.toMinutesPart() + "м " + duration.toSecondsPart() + "с");

                
            }
        } finally {
            MilvusV2.closeClient();
        }
    }
}
