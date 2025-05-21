package org.example;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.GetReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.GetResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.collection.response.GetCollectionStatsResp;

import io.milvus.v2.service.collection.request.CreateCollectionReq.FieldSchema;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.common.DataType;


import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.example.ConfigLoader.getMilvusEndpoint;
import static org.example.ConfigLoader.getMilvusUsername;
import static org.example.ConfigLoader.getMilvusPassword;

public class MilvusV2 {
    private static final String CLUSTER_ENDPOINT = getMilvusEndpoint();
    private static final String USERNAME = getMilvusUsername();
    private static final String PASSWORD = getMilvusPassword();
    private static final String TOKEN = USERNAME + ":" + PASSWORD;

    public static final String CODE_TESTS_COLLECTION = "test";
    public static final String PROJECT_KNOWLEDGE_COLLECTION = "knowledge_project";

    private static final MilvusClientV2 client;

    static {
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri(CLUSTER_ENDPOINT)
                .username(USERNAME)
                .password(PASSWORD)
               // .token(TOKEN)
                .connectTimeoutMs(30_000)
                .build();
        client = new MilvusClientV2(connectConfig);
    }

    public static MilvusClientV2 getClient() {
        return client;
    }

    public static void closeClient() {
        if (client != null) {
            client.close();
            System.out.println("Milvus client закрыт.");
        }
    }

    public static void checkConnection() {
        try {
            // Попытка получить информацию о коллекции
            HasCollectionReq hasCollectionReq = HasCollectionReq.builder()
                    .collectionName(PROJECT_KNOWLEDGE_COLLECTION)
                    .build();

            boolean exists = client.hasCollection(hasCollectionReq);
            if (exists) {
                System.out.println("Соединение с Milvus установлено успешно.");
            } else {
                System.out.println("Коллекция " + PROJECT_KNOWLEDGE_COLLECTION + " не существует.");
            }
        } catch (Exception e) {
            System.err.println("Ошибка подключения к Milvus: " + e.getMessage());
        }
    }

    public static void loadCollection(String collectionName) throws Exception {
        LoadCollectionReq loadCollectionReq = LoadCollectionReq.builder()
                .collectionName(collectionName)
                .build();
        client.loadCollection(loadCollectionReq);
        System.out.println("Коллекция " + collectionName + " загружена в память.");
    }

    public static void flush(String collectionName) {
        FlushReq flushReq = FlushReq.builder()
                .collectionNames(Collections.singletonList(collectionName))
                .build();
        client.flush(flushReq);
        System.out.println("Коллекция " + collectionName + " была сброшена (flush).");
    }

    public static void getData(String collectionName, List<String> fields)  throws Exception {
        loadCollection(collectionName);
        GetReq getReq = GetReq.builder()
                .collectionName(collectionName)
                .ids(List.of(0))
                .outputFields(Arrays.asList(String.valueOf(fields)))
                .build();
        GetResp getResp = client.get(getReq);

        List<QueryResp.QueryResult> results = getResp.getGetResults();
        for (QueryResp.QueryResult result : results) {
            System.out.println("ОК" + result.getEntity());
        }
    }

    public static Boolean hasCollection() {
        HasCollectionReq hasCollectionReq1 = HasCollectionReq.builder()
                .collectionName(CODE_TESTS_COLLECTION)
                .build();
        HasCollectionReq hasCollectionReq2 = HasCollectionReq.builder()
                .collectionName(PROJECT_KNOWLEDGE_COLLECTION)
                .build();
        return client.hasCollection(hasCollectionReq1) && client.hasCollection(hasCollectionReq2);
    }

    public static Boolean hasCollectionByName(String name) {
        HasCollectionReq hasCollectionReq = HasCollectionReq.builder()
                .collectionName(name)
                .build();

        return client.hasCollection(hasCollectionReq);
    }

    public static long getRowCount(String collectionName) {
        try {

            if (!hasCollectionByName(collectionName)) {
                System.err.println("Коллекция " + collectionName + " не существует");
                return 0;
            }

            // Загружаем коллекцию в память
            loadCollection(collectionName);

            // Синхронизируем данные
            flush(collectionName);

            GetCollectionStatsReq req = GetCollectionStatsReq.builder()
                    .collectionName(collectionName)
                    .build();

            GetCollectionStatsResp resp = client.getCollectionStats(req);
            return resp.getNumOfEntities();
        } catch (Exception e) {
            System.err.println("Ошибка при получении количества записей: " + e.getMessage());
            return -1;
        }
    }

    public static void deleteCollectionByName(String name) {
        client.dropCollection(
                DropCollectionReq.builder()
                        .collectionName(name)
                        .build()
        );
    }

