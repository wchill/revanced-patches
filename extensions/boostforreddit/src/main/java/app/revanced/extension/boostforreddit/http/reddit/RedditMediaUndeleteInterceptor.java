package app.revanced.extension.boostforreddit.http.reddit;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.Optional;

import app.revanced.extension.boostforreddit.http.HttpUtils;
import app.revanced.extension.boostforreddit.http.wayback.WaybackMachine;
import app.revanced.extension.boostforreddit.http.wayback.WaybackResponse;
import okhttp3.Interceptor;
import okhttp3.Response;
import okhttp3.Request;


public class RedditMediaUndeleteInterceptor implements Interceptor {

    @NotNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String host = request.url().host();

        String contentUrl;
        if (host.contains("i.redd.it") || host.contains("preview.redd.it")) {
            contentUrl = request.url().toString();
        } else if (request.url().toString().contains("reddit.com/gallery/")) {
            return chain.proceed(request);
        } else {
            return chain.proceed(request);
        }

        try {
            Optional<WaybackResponse> cachedResponse = WaybackMachine.fetchUrlFromCache(request, contentUrl);
            if (cachedResponse.isPresent()) {
                return cachedResponse.get().getResponse();
            }

            Response response = chain.proceed(request);
            if (response.code() != HttpURLConnection.HTTP_NOT_FOUND) {
                return response;
            }
            response.close();

            WaybackResponse waybackResponse = WaybackMachine.getFromWayback(request, contentUrl);
            return waybackResponse.getResponse();
        } catch (SocketTimeoutException e) {
            return HttpUtils.create503(request);
        }
    }
}
