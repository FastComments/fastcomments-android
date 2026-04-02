package com.fastcomments;

import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import static org.junit.Assert.*;

/**
 * Test whether concurrent HTTP API calls kill the WebSocket connection.
 */
@RunWith(AndroidJUnit4.class)
public class WebSocketConcurrentTest {

    private static final String TAG = "WsConcTest";

    @Test
    public void testWsSurvivesConcurrentHttpCalls() throws Exception {
        String wsUrl = "wss://ws.fastcomments.com/sub?urlId=demo%3Ahttps%3A%2F%2Ffastcomments.com%2F&userIdWS=conctest&tenantIdWS=demo";
        String apiUrl = "https://fastcomments.com/comments/demo/?urlId=https%3A%2F%2Ffastcomments.com%2F&direction=NF&count=5";

        // WS client with its own dispatcher and pool (same as SDK's LiveEventSubscriber)
        OkHttpClient wsClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.SECONDS)
                .pingInterval(5, TimeUnit.SECONDS)
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .dispatcher(new Dispatcher())
                .build();

        // API client (same as SDK's PublicApi default)
        OkHttpClient apiClient = new OkHttpClient.Builder().build();

        long[] diedAt = {0};
        long start = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(1);

        // Open WebSocket
        Log.d(TAG, "Opening WebSocket...");
        wsClient.newWebSocket(new Request.Builder().url(wsUrl).build(), new WebSocketListener() {
            public void onOpen(WebSocket ws, Response r) {
                Log.d(TAG, "WS OPEN");
            }
            public void onMessage(WebSocket ws, String t) {
                double elapsed = (System.currentTimeMillis() - start) / 1000.0;
                Log.d(TAG, String.format("[%.1fs] WS MSG: %s", elapsed, t.substring(0, Math.min(60, t.length()))));
            }
            public void onFailure(WebSocket ws, Throwable t, Response r) {
                diedAt[0] = System.currentTimeMillis() - start;
                Log.d(TAG, "WS DIED after " + diedAt[0] + "ms: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                latch.countDown();
            }
        });

        // Simultaneously make HTTP API calls every 2 seconds
        Thread apiThread = new Thread(() -> {
            for (int i = 0; i < 15; i++) {
                try {
                    Request req = new Request.Builder().url(apiUrl).build();
                    try (Response resp = apiClient.newCall(req).execute()) {
                        double elapsed = (System.currentTimeMillis() - start) / 1000.0;
                        Log.d(TAG, String.format("[%.1fs] API call %d: %d", elapsed, i + 1, resp.code()));
                    }
                    Thread.sleep(2000);
                } catch (Exception e) {
                    Log.d(TAG, "API call failed: " + e.getMessage());
                }
            }
        });
        apiThread.start();

        boolean died = latch.await(35, TimeUnit.SECONDS);
        apiThread.interrupt();
        wsClient.dispatcher().executorService().shutdown();
        apiClient.dispatcher().executorService().shutdown();

        if (died) {
            Log.d(TAG, "RESULT: WS died after " + diedAt[0] + "ms while API calls were active");
            fail("WebSocket died after " + diedAt[0] + "ms during concurrent API calls");
        } else {
            Log.d(TAG, "RESULT: WS survived 35s with concurrent API calls!");
        }
    }
}
