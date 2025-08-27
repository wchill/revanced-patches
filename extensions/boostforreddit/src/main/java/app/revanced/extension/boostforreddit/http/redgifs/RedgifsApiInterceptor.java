package app.revanced.extension.boostforreddit.http.redgifs;

import com.fasterxml.jackson.databind.JsonNode;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import app.revanced.extension.boostforreddit.http.HttpUtils;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class RedgifsApiInterceptor implements Interceptor {
    private String accessToken;
    private long expiryTime;

    public RedgifsApiInterceptor() {
        accessToken = null;
        expiryTime = 0;
    }

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request request = chain.request();
        if (!request.url().host().equals("api.redgifs.com")) {
            return chain.proceed(request);
        }

        Request modifiedRequest = request.newBuilder()
                .header("User-Agent", "Boost").build();
        // Reference: https://github.com/JeffreyCA/Apollo-ImprovedCustomApi/pull/67/files
        if (modifiedRequest.header("Authorization") != null) {
            return chain.proceed(modifiedRequest);
        }
        if (!isTokenValid()) {
            JsonNode response = HttpUtils.getJson("https://api.redgifs.com/v2/auth/temporary",
                    Headers.of("User-Agent", "Boost"));
            JsonNode tokenNode = response.get("token");
            if (tokenNode != null && !tokenNode.isNull()) {
                expiryTime = System.currentTimeMillis() / 1000 + 82800;
                accessToken = response.get("token").asText();
            }
        }

        modifiedRequest = modifiedRequest.newBuilder()
                .header("Authorization", "Bearer " + accessToken).build();
        return chain.proceed(modifiedRequest);
    }

    private boolean isTokenValid() {
        if (accessToken == null) return false;
        return expiryTime >= System.currentTimeMillis() / 1000;
    }
}
