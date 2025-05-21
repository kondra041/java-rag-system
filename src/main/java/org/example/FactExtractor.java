package org.example;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.google.gson.JsonElement;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.milvus.common.utils.JsonUtils.toJson;
import static org.example.MilvusV2.PROJECT_KNOWLEDGE_COLLECTION;
import static org.example.MilvusV2.getClient;
import static org.example.TestFileService.cleanCode;

import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.expr.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;


public class FactExtractor {

    public static void parseAndSaveFacts(File file, String lastCommit) throws Exception {
        JavaParser javaParser = new JavaParser();
        CompilationUnit cu = javaParser.parse(file).getResult()
                .orElseThrow(() -> new RuntimeException("Не удалось распарсить файл: " + file.getName()));

        String filePath = file.getAbsolutePath();
        Path projectSrcPath = Paths.get(ConfigLoader.getProjectDir() + "/src/main/java"); // ← путь к корню проекта с исходниками

        // Получаем пакет
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getName().toString())
                .orElse("unknown");

        // Получаем текст файла
        String codeFileContent = Files.readString(file.toPath());

        // Список импортов
        Set<String> externalImports = cu.getImports().stream()
                .map(imp -> imp.getName().getIdentifier())
                .collect(Collectors.toSet());

        Set<String> javaLangTypes = Set.of(
                "String", "Integer", "Boolean", "Long", "Double", "Float", "Character", "Byte", "Short", "Void", "Object"
        );

        Set<String> primitiveTypes = Set.of(
                "int", "boolean", "char", "byte", "short", "long", "float", "double"
        );

