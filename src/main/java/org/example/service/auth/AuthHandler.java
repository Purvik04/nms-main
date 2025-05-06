package org.example.service.auth;

import io.vertx.core.http.Cookie;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.RoutingContext;
import org.example.BootStrap;
import org.example.utils.Constants;
import org.example.utils.MotaDataConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AuthHandler is responsible for managing authentication operations using JWT.
 * It provides login and token refresh functionality, handling secure token generation and validation.
 */
public class AuthHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthHandler.class);

    // Config key constants
    private static final String JWT_SECRET_KEY = "jwt.secret.key";
    private static final String JWT_ENCRYPTION_ALGORITHM = "jwt.encryption.algorithm";
    private static final String JWT_EXPIRY_TIME_IN_SECONDS = "jwtExpiryTimeInSeconds";

    // Refresh token expiry duration (7 days)
    private static final int JWT_EXPIRY_REFRESH_TIME_IN_SECONDS = 604800;

    // Request field constants
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";

    // Token key names
    private static final String ACCESS_TOKEN = "access_token";
    private static final String REFRESH_TOKEN = "refresh_token";

    // Error messages
    private static final String MESSAGE_MISSING_OR_INVALID_CREDENTIALS = "Missing/Invalid credentials";
    private static final String MESSAGE_INVALID_TOKEN = "Invalid token";

    // JWT provider instance
    private final JWTAuth jwtAuth;

    /**
     * Initializes the AuthHandler by configuring the JWTAuth provider using secret and algorithm from config.
     */
    public AuthHandler()
    {
        this.jwtAuth = JWTAuth.create(BootStrap.getVertx(), new JWTAuthOptions().addPubSecKey(new PubSecKeyOptions()
                .setAlgorithm(MotaDataConfigUtil.getConfig()
                        .getString(JWT_ENCRYPTION_ALGORITHM, Constants.JWT_ENCRYPTION_ALGORITHM))  // default algorithm fallback
                .setBuffer(MotaDataConfigUtil.getConfig()
                        .getString(JWT_SECRET_KEY, Constants.JWT_SECRET_KEY)))); // default secret fallback
    }

    /**
     * Generates a short-lived access token with provided claims.
     *
     * @param claims JsonObject containing user claims like username
     * @return JWT access token
     */
    private String generateAccessToken(JsonObject claims)
    {
        return jwtAuth.generateToken(claims, new JWTOptions()
                .setExpiresInSeconds(MotaDataConfigUtil.getConfig()
                        .getInteger(JWT_EXPIRY_TIME_IN_SECONDS , Constants.JWT_EXPIRY_TIME_IN_SECONDS)));
    }

    /**
     * Generates a longer-lived refresh token with provided claims.
     *
     * @param claims JsonObject containing user claims
     * @return JWT refresh token
     */
    private String generateRefreshToken(JsonObject claims)
    {
        return jwtAuth.generateToken(claims, new JWTOptions()
                .setExpiresInSeconds(JWT_EXPIRY_REFRESH_TIME_IN_SECONDS));
    }

    /**
     * Provides the underlying JWTAuth instance.
     *
     * @return JWTAuth provider
     */
    public JWTAuth getProvider()
    {
        return jwtAuth;
    }

    /**
     * Handles user login using username and password from request body.
     * On successful authentication, issues access and refresh tokens.
     *
     * @param context RoutingContext containing the HTTP request and response
     */
    public void handleLogin(RoutingContext context)
    {
        context.request().body().onSuccess(buffer ->
        {
            try
            {
                // Ensure request body is not empty
                if (buffer == null || buffer.length() == 0)
                {
                    context.response()
                            .setStatusCode(Constants.SC_400)
                            .end(new JsonObject()
                                    .put(Constants.SUCCESS, false)
                                    .put(Constants.MESSAGE, Constants.MESSAGE_BODY_REQUIRED).encode());
                    return;
                }

                var body = buffer.toJsonObject();

                // Check for presence of required fields
                if(body.containsKey(USERNAME) && body.containsKey(PASSWORD)
                        && body.getValue(USERNAME) != null && body.getValue(PASSWORD) != null)
                {
                    // Hardcoded authentication check (placeholder for DB verification)
                    if (USERNAME.equals(body.getString(USERNAME)) && PASSWORD.equals(body.getString(PASSWORD)))
                    {
                        // Create claims and generate tokens
                        var claims = new JsonObject().put(USERNAME, body.getString(USERNAME));

                        context.response()
                                .setStatusCode(Constants.SC_200)
                                .addCookie(Cookie.cookie(REFRESH_TOKEN, generateRefreshToken(claims))
                                        .setHttpOnly(Constants.TRUE)
                                        .setSecure(Constants.TRUE)
                                        .setPath(Constants.PATH_SEPARATOR)
                                        .setMaxAge(JWT_EXPIRY_REFRESH_TIME_IN_SECONDS)) // 7-day expiry
                                .end(new JsonObject()
                                        .put(Constants.SUCCESS, Constants.TRUE)
                                        .put(ACCESS_TOKEN, generateAccessToken(claims))
                                        .encode());
                    }
                    else
                    {
                        // Invalid credentials
                        context.response()
                                .setStatusCode(Constants.SC_401)
                                .end(new JsonObject()
                                        .put(Constants.SUCCESS, Constants.FALSE)
                                        .put(Constants.MESSAGE, MESSAGE_MISSING_OR_INVALID_CREDENTIALS)
                                        .encode());
                    }
                }
                else
                {
                    // Missing fields in request
                    context.response()
                            .setStatusCode(Constants.SC_401)
                            .end(new JsonObject()
                                    .put(Constants.SUCCESS, Constants.FALSE)
                                    .put(Constants.MESSAGE, MESSAGE_MISSING_OR_INVALID_CREDENTIALS)
                                    .encode());
                }
            }
            catch (Exception exception)
            {
                LOGGER.error(exception.getMessage());

                // Internal server error
                context.response()
                        .setStatusCode(Constants.SC_500)
                        .end(exception.getMessage());
            }
        }).onFailure(err ->
                // Failed to read request body
                context.response()
                        .setStatusCode(Constants.SC_500)
                        .end(new JsonObject()
                                .put(Constants.SUCCESS, Constants.FALSE)
                                .put(Constants.MESSAGE, err.getMessage()).encode())
        );
    }

    /**
     * Handles refresh token validation. If valid, generates a new access token.
     *
     * @param context RoutingContext containing the HTTP request and response
     */
    public void handleRefresh(RoutingContext context)
    {
        try
        {
            // Get refresh token from cookie
            var cookie = context.request().getCookie(REFRESH_TOKEN);

            if (cookie == null)
            {
                // No token found
                context.response()
                        .setStatusCode(Constants.SC_401)
                        .end(new JsonObject()
                                .put(Constants.SUCCESS, false)
                                .put(Constants.MESSAGE,MESSAGE_INVALID_TOKEN).encode());
                return;
            }

            // Validate token using JWTAuth
            jwtAuth.authenticate(new TokenCredentials(cookie.getValue()))
                    .onSuccess(user ->
                            // Send new access token if validation succeeds
                            context.response()
                                    .setStatusCode(Constants.SC_200)
                                    .end(new JsonObject()
                                            .put(Constants.SUCCESS, Constants.TRUE)
                                            .put(ACCESS_TOKEN, generateAccessToken(user.principal()))
                                            .encode())
                    )
                    .onFailure(err ->
                            // Invalid token
                            context.response()
                                    .setStatusCode(Constants.SC_401)
                                    .end(new JsonObject()
                                            .put(Constants.SUCCESS, Constants.FALSE)
                                            .put(Constants.MESSAGE, MESSAGE_INVALID_TOKEN)
                                            .encode())
                    );
        }
        catch (Exception exception)
        {
            LOGGER.error(exception.getMessage());

            // Internal server error
            context.response()
                    .setStatusCode(Constants.SC_500)
                    .end(exception.getMessage());
        }
    }
}