    public static void createTestsCollection() {
        try {
            // Создаем схему коллекции
            CreateCollectionReq.CollectionSchema collectionSchema = client.createSchema();

            // Добавляем поля согласно схеме из скриншота
            collectionSchema.addField(AddFieldReq.builder()
                    .fieldName("Auto_id")
                    .dataType(DataType.Int64)
                    .isPrimaryKey(true)
                    .autoID(true)
                    .description("The Primary Key")
                    .build());

            collectionSchema.addField(AddFieldReq.builder()
                    .fieldName("emb_code")
                    .dataType(DataType.FloatVector)
                    .dimension(768)
                    .build());

            collectionSchema.addField(AddFieldReq.builder()
                    .fieldName("source_file")
                    .dataType(DataType.VarChar)
                    .maxLength(128)
                    .build());

            collectionSchema.addField(AddFieldReq.builder()
                    .fieldName("test_path")
                    .dataType(DataType.VarChar)
                    .maxLength(256)
                    .build());

            collectionSchema.addField(AddFieldReq.builder()
                    .fieldName("test_code")
                    .dataType(DataType.VarChar)
                    .maxLength(8192)
                    .build());

            collectionSchema.addField(AddFieldReq.builder()
                    .fieldName("status")
                    .dataType(DataType.VarChar)
                    .maxLength(24)
                    .isNullable(true)
                    .build());

            // Параметры индекса для векторного поля
            IndexParam indexParam = IndexParam.builder()
                    .fieldName("emb_code")  // Индексируем векторное поле
                    .metricType(IndexParam.MetricType.COSINE)  // Косинусное расстояние
                    .build();

            // Создаем коллекцию
            CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                    .collectionName(CODE_TESTS_COLLECTION)
                    .collectionSchema(collectionSchema)
                    .indexParams(Collections.singletonList(indexParam))
                    .build();

            client.createCollection(createCollectionReq);
            System.out.println("Коллекция " + CODE_TESTS_COLLECTION + " успешно создана");
        } catch (Exception e) {
            System.err.println("Ошибка при создании коллекции " + CODE_TESTS_COLLECTION + ": " + e.getMessage());
        }
    }

    public static void createCodeCollection() {
        try {
            // Создаем схему коллекции
            CreateCollectionReq.CollectionSchema collectionSchema = client.createSchema();

            // Добавляем поля согласно схеме
            collectionSchema.addField(AddFieldReq.builder()
                    .fieldName("file_id")
                    .dataType(DataType.VarChar)
                    .maxLength(64)
                    .isPrimaryKey(true)
                    .description("The Primary Key")
                    .build());

            collectionSchema.addField(AddFieldReq.builder()
                    .fieldName("code_embed")
                    .dataType(DataType.FloatVector)
                    .dimension(768)  // Размерность вектора
                    .build());

            collectionSchema.addField(AddFieldReq.builder()
                    .fieldName("file_path")
                    .dataType(DataType.VarChar)
                    .maxLength(512)
                    .build());

            collectionSchema.addField(AddFieldReq.builder()
                    .fieldName("package")
                    .dataType(DataType.VarChar)
                    .maxLength(512)
                    .build());

            collectionSchema.addField(AddFieldReq.builder()
                    .fieldName("class_name")
                    .dataType(DataType.VarChar)
                    .maxLength(128)
                    .build());

            collectionSchema.addField(AddFieldReq.builder()
                    .fieldName("methods")
                    .dataType(DataType.JSON)
                    .build());

            collectionSchema.addField(AddFieldReq.builder()
                    .fieldName("depends_on")
                    .dataType(DataType.JSON)
                    .build());

            collectionSchema.addField(AddFieldReq.builder()
                    .fieldName("last_commit")
                    .dataType(DataType.VarChar)
                    .maxLength(50)
                    .isNullable(true)  // Поле может быть null
                    .build());

            // Параметры индекса для векторного поля
            IndexParam indexParam = IndexParam.builder()
                    .fieldName("code_embed")  // Индексируем векторное поле
                    .metricType(IndexParam.MetricType.COSINE)  // Косинусное расстояние
                    .build();

            // Создаем коллекцию
            CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                    .collectionName(PROJECT_KNOWLEDGE_COLLECTION)
                    .collectionSchema(collectionSchema)
                    .indexParams(Collections.singletonList(indexParam))
                    .build();

            client.createCollection(createCollectionReq);
            System.out.println("Коллекция " + PROJECT_KNOWLEDGE_COLLECTION +" успешно создана");
        } catch (Exception e) {
            System.err.println("Ошибка при создании коллекции " + PROJECT_KNOWLEDGE_COLLECTION + ": " + e.getMessage());
        }
    }


}