        // Обрабатываем каждый класс в файле
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            try {
                String className = cls.getNameAsString();

                // Методы
                List<Map<String, String>> methodNames = cls.getMethods().stream()
                        .map(method -> {
                            Map<String, String> methodDetails = new HashMap<>();
                            methodDetails.put("name", method.getNameAsString());
                            methodDetails.put("params", method.getParameters().stream()
                                    .map(param -> param.getType().toString() + " " + param.getNameAsString())
                                    .collect(Collectors.joining(", ")));
                            methodDetails.put("return_type", method.getType().toString());
                            return methodDetails;
                        })
                        .collect(Collectors.toList());

                // Собираем ВСЕ использованные типы
                Set<String> allTypesUsed = new HashSet<>();
                cls.walk(Node.TreeTraversal.PREORDER, node -> {
                    if (node instanceof Type) {
                        allTypesUsed.add(((Type) node).asString());
                    } else if (node instanceof ObjectCreationExpr) {
                        allTypesUsed.add(((ObjectCreationExpr) node).getType().asString());
                    } else if (node instanceof ClassOrInterfaceType) {
                        allTypesUsed.add(((ClassOrInterfaceType) node).getNameAsString());
                    }
                });

                // Фильтруем только зависимости, определённые в проекте
                Set<String> dependencies = allTypesUsed.stream()
                        .filter(type -> !externalImports.contains(type))
                        .filter(type -> !javaLangTypes.contains(type))
                        .filter(type -> !primitiveTypes.contains(type))
                        .filter(type -> classExistsInProject(type, projectSrcPath))
                        .collect(Collectors.toSet());

                float[] codeEmbed = EmbeddingService.getEmbedding(codeFileContent);

                String fact = FactToLog(className, packageName, methodNames, new ArrayList<>(dependencies), codeEmbed);

                System.out.println("Generated Fact:");
                System.out.println(fact);

                RAGService.saveFactToMilvus(filePath, packageName, className,
                        toJson(methodNames), toJson(new ArrayList<>(dependencies)), lastCommit, codeEmbed);

            } catch (Exception e) {
                System.err.println("Ошибка парсинга класса в файле: " + file.getName() + ", ошибка: " + e.getMessage());
            }
        });
    }

    private static boolean classExistsInProject(String className, Path projectSrcPath) {
        try (Stream<Path> paths = Files.walk(projectSrcPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .anyMatch(p -> p.getFileName().toString().equals(className + ".java"));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }


    public static String FactToLog(String className, String packageName, List<Map<String, String>> methods, List<String> dependencies, float[] codeEmbed) {
        String methodsStr = methods.stream()
                .map(method -> String.format("{name: %s, params: %s, return_type: %s}",
                        method.get("name"), method.get("params"), method.get("return_type")))
                .collect(Collectors.joining(", "));

        String dependenciesStr = String.join(", ", dependencies);


        return String.format("""
            Class: %s
            Package: %s
            Methods: [%s]
            DependsOn: [%s]
            CodeEmbed size: %d
            """, className, packageName, methodsStr, dependenciesStr, codeEmbed.length);
    }

    public static void parseProject(File projectRoot) throws IOException {
        Files.walk(projectRoot.toPath())
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        parseAndSaveFacts(path.toFile(), "");
                    } catch (Exception e) {
                        System.err.println("Ошибка обработки файла: " + path + ", ошибка: " + e.getMessage());
                    }
                });
    }

    public static String getDependencies(String className) throws Exception {

        QueryReq queryReq = QueryReq.builder()
                .collectionName(MilvusV2.PROJECT_KNOWLEDGE_COLLECTION)
                .filter("class_name == \"" + className + "\"")
                .outputFields(Arrays.asList("class_name", "code_embed", "depends_on", "file_path"))
                .limit(1)
                .build();

        QueryResp getResp = getClient().query(queryReq);

        List<QueryResp.QueryResult> results = getResp.getQueryResults();

        if (results.isEmpty()) {
            throw new RuntimeException("Класс " + className + " не найден в Milvus");
        }

        QueryResp.QueryResult classData = results.get(0);

        Map<String, Object> entity = classData.getEntity();

        // Извлекаем зависимость и проверяем тип
        Object dependsOnRaw = entity.get("depends_on");

        List<String> dependencies = null;

        if (dependsOnRaw instanceof String) {
            // Если это строка, парсим как JSON
            ObjectMapper objectMapper = new ObjectMapper();
            dependencies = objectMapper.readValue((String) dependsOnRaw, List.class);
        } else if (dependsOnRaw instanceof JsonElement) {

            JsonElement dependsOnJson = (JsonElement) dependsOnRaw;
            if (dependsOnJson.isJsonPrimitive() && dependsOnJson.getAsJsonPrimitive().isString()) {
                String dependsOnStr = dependsOnJson.getAsString();
                ObjectMapper objectMapper = new ObjectMapper();
                dependencies = objectMapper.readValue(dependsOnStr, List.class);
            } else {
                throw new RuntimeException("Невозможно обработать поле depends_on");
            }
        } else {
            throw new RuntimeException("Некорректный формат данных в поле depends_on");
        }

        System.out.println("Зависимости: " + dependencies);

        String fullCode = getRelevantString(dependencies);

        return fullCode;
    }

    public static String getRelevantString(List<String> dependencies) throws Exception {
        StringBuilder relevantCode = new StringBuilder();

        for (String dependency : dependencies) {
            // Запрос к Milvus для получения записи по зависимости
            QueryReq queryReq = QueryReq.builder()
                    .collectionName(MilvusV2.PROJECT_KNOWLEDGE_COLLECTION)
                    .filter("class_name == \"" + dependency + "\"")
                    .outputFields(Arrays.asList("file_path"))
                    .limit(1)
                    .build();

            QueryResp getResp = getClient().query(queryReq);
            List<QueryResp.QueryResult> results = getResp.getQueryResults();

            if (results.isEmpty()) {
                System.out.println("Зависимость " + dependency + " не найдена в Milvus");
                continue;
            }

            // Извлекаем file_path из результатов
            QueryResp.QueryResult classData = results.get(0);
            Map<String, Object> entity = classData.getEntity();
            String filePath = (String) entity.get("file_path");

            if (filePath != null) {
                // Чтение содержимого файла

                String fileContent = readFileContent(filePath);

                // Добавление содержимого файла в строку с обёрткой в блок ```java ```
                relevantCode.append("Code of ").append(dependency).append(":\n")
                        .append("```java\n")
                        .append(cleanCode(fileContent))
                        .append("\n```\n\n");
            } else {
                System.out.println("File path для зависимости " + dependency + " не найден.");
            }

        }
        return relevantCode.toString();
    }

    private static String readFileContent(String filePath) throws IOException {
        // Чтение содержимого файла по пути
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    public static List<String> searchRelevantKnowledge(String text, int topK) throws Exception {

        float[] queryEmbedding = EmbeddingService.getEmbedding(text);

        FloatVec queryVector = new FloatVec(queryEmbedding);
        // Создаём запрос на поиск
        SearchReq searchReq = SearchReq.builder()
                .collectionName(PROJECT_KNOWLEDGE_COLLECTION)
                .data(Collections.singletonList(queryVector))
                .outputFields(Arrays.asList("class_name"))
                .topK(topK)
                .build();
        SearchResp searchResp = getClient().search(searchReq);

        List<List<SearchResp.SearchResult>> searchRe = searchResp.getSearchResults();
        for (List<SearchResp.SearchResult> results : searchRe) {
            System.out.println("TopK results:");
            for (SearchResp.SearchResult result : results) {
                System.out.println(result.getEntity().get("class_name"));
            }
        }

        List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();

        // Собираем информацию по результатам
        List<String> knowledgeTexts = new ArrayList<>();
        for (List<SearchResp.SearchResult> resultList : searchResults) {
            for (SearchResp.SearchResult result : resultList) {
                StringBuilder knowledge = new StringBuilder();

                Object packageName = result.getEntity().get("package");
                Object className = result.getEntity().get("class_name");
                Object methods = result.getEntity().get("methods");
                Object dependsOn = result.getEntity().get("depends_on");

                if (packageName != null) {
                    knowledge.append("Package: ").append(packageName.toString()).append(". ");
                }
                if (className != null) {
                    knowledge.append("Class: ").append(className.toString()).append(". ");
                }
                if (methods != null) {
                    knowledge.append("Methods: ").append(methods.toString()).append(". ");
                }
                if (dependsOn != null) {
                    knowledge.append("Depends on: ").append(dependsOn.toString()).append(". ");
                }

                if (!knowledge.isEmpty()) {
                    knowledgeTexts.add(knowledge.toString());
                }
            }
        }

        // Проверяем, есть ли релевантные данные
        if (knowledgeTexts.isEmpty()) {
            System.out.println("Нет релевантных данных.");
        } else {
            System.out.println("Есть релевантные данные:");
            knowledgeTexts.forEach(System.out::println);
        }

        return knowledgeTexts;
    }



}

