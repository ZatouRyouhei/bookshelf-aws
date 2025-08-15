package com.example.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.example.Constant;
import com.example.dto.RestUser;
import com.example.utils.PasswordGenerator;
import com.example.utils.SHA512Encoder;

import software.amazon.awssdk.services.dynamodb.model.AttributeAction;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.regions.Region;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class UserResource {
    public APIGatewayProxyResponseEvent login(APIGatewayProxyRequestEvent requestEvent) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        
        // パラメータ取得
        String requestBody = requestEvent.getBody();
        RestUser paramUser = new RestUser();
        if (requestBody != null && !requestBody.isEmpty()) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                paramUser = objectMapper.readValue(requestBody, RestUser.class);
            } catch (JsonProcessingException e) {
                System.out.println(e.getMessage());
                response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.BAD_REQUEST)
                    .withBody("パラメータが不正です。" + e.getMessage());
                return response;
            }
        }

        // DynamoDBからユーザ情報を取得
        try {
            // DynamoDBクライアント生成
            Region region = Region.AP_NORTHEAST_1;
            DynamoDbClient ddb = DynamoDbClient.builder().region(region).build();
            // キー情報生成
            Map<String, AttributeValue> keyToGet = new HashMap<>();
            keyToGet.put("id", AttributeValue.builder().s(paramUser.getId()).build());
            GetItemRequest request = GetItemRequest.builder().key(keyToGet).tableName("t_bookshelf_user").build();
            // データ取得
            Map<String, AttributeValue> returnedItem = ddb.getItem(request).item();
            if (returnedItem.isEmpty()) {
                System.out.format("No item found with the key %s!\n", "id");
                response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.NOT_FOUND)
                    .withBody("ユーザが見つかりませんでした。");
            } else {
                // DynamoDBに保存されているパスワードと入力されているパスワードが一致していたら認証成功
                String dbPasswowrd = returnedItem.get("password").s();
                if (dbPasswowrd.equals(paramUser.getPassword())) {
                    // 認証成功
                    RestUser resUser = new RestUser();
                    resUser.setId(returnedItem.get("id").s());
                    resUser.setName(returnedItem.get("name").s());
                    resUser.setRoleName(returnedItem.get("roleName").s());
                    // JSON形式に変換
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);
                    String script = "";
                    try {
                        // JWT生成
                        Algorithm algorithm = Algorithm.HMAC256(Constant.SECRET_KEY);
                        String token = JWT.create()
                            .withClaim("id", resUser.getId())
                            .withClaim("name", resUser.getName())
                            .withClaim("roleName", resUser.getRoleName())
                            .sign(algorithm);
                        resUser.setToken(token);
                        
                        script = mapper.writeValueAsString(resUser);
                        response = new APIGatewayProxyResponseEvent()
                            .withStatusCode(HttpStatusCode.OK)
                            .withBody(script);
                    } catch (JsonProcessingException e) {
                        response = new APIGatewayProxyResponseEvent()
                            .withStatusCode(HttpStatusCode.BAD_GATEWAY)
                            .withBody("応答に失敗しました。");
                        return response;
                    }
                } else {
                    // パスワード誤り
                    response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(HttpStatusCode.BAD_REQUEST)
                        .withBody("認証に失敗しました。");
                }
            }
        } catch (DynamoDbException e) {
            System.err.println(e.getMessage());
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("データベースに接続できませんでした。");
        }
        return response;
    }

    public APIGatewayProxyResponseEvent changePassword(APIGatewayProxyRequestEvent requestEvent) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        // パラメータ取得
        String requestBody = requestEvent.getBody();
        RestUser paramUser = new RestUser();
        if (requestBody != null && !requestBody.isEmpty()) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                paramUser = objectMapper.readValue(requestBody, RestUser.class);
            } catch (JsonProcessingException e) {
                System.err.println(e.getMessage());
                response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.BAD_REQUEST)
                    .withBody("パラメータが不正です。");
                return response;
            }
        }

        // DynamoDBからユーザ情報を取得
        try {
            // DynamoDBクライアント生成
            Region region = Region.AP_NORTHEAST_1;
            DynamoDbClient ddb = DynamoDbClient.builder().region(region).build();
            // 入力されたパスワード
            String inputPassword = paramUser.getPassword();
            // アップデート対象のキー設定
            HashMap<String, AttributeValue> itemKey = new HashMap<>();
            itemKey.put("id", AttributeValue.builder().s(paramUser.getId()).build());
            // アップデート値を設定
            HashMap<String, AttributeValueUpdate> updatedValues = new HashMap<>();
            updatedValues.put("password", AttributeValueUpdate.builder().value(AttributeValue.builder().s(inputPassword).build()).action(AttributeAction.PUT).build());
            UpdateItemRequest request = UpdateItemRequest.builder().tableName("t_bookshelf_user").key(itemKey).attributeUpdates(updatedValues).build();
            // アップデート処理
            ddb.updateItem(request);
            response = new APIGatewayProxyResponseEvent()
                            .withStatusCode(HttpStatusCode.OK)
                            .withBody("パスワードを変更しました。");
        } catch (DynamoDbException e) {
            System.err.println(e.getMessage());
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("データベースに接続できませんでした。");
        }
        return response;
    }

    public APIGatewayProxyResponseEvent getList(APIGatewayProxyRequestEvent requestEvent) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        try {
            // DynamoDBクライアント生成
            Region region = Region.AP_NORTHEAST_1;
            DynamoDbClient ddb = DynamoDbClient.builder().region(region).build();
            ScanRequest scanRequest = ScanRequest.builder().tableName("t_bookshelf_user").build();
            // テーブルスキャン
            ScanResponse scanResponse = ddb.scan(scanRequest);
            // スキャン結果をリストに変換
            List<RestUser> userList = new ArrayList<>();
            for (Map<String, AttributeValue> item : scanResponse.items()) {
                RestUser restUser = new RestUser();
                restUser.setId(item.get("id").s());
                restUser.setName(item.get("name").s());
                restUser.setRoleName(item.get("roleName").s());
                restUser.setMailAddress(item.get("mailAddress").s());
                userList.add(restUser);
            }
            // id順に並び替え
            List<RestUser> orderedUserList = userList.stream().sorted(Comparator.comparing(RestUser::getId)).collect(Collectors.toList());
            // リストをJSON形式に変換
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            String script = "";
            try {
                script = mapper.writeValueAsString(orderedUserList);
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

    public APIGatewayProxyResponseEvent registUser(APIGatewayProxyRequestEvent requestEvent) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        // パラメータ取得
        String requestBody = requestEvent.getBody();
        RestUser paramUser = new RestUser();
        if (requestBody != null && !requestBody.isEmpty()) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                paramUser = objectMapper.readValue(requestBody, RestUser.class);
            } catch (JsonProcessingException e) {
                System.err.println(e.getMessage());
                response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.BAD_REQUEST)
                    .withBody("パラメータが不正です。");
                return response;
            }
        }

        try {
            // DynamoDBクライアント生成
            Region region = Region.AP_NORTHEAST_1;
            DynamoDbClient ddb = DynamoDbClient.builder().region(region).build();

            // ユーザすでにいるかどうか確認する
            Map<String, AttributeValue> keyToGet = new HashMap<>();
            keyToGet.put("id", AttributeValue.builder().s(paramUser.getId()).build());
            GetItemRequest request = GetItemRequest.builder().key(keyToGet).tableName("t_bookshelf_user").build();
            // データ取得
            Map<String, AttributeValue> returnedItem = ddb.getItem(request).item();
            if (returnedItem.isEmpty()) {
                // いない場合は新規登録
                // 登録データ作成
                HashMap<String, AttributeValue> itemValues = new HashMap<>();
                itemValues.put("id", AttributeValue.builder().s(paramUser.getId()).build());
                itemValues.put("name", AttributeValue.builder().s(paramUser.getName()).build());
                itemValues.put("roleName", AttributeValue.builder().s(paramUser.getRoleName()).build());
                itemValues.put("mailAddress", AttributeValue.builder().s(paramUser.getMailAddress()).build());
                // 初期パスワード生成（ランダムの文字列6文字）
                String generatePassword = PasswordGenerator.generate(6);
                SHA512Encoder encoder = new SHA512Encoder();
                String iniPassword = encoder.encodePassword(generatePassword);
                itemValues.put("password", AttributeValue.builder().s(iniPassword).build());
                // データベースに登録
                PutItemRequest putItemRequest = PutItemRequest.builder().tableName("t_bookshelf_user").item(itemValues).build();
                ddb.putItem(putItemRequest);

                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(HttpStatusCode.OK)
                        .withBody("ユーザを登録しました。");

                // 生成したパスワードをメールで通知する
                SqsClient sqsClient = SqsClient.builder().region(region).build();
                Map<String, MessageAttributeValue> messageAttributeMap = Map.of(
                    "title", MessageAttributeValue.builder()
                                .stringValue("【ワタシノホンダナ】ユーザ新規登録")
                                .dataType("String")
                                .build(),
                    "body", MessageAttributeValue.builder()
                                .stringValue(String.format("ユーザを新規登録しました。\n URL : %s \n ID : %s \n パスワード : %s", Constant.SYSTEM_URL, paramUser.getId(), generatePassword))
                                .dataType("String")
                                .build(),
                    "mailto", MessageAttributeValue.builder()
                                .stringValue(paramUser.getMailAddress())
                                .dataType("String")
                                .build()
                );
                SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                    .queueUrl(Constant.QUEUE_URL)
                    .messageBody(messageAttributeMap.get("body").stringValue())
                    .messageAttributes(messageAttributeMap)
                    .delaySeconds(1)
                    .build();
                sqsClient.sendMessage(sendMsgRequest);
            } else {
                // いる場合は更新
                // 登録データ生成
                HashMap<String, AttributeValueUpdate> updateValues = new HashMap<>();
                updateValues.put("name", AttributeValueUpdate.builder().value(AttributeValue.builder().s(paramUser.getName()).build()).action(AttributeAction.PUT).build());
                updateValues.put("roleName", AttributeValueUpdate.builder().value(AttributeValue.builder().s(paramUser.getRoleName()).build()).action(AttributeAction.PUT).build());
                updateValues.put("mailAddress", AttributeValueUpdate.builder().value(AttributeValue.builder().s(paramUser.getMailAddress()).build()).action(AttributeAction.PUT).build());
                // データベースに登録
                UpdateItemRequest updateItemRequest = UpdateItemRequest.builder().tableName("t_bookshelf_user").key(keyToGet).attributeUpdates(updateValues).build();
                ddb.updateItem(updateItemRequest);
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(HttpStatusCode.OK)
                        .withBody("ユーザを登録しました。");
            }
        } catch (ResourceNotFoundException e) {
            System.err.println(e.getMessage());
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("テーブルが存在しません。");
            return response;
        } catch (DynamoDbException e) {
            System.err.println(e.getMessage());
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("データベースに接続できませんでした。");
            return response;
        }
        return response;
    }

    public APIGatewayProxyResponseEvent deleteUser(APIGatewayProxyRequestEvent requestEvent) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        // 削除対象のidを取得
        String targetId = "";
        Map<String, String> pathParameters = requestEvent.getPathParameters();
        if (pathParameters != null) {
            targetId = pathParameters.get("id");
        }

        try {
            // DynamoDBクライアント生成
            Region region = Region.AP_NORTHEAST_1;
            DynamoDbClient ddb = DynamoDbClient.builder().region(region).build();
            // 本のデータを登録しているか確認
            // 本のデータを登録しているユーザは削除不可
            Map<String, AttributeValue> attrValues = new HashMap<>();
            attrValues.put(":targetId", AttributeValue.builder().s(targetId).build());
            QueryRequest queryReq = QueryRequest.builder()
                .tableName("t_bookshelf_book")
                .keyConditionExpression("userId = :targetId")
                .expressionAttributeValues(attrValues)
                .build();
            QueryResponse queryResponse = ddb.query(queryReq);
            if (queryResponse.count() > 0) {
                // 本のデータが登録されているので削除できません。0を返答する。
                response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.OK)
                    .withBody("0");
                return response;
            }
            // 削除キーデータ作成
            HashMap<String, AttributeValue> keyToDel = new HashMap<>();
            keyToDel.put("id", AttributeValue.builder().s(targetId).build());
            DeleteItemRequest deleteItemRequest = DeleteItemRequest.builder().tableName("t_bookshelf_user").key(keyToDel).build();
            // データ削除成功 1を返答
            ddb.deleteItem(deleteItemRequest);
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.OK)
                    .withBody("1");
        } catch (DynamoDbException e) {
            System.out.println(e.getMessage());
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("データベースに接続できませんでした。: " + e.getMessage());
            return response;
        }
        return response;
    }

    public APIGatewayProxyResponseEvent resetPassword(APIGatewayProxyRequestEvent requestEvent) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        // パスワード初期化対象のidを取得
        String targetId = "";
        Map<String, String> pathParameters = requestEvent.getPathParameters();
        if (pathParameters != null) {
            targetId = pathParameters.get("id");
        }

        // DynamoDBクライアント生成
        Region region = Region.AP_NORTHEAST_1;
        DynamoDbClient ddb = DynamoDbClient.builder().region(region).build();

        // ユーザが存在するかどうか確認する
        Map<String, AttributeValue> keyToGet = new HashMap<>();
        keyToGet.put("id", AttributeValue.builder().s(targetId).build());
        GetItemRequest request = GetItemRequest.builder().key(keyToGet).tableName("t_bookshelf_user").build();
        // データ取得
        Map<String, AttributeValue> returnedItem = ddb.getItem(request).item();
        if (!returnedItem.isEmpty()) {
            // ユーザが存在している場合は処理続行
            // アップデート対象のキー設定
            HashMap<String, AttributeValue> itemKey = new HashMap<>();
            itemKey.put("id", AttributeValue.builder().s(targetId).build());

            // 初期パスワード生成（ランダムの文字列6文字）
            String generatePassword = PasswordGenerator.generate(6);
            SHA512Encoder encoder = new SHA512Encoder();
            String iniPassword = encoder.encodePassword(generatePassword);

            // 登録データ生成
            HashMap<String, AttributeValueUpdate> updateValues = new HashMap<>();
            updateValues.put("password", AttributeValueUpdate.builder().value(AttributeValue.builder().s(iniPassword).build()).action(AttributeAction.PUT).build());
            
            // データベースに登録
            UpdateItemRequest updateItemRequest = UpdateItemRequest.builder().tableName("t_bookshelf_user").key(itemKey).attributeUpdates(updateValues).build();
            ddb.updateItem(updateItemRequest);

            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.OK)
                    .withBody("パスワードが初期化されました。");

            // 生成したパスワードをメールで通知する
            SqsClient sqsClient = SqsClient.builder().region(region).build();
            String mailAddress = returnedItem.get("mailAddress").s();
            Map<String, MessageAttributeValue> messageAttributeMap = Map.of(
                "title", MessageAttributeValue.builder()
                            .stringValue("【ワタシノホンダナ】パスワード初期化")
                            .dataType("String")
                            .build(),
                "body", MessageAttributeValue.builder()
                            .stringValue(String.format("パスワードを初期化しました。\n URL : %s \n ID : %s \n パスワード : %s", Constant.SYSTEM_URL, targetId, generatePassword))
                            .dataType("String")
                            .build(),
                "mailto", MessageAttributeValue.builder()
                            .stringValue(mailAddress)
                            .dataType("String")
                            .build()
            );
            SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                .queueUrl(Constant.QUEUE_URL)
                .messageBody(messageAttributeMap.get("body").stringValue())
                .messageAttributes(messageAttributeMap)
                .delaySeconds(1)
                .build();
            sqsClient.sendMessage(sendMsgRequest);
        } else {
            // ユーザが存在しない場合は処理終了
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.BAD_GATEWAY)
                    .withBody("パラメータが不正です。");
        }
        return response;
    }
}
