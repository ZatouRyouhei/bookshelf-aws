package com.example.utils;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.Constant;

public class JwtExtractor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * APIGatewayProxyRequestEventからAuthorizationヘッダーのJWTを取得します。
     * @param requestEvent APIGatewayProxyRequestEventオブジェクト
     * @return 抽出されたJWT文字列。存在しない場合、またはBearer形式でない場合は空文字を返します。
     */
    public String extractJwtFromHeaders(APIGatewayProxyRequestEvent requestEvent) {
        try {
                // リクエストヘッダーのJWTを取得
                String requestJwt = "";
                if (requestEvent == null || requestEvent.getHeaders() == null) {
                    // リクエストヘッダーがセットされていない場合は空を返す
                    return "";
                }
                Map<String, String> headers = requestEvent.getHeaders();
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(AUTHORIZATION_HEADER)) {
                        String authorizationHeaderValue = entry.getValue();
                        if (authorizationHeaderValue != null && authorizationHeaderValue.startsWith(BEARER_PREFIX)) {
                            // "Bearer " の部分を取り除いてJWTを抽出
                            requestJwt = authorizationHeaderValue.substring(BEARER_PREFIX.length()).trim();
                        }
                    }
                }
                if ("".equals(requestJwt)) {
                    // ヘッダーからJWTを取得できなかった場合は空を返す
                    return "";
                }
                Algorithm algorithm = Algorithm.HMAC256(Constant.SECRET_KEY);
                JWTVerifier verifier = JWT.require(algorithm).build();
                DecodedJWT jwt = verifier.verify(requestJwt);
                System.out.println("JWT認証成功");
                System.out.println("ID：" + jwt.getClaim("id"));
                System.out.println("名前：" + jwt.getClaim("name"));
                System.out.println("権限：" + jwt.getClaim("roleName"));
                return requestJwt;
            } catch (JWTVerificationException e) {
                System.out.println("JWT認証エラー：" + e.getMessage());
                // 検証失敗（空文字を返す）
                return "";
            }
    }
}
