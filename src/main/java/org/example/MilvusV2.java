package org.example;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.GetReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.GetResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.response.GetCollectionStatsResp;

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
        GetCollectionStatsReq req = GetCollectionStatsReq.builder()
                .collectionName(collectionName)
                .build();

        GetCollectionStatsResp resp = client.getCollectionStats(req);

        return resp.getNumOfEntities();
    }

    public static void deleteCollectionByName(String name) {
        client.dropCollection(
                DropCollectionReq.builder()
                        .collectionName(name)
                        .build()
        );
    }


}
