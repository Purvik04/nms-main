package org.example.service;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.example.utils.Constants;
import org.example.utils.MotaDataConfigUtil;

public class AuthHandler {

    private final JWTAuth jwtAuth;

    public AuthHandler(Vertx vertx)
    {
        this.jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm(MotaDataConfigUtil.getConfig().getString(Constants.JWT_ENCRYPTION_ALGORITHM, "HS256"))
                        .setBuffer(MotaDataConfigUtil.getConfig().getString(Constants.JWT_SECRET_KEY, "defualt-secret-key"))));  // keep this secret safe
    }

    public String generateToken(JsonObject claims)
    {
        return jwtAuth.generateToken(claims, new JWTOptions().setExpiresInSeconds(MotaDataConfigUtil.getConfig().getInteger(Constants.JWT_EXPIRY_TIME_IN_SECONDS, 3600))); // 1-hour expiry
    }

    public JWTAuth getProvider()
    {
        return jwtAuth;
    }
}


