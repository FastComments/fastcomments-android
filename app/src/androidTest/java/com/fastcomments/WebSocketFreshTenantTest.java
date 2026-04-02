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
public class WebSocketFreshTenantTest {
    @Test
    public void testFreshTenant() throws Exception {
        String url = "wss://ws.fastcomments.com/sub?urlId=UVRuB7NzirIM%3Aws-test-page&userIdWS=BBe1etDuV1MH2bpymUN2u&tenantIdWS=UVRuB7NzirIM";
        Log.d("WsDebug", "Fresh tenant URL: " + url);
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder result = new StringBuilder();

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.SECONDS)
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build();

        client.newWebSocket(new Request.Builder().url(url).build(), new WebSocketListener() {
            public void onOpen(WebSocket ws, Response r) {
                Log.d("WsDebug", "OPEN fresh: " + r.code() + " " + r.protocol());
                result.append("OPEN;");
            }
            public void onMessage(WebSocket ws, String t) {
                Log.d("WsDebug", "MSG fresh: " + t.substring(0, Math.min(80, t.length())));
                result.append("MSG;");
                latch.countDown();
            }
            public void onFailure(WebSocket ws, Throwable t, Response r) {
                Log.e("WsDebug", "FAIL fresh: " + t.getClass().getName() + ": " + t.getMessage());
                if (r != null) Log.e("WsDebug", "  resp: " + r.code());
                else Log.e("WsDebug", "  resp: null");
                result.append("FAIL:" + t.getClass().getSimpleName() + ";");
                latch.countDown();
            }
        });

        boolean got = latch.await(15, TimeUnit.SECONDS);
        client.dispatcher().executorService().shutdown();
        String r = result.toString();
        Log.d("WsDebug", "Fresh result: " + r + " latch=" + got);
        assertFalse("Should not fail: " + r, r.contains("FAIL"));
        assertTrue("Should have opened", r.contains("OPEN"));
    }
}
