package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.response.InsertResp;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.example.MilvusV2.CODE_TESTS_COLLECTION;
import static org.example.MilvusV2.PROJECT_KNOWLEDGE_COLLECTION;

public class RAGService {

    private static MilvusClientV2 getMilvusClient() {
        return MilvusV2.getClient();
    }

    private static void saveToMilvus(String collectionName, List<JsonObject> data) {
        try {
            InsertReq insertReq = InsertReq.builder()
                    .collectionName(collectionName)
                    .data(data)
                    .build();

            getMilvusClient().insert(insertReq);
            System.out.println("Запись сохранена в коллекцию " + collectionName);
        } catch (Exception e) {
            System.err.println("Ошибка при сохранении в Milvus: " + e.getMessage());
        }
    }

    public static void saveTestToMilvus(String sourceFileName, String testFilePath, String generatedTestCode) {
        List<JsonObject> data = new ArrayList<>();
        float[] embeddingVector;

        try {
            embeddingVector = EmbeddingService.getEmbedding(generatedTestCode);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка получения эмбеддинга для теста", e);
        }

        // Создаем Gson для преобразования в JSON
        Gson gson = new Gson();

        JsonObject record = new JsonObject();
        record.addProperty("source_file", sourceFileName);
        record.addProperty("test_path", testFilePath);
        record.addProperty("test_code", generatedTestCode);
        record.addProperty("status", "generated");
        record.add("emb_code", gson.toJsonTree(embeddingVector));

        data.add(record);

        saveToMilvus(CODE_TESTS_COLLECTION, data);
    }

    public static void saveFactToMilvus(String filePath, String packageName, String className,
                                        String methodsJson, String dependsOnJson, String lastCommit, float[] codeEmbed) {
        try {
            Gson gson = new Gson();

            // Генерация хеша для file_id
            String fileName = Paths.get(filePath).getFileName().toString();
            String fileId = Integer.toHexString(fileName.hashCode());


            List<JsonObject> data = new ArrayList<>();


            JsonObject record = new JsonObject();
            record.addProperty("file_id", fileId);
            record.add("code_embed", gson.toJsonTree(codeEmbed));
            record.addProperty("file_path", filePath);
            record.addProperty("package", packageName);
            record.addProperty("class_name", className);
            record.addProperty("methods", methodsJson);
            record.addProperty("depends_on", dependsOnJson);
            record.addProperty("last_commit", lastCommit);


            data.add(record);


            InsertReq insertReq = InsertReq.builder()
                    .collectionName(PROJECT_KNOWLEDGE_COLLECTION)
                    .data(data)
                    .build();

            InsertResp insertResp = getMilvusClient().insert(insertReq);


            System.out.println(insertResp);
        } catch (Exception e) {
            System.err.println("Ошибка при сохранении в Milvus: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
