package com.fastcomments;

import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.pubsub.LiveEventSubscriber;
import com.fastcomments.pubsub.SubscribeToChangesResult;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static org.junit.Assert.*;

/**
 * Uses the actual LiveEventSubscriber.createTesting() and subscribeToChanges()
 * to reproduce the SDK's exact WS creation path.
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
    public void testSubscribeToChangesDirectly() throws Exception {
        String urlId = "ws-flow-" + System.currentTimeMillis();
        String sso = makeSecureSSOToken("flow-user");
        String encodedSso = java.net.URLEncoder.encode(sso, "UTF-8");

        // Get WS params from API
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
        }
        apiClient.dispatcher().executorService().shutdown();

        Log.d(TAG, "WS params: tenantIdWS=" + tenantIdWS + " urlIdWS=" + urlIdWS + " userIdWS=" + userIdWS);

        // Use the actual LiveEventSubscriber.createTesting() — same as SDK testMode
        LiveEventSubscriber subscriber = LiveEventSubscriber.createTesting();

        CommentWidgetConfig config = new CommentWidgetConfig(testTenantId, urlId);

        CountDownLatch failLatch = new CountDownLatch(1);
        long start = System.currentTimeMillis();
        final long[] diedAt = {0};

        subscriber.setOnConnectionStatusChange((connected, lastEventTime) -> {
            double elapsed = (System.currentTimeMillis() - start) / 1000.0;
            Log.d(TAG, String.format("[%.1fs] Connection status: connected=%b", elapsed, connected));
            if (!connected) {
                diedAt[0] = System.currentTimeMillis() - start;
                failLatch.countDown();
            }
        });

        SubscribeToChangesResult result = subscriber.subscribeToChanges(
                config,
                tenantIdWS,
                urlId,
                urlIdWS,
                userIdWS,
                null, // no visibility check
                event -> {
                    double elapsed = (System.currentTimeMillis() - start) / 1000.0;
                    Log.d(TAG, String.format("[%.1fs] Event: type=%s", elapsed, event.getType()));
                }
        );

        assertNotNull("subscribeToChanges should return a result", result);
        Log.d(TAG, "subscribeToChanges returned, waiting 30s...");

        boolean died = failLatch.await(30, TimeUnit.SECONDS);

        if (result != null) {
            result.close();
        }

        if (died) {
            Log.d(TAG, "RESULT: WS died after " + diedAt[0] + "ms using LiveEventSubscriber.subscribeToChanges");
            fail("WS died after " + diedAt[0] + "ms");
        } else {
            Log.d(TAG, "RESULT: WS survived 30s using LiveEventSubscriber.subscribeToChanges!");
        }
    }
}
