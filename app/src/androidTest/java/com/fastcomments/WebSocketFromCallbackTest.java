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
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.*;
import static org.junit.Assert.*;

/**
 * Calls subscribeToChanges from WITHIN an OkHttp async callback,
 * mimicking the exact SDK call path.
 */
@RunWith(AndroidJUnit4.class)
public class WebSocketFromCallbackTest extends UITestBase {

    private static final String TAG = "WsFromCb";

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        createTestTenant("android-ws-callback@fctest.com");
    }

    @Test
    public void testWsCreatedInsideAsyncCallback() throws Exception {
        String urlId = "ws-cb-" + System.currentTimeMillis();
        String sso = makeSecureSSOToken("cb-user");
        String encodedSso = java.net.URLEncoder.encode(sso, "UTF-8");

        // Use the SDK's default API client (same as FastCommentsSDK uses)
        OkHttpClient apiClient = new OkHttpClient.Builder().build();

        CountDownLatch wsLatch = new CountDownLatch(1);
        AtomicLong diedAt = new AtomicLong(0);
        AtomicReference<SubscribeToChangesResult> resultRef = new AtomicReference<>();
        long start = System.currentTimeMillis();

        // Make async API call, then create WS from within the callback
        Request apiReq = new Request.Builder()
                .url("https://fastcomments.com/comments/" + testTenantId
                        + "/?urlId=" + urlId + "&sso=" + encodedSso
                        + "&direction=NF&count=30&asTree=true")
                .build();

        apiClient.newCall(apiReq).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "API call failed: " + e.getMessage());
                wsLatch.countDown();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                response.close();
                try {
                    JSONObject json = new JSONObject(body);
                    String tenantIdWS = json.optString("tenantIdWS", "");
                    String urlIdWS = json.optString("urlIdWS", "");
                    String userIdWS = json.optString("userIdWS", "");
                    Log.d(TAG, "API response on thread: " + Thread.currentThread().getName());
                    Log.d(TAG, "WS params: " + tenantIdWS + " / " + urlIdWS + " / " + userIdWS);

                    // Create WS from within THIS callback (OkHttp dispatcher thread)
                    LiveEventSubscriber subscriber = LiveEventSubscriber.createTesting();
                    CommentWidgetConfig config = new CommentWidgetConfig(testTenantId, urlId);

                    subscriber.setOnConnectionStatusChange((connected, lastEventTime) -> {
                        double elapsed = (System.currentTimeMillis() - start) / 1000.0;
                        Log.d(TAG, String.format("[%.1fs] connected=%b", elapsed, connected));
                        if (!connected) {
                            diedAt.set(System.currentTimeMillis() - start);
                            wsLatch.countDown();
                        }
                    });

                    SubscribeToChangesResult result = subscriber.subscribeToChanges(
                            config, tenantIdWS, urlId, urlIdWS, userIdWS,
                            null, event -> {
                                double elapsed = (System.currentTimeMillis() - start) / 1000.0;
                                Log.d(TAG, String.format("[%.1fs] Event: %s", elapsed, event.getType()));
                            });
                    resultRef.set(result);
                    Log.d(TAG, "subscribeToChanges returned from callback thread");
                } catch (Exception e) {
                    Log.e(TAG, "Error in callback: " + e.getMessage());
                    wsLatch.countDown();
                }
            }
        });

        boolean died = wsLatch.await(30, TimeUnit.SECONDS);
        if (resultRef.get() != null) resultRef.get().close();
        apiClient.dispatcher().executorService().shutdown();

        if (died && diedAt.get() > 0) {
            Log.d(TAG, "RESULT: WS died after " + diedAt.get() + "ms when created from async callback");
            fail("WS died after " + diedAt.get() + "ms");
        } else {
            Log.d(TAG, "RESULT: WS survived 30s when created from async callback!");
        }
    }
}
