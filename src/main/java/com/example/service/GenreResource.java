package com.example.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.example.dto.RestGenre;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

public class GenreResource {
    public APIGatewayProxyResponseEvent getList(APIGatewayProxyRequestEvent requestEvent) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        try {
            // DynamoDBクライアント生成
            Region region = Region.AP_NORTHEAST_1;
            DynamoDbClient ddb = DynamoDbClient.builder().region(region).build();
            ScanRequest scanRequest = ScanRequest.builder().tableName("t_bookshelf_genre").build();
            // テーブルスキャン
            ScanResponse scanResponse = ddb.scan(scanRequest);
            // スキャン結果をリストに変換
            List<RestGenre> genreList = new ArrayList<>();
            for (Map<String, AttributeValue> item : scanResponse.items()) {
                RestGenre restGenre = new RestGenre();
                restGenre.setId(Integer.parseInt(item.get("id").n()));
                restGenre.setName(item.get("name").s());
                genreList.add(restGenre);
            }
            // id順に並び替え
            List<RestGenre> orderedGenreList = genreList.stream().sorted(Comparator.comparing(RestGenre::getId)).collect(Collectors.toList());
            // リストをJSON形式に変換
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            String script = "";
            try {
                script = mapper.writeValueAsString(orderedGenreList);
                response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.OK)
                    .withBody(script);
            } catch (JsonProcessingException e) {
                response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.BAD_GATEWAY)
                    .withBody("応答に失敗しました。");
                return response;
            }
        } catch (DynamoDbException e) {
            System.err.println(e.getMessage());
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("データベースに接続できませんでした。");
            return response;
        }
        return response;
    }
}
