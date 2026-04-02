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

@RunWith(AndroidJUnit4.class)
public class WebSocketExactURLTest {
    /** 
     * Test the exact same URL that the SDK tried to connect to.
     * Uses the same OkHttpClient config as LiveEventSubscriber.
     */
    @Test
    public void testSameURLAsSdk() throws Exception {
        // Same URL that appeared in the SDK log
        String url = "wss://ws.fastcomments.com/sub?urlId=aQN7KeCPkb5a%3Alive-1775103409632&userIdWS=YUMyisRtKIloQAaX2Z0Mc&tenantIdWS=aQN7KeCPkb5a";
        Log.d("WsDebug", "Testing exact SDK URL: " + url);

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder result = new StringBuilder();

        // Exact same config as LiveEventSubscriber
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.SECONDS)
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build();

        client.newWebSocket(new Request.Builder().url(url).build(), new WebSocketListener() {
            public void onOpen(WebSocket ws, Response r) {
                Log.d("WsDebug", "OPEN: " + r.code() + " " + r.protocol());
                result.append("OPEN;");
            }
            public void onMessage(WebSocket ws, String t) {
                Log.d("WsDebug", "MSG: " + t.substring(0, Math.min(80, t.length())));
                result.append("MSG;");
                latch.countDown();
            }
            public void onFailure(WebSocket ws, Throwable t, Response r) {
                Log.e("WsDebug", "FAIL: " + t.getClass().getName() + ": " + t.getMessage());
                result.append("FAIL:" + t.getClass().getSimpleName() + ";");
                latch.countDown();
            }
        });

        boolean got = latch.await(15, TimeUnit.SECONDS);
        client.dispatcher().executorService().shutdown();
        String r = result.toString();
        Log.d("WsDebug", "Result: " + r + " latch=" + got);
        assertFalse("Should not fail: " + r, r.contains("FAIL"));
    }
}
