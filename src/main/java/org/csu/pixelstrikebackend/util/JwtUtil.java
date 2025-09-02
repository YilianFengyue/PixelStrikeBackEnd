package org.csu.pixelstrikebackend.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;

import java.util.Date;

public class JwtUtil {
    //7天过期
    private static final long EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000;
    // Token 签名密钥，实际项目中请保存在配置文件中，且不要泄露
    private static final String TOKEN_SECRET = "your-secret-key-change-it";

    /**
     * 生成 Token
     * @param userId 用户ID
     * @return 生成的 JWT Token
     */
    public static String generateToken(Integer userId) {
        try {
            Date date = new Date(System.currentTimeMillis() + EXPIRE_TIME);
            // 使用 HMAC256 算法签名
            Algorithm algorithm = Algorithm.HMAC256(TOKEN_SECRET);
            // 创建 JWT 并设置 Claims
            return JWT.create()
                    // 将 userId 保存到 claim 中
                    .withClaim("userId", userId)
                    // 设置过期时间
                    .withExpiresAt(date)
                    // 签名
                    .sign(algorithm);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 验证 Token 并从中获取 userId
     * @param token 前端传入的 token
     * @return token 验证成功则返回 userId，否则返回 null
     */
    public static Integer verifyTokenAndGetUserId(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(TOKEN_SECRET);
            // 创建一个 JWT Verifier
            JWTVerifier verifier = JWT.require(algorithm)
                    .build();
            // 验证 token
            DecodedJWT jwt = verifier.verify(token);
            // 从 claim 中获取 userId
            return jwt.getClaim("userId").asInt();
        } catch (Exception e) {
            // Token 验证失败（如过期、签名错误等）
            return null;
        }
    }
}
