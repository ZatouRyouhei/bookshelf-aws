package com.example.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.example.dto.RestBook;
import com.example.dto.RestGenre;
import com.example.dto.RestSearchCondition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest.Builder;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import org.apache.poi.hssf.usermodel.HSSFDataFormat;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class BookResource {
    public APIGatewayProxyResponseEvent registBook(APIGatewayProxyRequestEvent requestEvent) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        // パラメータ取得
        String requestBody = requestEvent.getBody();
        RestBook paramBook = new RestBook();
        if (requestBody != null && !requestBody.isEmpty()) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                paramBook = objectMapper.readValue(requestBody, RestBook.class);
            } catch (JsonProcessingException e) {
                System.out.println(e.getMessage());
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
            // 連番が入っていない場合（新規登録）は連番を求める
            if (paramBook.getSeqNo() == 0) {
                // ユーザIDで本情報を取得
                Map<String, AttributeValue> attrValues = new HashMap<>();
                attrValues.put(":targetId", AttributeValue.builder().s(paramBook.getUserId()).build());
                QueryRequest queryReq = QueryRequest.builder()
                    .tableName("t_bookshelf_book")
                    .keyConditionExpression("userId = :targetId")
                    .expressionAttributeValues(attrValues)
                    .build();
                QueryResponse queryResponse = ddb.query(queryReq);
                // 最大の連番を求める。
                int maxSeqNo = 0;
                for (Map<String, AttributeValue> item : queryResponse.items()) {
                    int seqNo = Integer.parseInt(item.get("seqNo").n());
                    if (seqNo > maxSeqNo) {
                        maxSeqNo = seqNo;
                    }
                }
                // 最大の連番+1を次の連番として設定する。
                paramBook.setSeqNo(maxSeqNo+1);
            }
            // 登録データ作成
            HashMap<String, AttributeValue> itemValues = new HashMap<>();
            itemValues.put("userId", AttributeValue.builder().s(paramBook.getUserId()).build());
            itemValues.put("seqNo", AttributeValue.builder().n(String.valueOf(paramBook.getSeqNo())).build());
            itemValues.put("title", AttributeValue.builder().s(paramBook.getTitle()).build());
            itemValues.put("author", AttributeValue.builder().s(paramBook.getAuthor()).build());
            itemValues.put("price", AttributeValue.builder().n(String.valueOf(paramBook.getPrice())).build());
            itemValues.put("publisher", AttributeValue.builder().s(paramBook.getPublisher()).build());
            itemValues.put("published", AttributeValue.builder().s(paramBook.getPublished()).build());
            itemValues.put("buyDate", AttributeValue.builder().s(paramBook.getBuyDate()).build());
            itemValues.put("completeDate", AttributeValue.builder().s(paramBook.getCompleteDate()).build());
            itemValues.put("genre", AttributeValue.builder().n(String.valueOf(paramBook.getGenre().getId())).build());
            itemValues.put("memo", AttributeValue.builder().s(paramBook.getMemo()).build());
            itemValues.put("rate", AttributeValue.builder().n(String.valueOf(paramBook.getRate())).build());
            itemValues.put("imgUrl", AttributeValue.builder().s(paramBook.getImgUrl()).build());
            itemValues.put("infoUrl", AttributeValue.builder().s(paramBook.getInfoUrl()).build());
            // データベースに登録
            PutItemRequest request = PutItemRequest.builder().tableName("t_bookshelf_book").item(itemValues).build();
            ddb.putItem(request);
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.OK)
                    .withBody(String.valueOf(paramBook.getSeqNo()));
        } catch (ResourceNotFoundException e) {
            System.out.println(e.getMessage());
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("テーブルが存在しません。");
            return response;
        } catch (DynamoDbException e) {
            System.out.println(e.getMessage());
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("データベースに接続できませんでした。");
            return response;
        }
        return response;
    }

    public APIGatewayProxyResponseEvent searchBook(APIGatewayProxyRequestEvent requestEvent) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        // パラメータ取得
        String requestBody = requestEvent.getBody();
        RestSearchCondition paramCondition = new RestSearchCondition();
        if (requestBody != null && !requestBody.isEmpty()) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                paramCondition = objectMapper.readValue(requestBody, RestSearchCondition.class);
            } catch (JsonProcessingException e) {
                System.out.println(e.getMessage());
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
            // クエリ実施
            // 結果が1MBを超えることを想定しlastEvaluatedKeyをもとに繰り返しqueryを実行する。
            List<RestBook> bookList = new ArrayList<>();
            Map<String, AttributeValue> lastEvaluatedKey = null;
            do {
                try {
                    // 検索条件設定
                    Map<String, AttributeValue> attrValues = new HashMap<>();
                    Builder queryBuilder = QueryRequest.builder().tableName("t_bookshelf_book");
                    // ユーザID（クエリはキー指定必須）
                    attrValues.put(":userId", AttributeValue.builder().s(paramCondition.getUserId()).build());
                    queryBuilder = queryBuilder.keyConditionExpression("userId = :userId");
                    // キー以外の条件
                    StringBuilder filterExpressionBuilder = new StringBuilder();
                    // タイトル
                    if (StringUtils.isNotEmpty(paramCondition.getTitle())) {
                        attrValues.put(":title", AttributeValue.builder().s(paramCondition.getTitle()).build());
                        if (filterExpressionBuilder.length() > 0) {
                            filterExpressionBuilder.append(" and ");
                        }
                        filterExpressionBuilder.append("contains(title, :title)");
                    }
                    // 著者
                    if (StringUtils.isNotEmpty(paramCondition.getAuthor())) {
                        attrValues.put(":author", AttributeValue.builder().s(paramCondition.getAuthor()).build());
                        if (filterExpressionBuilder.length() > 0) {
                            filterExpressionBuilder.append(" and ");
                        }
                        filterExpressionBuilder.append("contains(author, :author)");
                    }
                    // 読了日（from）
                    if (StringUtils.isNotEmpty(paramCondition.getCompleteDateFrom())) {
                        attrValues.put(":completeDateFrom", AttributeValue.builder().s(paramCondition.getCompleteDateFrom()).build());
                        if (filterExpressionBuilder.length() > 0) {
                            filterExpressionBuilder.append(" and ");
                        }
                        filterExpressionBuilder.append("completeDate >= :completeDateFrom");
                    }
                    // 読了日（to）
                    if (StringUtils.isNotEmpty(paramCondition.getCompleteDateTo())) {
                        attrValues.put(":completeDateTo", AttributeValue.builder().s(paramCondition.getCompleteDateTo()).build());
                        if (filterExpressionBuilder.length() > 0) {
                            filterExpressionBuilder.append(" and ");
                        }
                        filterExpressionBuilder.append("completeDate <= :completeDateTo");
                    }
                    // ジャンル
                    if (paramCondition.getGenre() != 0) {
                        attrValues.put(":genre", AttributeValue.builder().n(String.valueOf(paramCondition.getGenre())).build());
                        if (filterExpressionBuilder.length() > 0) {
                            filterExpressionBuilder.append(" and ");
                        }
                        filterExpressionBuilder.append("genre = :genre");
                    }
                    // 評価
                    if (paramCondition.getRate() != 0) {
                        attrValues.put(":rate", AttributeValue.builder().n(String.valueOf(paramCondition.getRate())).build());
                        if (filterExpressionBuilder.length() > 0) {
                            filterExpressionBuilder.append(" and ");
                        }
                        filterExpressionBuilder.append("rate = :rate");
                    }
                    queryBuilder = queryBuilder.expressionAttributeValues(attrValues);
                    if (filterExpressionBuilder.length() > 0) {
                        queryBuilder = queryBuilder.filterExpression(filterExpressionBuilder.toString());
                    }
                    // 前ループのqueryの結果が1MBを超えていた場合にはlastEvaluatedKeyを設定する。
                    if (lastEvaluatedKey != null) {
                        queryBuilder.exclusiveStartKey(lastEvaluatedKey);
                    }
                    QueryResponse queryResponse = ddb.query(queryBuilder.build());
                    // ジャンルテーブルを取得する。
                    ScanRequest scanGenreRequest = ScanRequest.builder().tableName("t_bookshelf_genre").build();
                    ScanResponse genreResponse = ddb.scan(scanGenreRequest);
                    Map<Integer, String> genreMap = new HashMap<>();
                    for (Map<String, AttributeValue> item : genreResponse.items()) {
                        genreMap.put(Integer.parseInt(item.get("id").n()), item.get("name").s());
                    }
                    // クエリ結果をリストに変換
                    for (Map<String, AttributeValue> item : queryResponse.items()) {
                        RestBook restBook = new RestBook();
                        restBook.setUserId(item.get("userId").s());
                        restBook.setSeqNo(Integer.parseInt(item.get("seqNo").n()));
                        restBook.setTitle(item.get("title").s());
                        restBook.setAuthor(item.get("author").s());
                        restBook.setPrice(Integer.parseInt(item.get("price").n()));
                        restBook.setPublisher(item.get("publisher").s());
                        restBook.setPublished(item.get("published").s());
                        restBook.setBuyDate(item.get("buyDate").s());
                        restBook.setCompleteDate(item.get("completeDate").s());
                        // ジャンル設定
                        RestGenre restGenre = new RestGenre();
                        restGenre.setId(Integer.parseInt(item.get("genre").n()));
                        restGenre.setName(genreMap.get(Integer.parseInt(item.get("genre").n())));
                        restBook.setGenre(restGenre);
                        
                        restBook.setMemo(item.get("memo").s());
                        restBook.setRate(Integer.parseInt(item.get("rate").n()));
                        restBook.setImgUrl(item.get("imgUrl").s());
                        restBook.setInfoUrl(item.get("infoUrl").s());
                        bookList.add(restBook);
                    }
                    lastEvaluatedKey = queryResponse.lastEvaluatedKey();
                    if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
                        break;
                    }
                } catch (DynamoDbException e) {
                    System.out.println(e.getMessage());
                    response = new APIGatewayProxyResponseEvent()
                            .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                            .withBody("データベースに接続できませんでした。:" + e.getMessage());
                    return response;
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    response = new APIGatewayProxyResponseEvent()
                            .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                            .withBody("予期せぬエラーが発生しました。:" + e.getMessage());
                    return response;
                }
            } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());
            
            // 購入日の降順に並び替え
            List<RestBook> orderedBookList = bookList.stream().sorted(Comparator.comparing(RestBook::getBuyDate).reversed()).collect(Collectors.toList());
            // リストをJSON形式に変換
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            String script = "";
            try {
                script = mapper.writeValueAsString(orderedBookList);
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
            System.out.println(e.getMessage());
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("データベースに接続できませんでした。:" + e.getMessage());
            return response;
        }
        return response;
    }

    public APIGatewayProxyResponseEvent downloadExcel(APIGatewayProxyRequestEvent requestEvent) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        // パラメータ取得
        String requestBody = requestEvent.getBody();
        RestSearchCondition paramCondition = new RestSearchCondition();
        if (requestBody != null && !requestBody.isEmpty()) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                paramCondition = objectMapper.readValue(requestBody, RestSearchCondition.class);
            } catch (JsonProcessingException e) {
                System.out.println(e.getMessage());
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
            // クエリ実施
            // 結果が1MBを超えることを想定しlastEvaluatedKeyをもとに繰り返しqueryを実行する。
            List<RestBook> bookList = new ArrayList<>();
            Map<String, AttributeValue> lastEvaluatedKey = null;
            do {
                try {
                    // 検索条件設定
                    Map<String, AttributeValue> attrValues = new HashMap<>();
                    Builder queryBuilder = QueryRequest.builder().tableName("t_bookshelf_book");
                    // ユーザID（クエリはキー指定必須）
                    attrValues.put(":userId", AttributeValue.builder().s(paramCondition.getUserId()).build());
                    queryBuilder = queryBuilder.keyConditionExpression("userId = :userId");
                    // キー以外の条件
                    StringBuilder filterExpressionBuilder = new StringBuilder();
                    // タイトル
                    if (StringUtils.isNotEmpty(paramCondition.getTitle())) {
                        attrValues.put(":title", AttributeValue.builder().s(paramCondition.getTitle()).build());
                        if (filterExpressionBuilder.length() > 0) {
                            filterExpressionBuilder.append(" and ");
                        }
                        filterExpressionBuilder.append("contains(title, :title)");
                    }
                    // 著者
                    if (StringUtils.isNotEmpty(paramCondition.getAuthor())) {
                        attrValues.put(":author", AttributeValue.builder().s(paramCondition.getAuthor()).build());
                        if (filterExpressionBuilder.length() > 0) {
                            filterExpressionBuilder.append(" and ");
                        }
                        filterExpressionBuilder.append("contains(author, :author)");
                    }
                    // 読了日（from）
                    if (StringUtils.isNotEmpty(paramCondition.getCompleteDateFrom())) {
                        attrValues.put(":completeDateFrom", AttributeValue.builder().s(paramCondition.getCompleteDateFrom()).build());
                        if (filterExpressionBuilder.length() > 0) {
                            filterExpressionBuilder.append(" and ");
                        }
                        filterExpressionBuilder.append("completeDate >= :completeDateFrom");
                    }
                    // 読了日（to）
                    if (StringUtils.isNotEmpty(paramCondition.getCompleteDateTo())) {
                        attrValues.put(":completeDateTo", AttributeValue.builder().s(paramCondition.getCompleteDateTo()).build());
                        if (filterExpressionBuilder.length() > 0) {
                            filterExpressionBuilder.append(" and ");
                        }
                        filterExpressionBuilder.append("completeDate <= :completeDateTo");
                    }
                    // ジャンル
                    if (paramCondition.getGenre() != 0) {
                        attrValues.put(":genre", AttributeValue.builder().n(String.valueOf(paramCondition.getGenre())).build());
                        if (filterExpressionBuilder.length() > 0) {
                            filterExpressionBuilder.append(" and ");
                        }
                        filterExpressionBuilder.append("genre = :genre");
                    }
                    // 評価
                    if (paramCondition.getRate() != 0) {
                        attrValues.put(":rate", AttributeValue.builder().n(String.valueOf(paramCondition.getRate())).build());
                        if (filterExpressionBuilder.length() > 0) {
                            filterExpressionBuilder.append(" and ");
                        }
                        filterExpressionBuilder.append("rate = :rate");
                    }
                    queryBuilder = queryBuilder.expressionAttributeValues(attrValues);
                    if (filterExpressionBuilder.length() > 0) {
                        queryBuilder = queryBuilder.filterExpression(filterExpressionBuilder.toString());
                    }
                    // 前ループのqueryの結果が1MBを超えていた場合にはlastEvaluatedKeyを設定する。
                    if (lastEvaluatedKey != null) {
                        queryBuilder.exclusiveStartKey(lastEvaluatedKey);
                    }
                    QueryResponse queryResponse = ddb.query(queryBuilder.build());
                    // ジャンルテーブルを取得する。
                    ScanRequest scanGenreRequest = ScanRequest.builder().tableName("t_bookshelf_genre").build();
                    ScanResponse genreResponse = ddb.scan(scanGenreRequest);
                    Map<Integer, String> genreMap = new HashMap<>();
                    for (Map<String, AttributeValue> item : genreResponse.items()) {
                        genreMap.put(Integer.parseInt(item.get("id").n()), item.get("name").s());
                    }
                    // クエリ結果をリストに変換
                    for (Map<String, AttributeValue> item : queryResponse.items()) {
                        RestBook restBook = new RestBook();
                        restBook.setUserId(item.get("userId").s());
                        restBook.setSeqNo(Integer.parseInt(item.get("seqNo").n()));
                        restBook.setTitle(item.get("title").s());
                        restBook.setAuthor(item.get("author").s());
                        restBook.setPrice(Integer.parseInt(item.get("price").n()));
                        restBook.setPublisher(item.get("publisher").s());
                        restBook.setPublished(item.get("published").s());
                        restBook.setBuyDate(item.get("buyDate").s());
                        restBook.setCompleteDate(item.get("completeDate").s());
                        // ジャンル設定
                        RestGenre restGenre = new RestGenre();
                        restGenre.setId(Integer.parseInt(item.get("genre").n()));
                        restGenre.setName(genreMap.get(Integer.parseInt(item.get("genre").n())));
                        restBook.setGenre(restGenre);
                        
                        restBook.setMemo(item.get("memo").s());
                        restBook.setRate(Integer.parseInt(item.get("rate").n()));
                        restBook.setImgUrl(item.get("imgUrl").s());
                        restBook.setInfoUrl(item.get("infoUrl").s());
                        bookList.add(restBook);
                    }
                    lastEvaluatedKey = queryResponse.lastEvaluatedKey();
                    if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
                        break;
                    }
                } catch (DynamoDbException e) {
                    System.out.println(e.getMessage());
                    response = new APIGatewayProxyResponseEvent()
                            .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                            .withBody("データベースに接続できませんでした。:" + e.getMessage());
                    return response;
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    response = new APIGatewayProxyResponseEvent()
                            .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                            .withBody("予期せぬエラーが発生しました。:" + e.getMessage());
                    return response;
                }
            } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());
            
            // 購入日の降順に並び替え
            List<RestBook> orderedBookList = bookList.stream().sorted(Comparator.comparing(RestBook::getBuyDate).reversed()).collect(Collectors.toList());

            // Excel生成
            try (InputStream templateStream = getClass().getResourceAsStream("/template/template.xlsx")) { // 引数のパスの先頭にはスラッシュ(/)が必要
                if (templateStream == null) {
                    System.out.println("テンプレートファイルが見つかりません。src/main/resources/template/template.xlsx を確認してください。");
                    throw new IOException();
                }
                // テンプレートからワークブックを作成
                try (Workbook workbook = new XSSFWorkbook(templateStream)) {
                    // シート取得
                    Sheet sheet = workbook.getSheetAt(0);
                    // リストから1行づつ取り出してセルに設定していく
                    for (int rowIndex = 2; rowIndex <= orderedBookList.size() + 1; rowIndex++) {
                        // 行を生成
                        Row row = sheet.getRow(rowIndex);
                        if(row == null){
                            row = sheet.createRow(rowIndex);
                        }
                        // セルの枠線を設定する。
                        CellStyle cellStyle = workbook.createCellStyle();
                        cellStyle.setBorderLeft(BorderStyle.THIN);
                        cellStyle.setBorderRight(BorderStyle.THIN);
                        cellStyle.setBorderTop(BorderStyle.THIN);
                        cellStyle.setBorderBottom(BorderStyle.THIN);
                        // 上詰め
                        cellStyle.setVerticalAlignment(VerticalAlignment.TOP);
                        // 改行して表示
                        cellStyle.setWrapText(true);
                        // 日付用のセルスタイル
                        CellStyle dateCellStyle = workbook.createCellStyle();
                        dateCellStyle.cloneStyleFrom(cellStyle);
                        dateCellStyle.setDataFormat(HSSFDataFormat.getBuiltinFormat("m/d/yy"));
                        // 値段用のセルスタイル
                        CellStyle priceCellStyle = workbook.createCellStyle();
                        priceCellStyle.cloneStyleFrom(cellStyle);
                        priceCellStyle.setDataFormat(HSSFDataFormat.getBuiltinFormat("#,##0"));

                        // セルに値を設定していく
                        RestBook bookInfo = orderedBookList.get(rowIndex - 2);
                        // No
                        setCellValue(row, 0, rowIndex-1, cellStyle);
                        // タイトル
                        setCellValue(row, 1, bookInfo.getTitle(), cellStyle);
                        // 著者
                        setCellValue(row, 2, bookInfo.getAuthor(), cellStyle);
                        // 値段
                        setCellValue(row, 3, bookInfo.getPrice(), priceCellStyle);
                        // 出版社
                        setCellValue(row, 4, bookInfo.getPublisher(), cellStyle);
                        // 発売日
                        String pattern = "yyyy-MM-dd";
                        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                        if (StringUtils.isNotEmpty(bookInfo.getPublished())) {
                            Date publishedDate = sdf.parse(bookInfo.getPublished());
                            setCellValue(row, 5, publishedDate, dateCellStyle);
                        } else {
                            setCellValue(row, 5, "ー", dateCellStyle);
                        }
                        // 購入日
                        if (StringUtils.isNotEmpty(bookInfo.getBuyDate())) {
                            Date buyDate = sdf.parse(bookInfo.getBuyDate());
                            setCellValue(row, 6, buyDate, dateCellStyle);
                        } else {
                            setCellValue(row, 6, "ー", dateCellStyle);
                        }
                        // 読了日
                        if (StringUtils.isNotEmpty(bookInfo.getCompleteDate())) {
                            Date completeDate = sdf.parse(bookInfo.getCompleteDate());
                            setCellValue(row, 7, completeDate, dateCellStyle);
                        } else {
                            setCellValue(row, 7, "ー", dateCellStyle);
                        }
                        // ジャンル
                        setCellValue(row, 8, bookInfo.getGenre().getName(), cellStyle);
                        // 評価
                        String strRate = "";
                        switch (bookInfo.getRate()) {
                            case 1:
                                strRate = "面白くない";
                                break;
                            case 2:
                                strRate = "あまり面白くない";
                                break;
                            case 3:
                                strRate = "普通";
                                break;
                            case 4:
                                strRate = "面白い";
                                break;
                            case 5:
                                strRate = "とても面白い";
                                break;
                            default:
                                strRate = "";
                                break;
                        }
                        setCellValue(row, 9, strRate, cellStyle);
                        // 感想
                        setCellValue(row, 10, bookInfo.getMemo(), cellStyle);
                    }

                    // 作成したエクセルをバイトに変換しBase64エンコード
                    byte[] byteArray = byteArray(workbook);
                    Base64.Encoder encoder = Base64.getEncoder();
                    String encoded = encoder.encodeToString(byteArray);
                    // ヘッダーにコンテンツタイプを設定
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    // レスポンス生成
                    response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(HttpStatusCode.OK)
                        .withBody(encoded)
                        .withIsBase64Encoded(true)
                        .withHeaders(headers);
                }
            } catch (IOException e) {
                System.out.println("ファイルの処理中にエラーが発生しました: " + e.getMessage());
                response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("ファイルの処理中にエラーが発生しました:" + e.getMessage());
                return response;
            } catch (ParseException e) {
                System.out.println("日付のフォーマットに失敗しました: " + e.getMessage());
                response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("日付のフォーマットに失敗しました:" + e.getMessage());
                return response;
            }
        } catch (DynamoDbException e) {
            System.out.println(e.getMessage());
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("データベースに接続できませんでした。:" + e.getMessage());
            return response;
        }

        return response;
    }

    /**
     * セルに値を設定する。
     * @param row
     * @param cellIndex
     * @param value
     * @param cellStyle
     */
    private void setCellValue(Row row, int cellIndex, Object value, CellStyle cellStyle) {
        Cell cell = row.getCell(cellIndex);
        if(cell == null){
            cell = row.createCell(cellIndex);
        }
        if (cellStyle != null) {
            cell.setCellStyle(cellStyle);
        }
        if (value != null) {
            if (value instanceof String) {
                cell.setCellValue((String) value);
            } else if (value instanceof Number numValue) {
                if (numValue instanceof Float floatValue) {
                    numValue = Double.valueOf(String.valueOf(floatValue));
                }
                cell.setCellValue(numValue.doubleValue());
            } else if (value instanceof Date) {
                Date dateValue = (Date) value;
                cell.setCellValue(dateValue);
            }
        }
    }

    /**
     * ワークブックからbyte配列取得
     * @param workbook
     * @return
     * @throws IOException
     */
    private byte[] byteArray(Workbook workbook) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IOException();
        }
   }

    public APIGatewayProxyResponseEvent deleteBook(APIGatewayProxyRequestEvent requestEvent) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        // 削除対象のユーザIDと連番を取得
        String targetUserId = "";
        int targetSeqNo = 0;
        Map<String, String> pathParameters = requestEvent.getPathParameters();
        if (pathParameters != null) {
            targetUserId = pathParameters.get("userId");
            targetSeqNo = Integer.parseInt(pathParameters.get("seqNo"));
        }
        // パスパラメータが設定されていないときはエラー
        if ("".equals(targetUserId) || targetSeqNo == 0) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.BAD_REQUEST)
                    .withBody("パラメータが不正です。");
                return response;
        }

        try {
            // DynamoDBクライアント生成
            Region region = Region.AP_NORTHEAST_1;
            DynamoDbClient ddb = DynamoDbClient.builder().region(region).build();
            // 削除キーデータ作成
            HashMap<String, AttributeValue> keyToDel = new HashMap<>();
            keyToDel.put("userId", AttributeValue.builder().s(targetUserId).build());
            keyToDel.put("seqNo",  AttributeValue.builder().n(String.valueOf(targetSeqNo)).build());
            DeleteItemRequest deleteItemRequest = DeleteItemRequest.builder().tableName("t_bookshelf_book").key(keyToDel).build();
            // データ削除
            ddb.deleteItem(deleteItemRequest);
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.OK)
                    .withBody("本情報を削除しました。");
        } catch (DynamoDbException e) {
            System.out.println(e.getMessage());
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("データベースに接続できませんでした。: " + e.getMessage());
            return response;
        }
        return response;
    }
}
