package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.example.service.BookResource;
import com.example.service.GenreResource;
import com.example.service.UserResource;
import com.example.utils.JwtExtractor;

import software.amazon.awssdk.http.HttpStatusCode;

public class Router implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
        String path = requestEvent.getPath();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        // JWT検証
        // ログイン時はJWT検証はスキップする
        if (!(Constant.BASE_URL + "/user/login/").equals(path)) {
            JwtExtractor jwtExtractor = new JwtExtractor();
            String requestJwt = jwtExtractor.extractJwtFromHeaders(requestEvent);
            if ("".equals(requestJwt)) {
                // JWTを取得できなかった場合はエラー
                response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.UNAUTHORIZED)
                    .withBody("認証に失敗しました。");
                return response;
            }
        }

        switch(path) {
            case Constant.BASE_URL + "/user/login/" -> {
                UserResource userResource = new UserResource();
                response = userResource.login(requestEvent);
            }
            case Constant.BASE_URL + "/user/changePassword/" -> {
                UserResource userResource = new UserResource();
                response = userResource.changePassword(requestEvent);
            }
            case Constant.BASE_URL + "/user/getList/" -> {
                UserResource userResource = new UserResource();
                response = userResource.getList(requestEvent);
            }
            case Constant.BASE_URL + "/user/regist/" -> {
                UserResource userResource = new UserResource();
                response = userResource.registUser(requestEvent);
            }
            // パスパラメータを使用する場合の記載
            // パスパラメータを使用する場合はAPI Gatewayに専用のリソースを作成する必要がある。
            case String s when s.startsWith(Constant.BASE_URL + "/user/delete/") -> {
                UserResource userResource = new UserResource();
                response = userResource.deleteUser(requestEvent);
            }
            case Constant.BASE_URL + "/book/regist/" -> {
                BookResource bookResource = new BookResource();
                response = bookResource.registBook(requestEvent);
            }
            case Constant.BASE_URL + "/book/search/" -> {
                BookResource bookResource = new BookResource();
                response = bookResource.searchBook(requestEvent);
            }
            // パスパラメータを使用する場合の記載
            // パスパラメータを使用する場合はAPI Gatewayに専用のリソースを作成する必要がある。
            case String s when s.startsWith(Constant.BASE_URL + "/book/delete/") -> {
                BookResource bookResource = new BookResource();
                response = bookResource.deleteBook(requestEvent);
            }
            case Constant.BASE_URL + "/genre/getList/" -> {
                GenreResource genreResource = new GenreResource();
                response = genreResource.getList(requestEvent);
            }
            default -> {
                response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.NOT_FOUND)
                    .withBody("存在しないURLです。");
            }
        }
        return response;
    }
}
