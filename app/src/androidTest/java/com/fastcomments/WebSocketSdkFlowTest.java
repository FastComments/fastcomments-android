package com.fastcomments;

import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import static org.junit.Assert.*;

/**
 * Mimics the exact SDK flow: load comments via API, then open WS from within
 * the callback, using real WS params from the response.
 */
@RunWith(AndroidJUnit4.class)
public class WebSocketSdkFlowTest extends UITestBase {

    private static final String TAG = "WsSdkFlow";

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        createTestTenant();
    }

    @Test
    public void testWsSurvivesAfterApiCall() throws Exception {
        String urlId = "ws-flow-" + System.currentTimeMillis();
        String sso = makeSecureSSOToken("flow-user");
        String encodedSso = java.net.URLEncoder.encode(sso, "UTF-8");

        // Step 1: Make the same API call the SDK makes to get WS params
        OkHttpClient apiClient = new OkHttpClient.Builder().build();
        Request apiReq = new Request.Builder()
                .url("https://fastcomments.com/comments/" + testTenantId
                        + "/?urlId=" + urlId + "&sso=" + encodedSso
                        + "&direction=NF&count=30&asTree=true")
                .build();

        String tenantIdWS, urlIdWS, userIdWS;
        try (Response resp = apiClient.newCall(apiReq).execute()) {
            String body = resp.body().string();
            JSONObject json = new JSONObject(body);
            tenantIdWS = json.optString("tenantIdWS", "");
            urlIdWS = json.optString("urlIdWS", "");
            userIdWS = json.optString("userIdWS", "");
            Log.d(TAG, "API response: tenantIdWS=" + tenantIdWS + " urlIdWS=" + urlIdWS + " userIdWS=" + userIdWS);
        }

        assertTrue("Should have WS params", !tenantIdWS.isEmpty() && !urlIdWS.isEmpty() && !userIdWS.isEmpty());

        // Step 2: Open WS using the same client config as LiveEventSubscriber
        String wsUrl = "wss://ws.fastcomments.com/sub?urlId=" + urlIdWS
                + "&userIdWS=" + userIdWS + "&tenantIdWS=" + tenantIdWS;
        Log.d(TAG, "WS URL: " + wsUrl);

        OkHttpClient wsClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.SECONDS)
                .pingInterval(5, TimeUnit.SECONDS)
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .dispatcher(new Dispatcher())
                .build();

        long[] diedAt = {0};
        long start = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(1);

        wsClient.newWebSocket(new Request.Builder().url(wsUrl).build(), new WebSocketListener() {
            public void onOpen(WebSocket ws, Response r) {
                Log.d(TAG, "WS OPEN: " + r.code() + " " + r.protocol());
            }
            public void onMessage(WebSocket ws, String t) {
                double elapsed = (System.currentTimeMillis() - start) / 1000.0;
                Log.d(TAG, String.format("[%.1fs] MSG: %s", elapsed, t.substring(0, Math.min(80, t.length()))));
            }
            public void onFailure(WebSocket ws, Throwable t, Response r) {
                diedAt[0] = System.currentTimeMillis() - start;
                Log.e(TAG, "WS DIED after " + diedAt[0] + "ms: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                latch.countDown();
            }
        });

        // Step 3: Make more API calls concurrently (like the SDK does for presence polling)
        Thread apiThread = new Thread(() -> {
            for (int i = 0; i < 8; i++) {
                try {
                    Request req = new Request.Builder()
                            .url("https://fastcomments.com/comments/" + testTenantId
                                    + "/?urlId=" + urlId + "&sso=" + encodedSso
                                    + "&direction=NF&count=5")
                            .build();
                    try (Response resp = apiClient.newCall(req).execute()) {
                        Log.d(TAG, "API call " + (i+1) + ": " + resp.code());
                    }
                    Thread.sleep(3000);
                } catch (Exception e) {
                    Log.d(TAG, "API error: " + e.getMessage());
                }
            }
        });
        apiThread.start();

        boolean died = latch.await(30, TimeUnit.SECONDS);
        apiThread.interrupt();
        wsClient.dispatcher().executorService().shutdown();
        apiClient.dispatcher().executorService().shutdown();

        if (died) {
            Log.d(TAG, "RESULT: WS died after " + diedAt[0] + "ms");
            fail("WS died after " + diedAt[0] + "ms — same bug as SDK");
        } else {
            Log.d(TAG, "RESULT: WS survived 30s with real tenant + concurrent API calls");
        }
    }
}
