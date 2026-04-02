package com.fastcomments;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class WebSocketDebugTest {

    private static final String TAG = "WsDebug";

    @Test
    public void testEchoWebSocket() throws Exception {
        testWs("wss://echo.websocket.events", true);
    }

    @Test
    public void testFastCommentsWebSocket() throws Exception {
        // Use the demo tenant so we don't need signup
        String url = "wss://ws.fastcomments.com/sub?urlId=demo%3Ahttps%3A%2F%2Ffastcomments.com%2F&userIdWS=anon&tenantIdWS=demo";
        testWs(url, false);
    }

    @Test
    public void testFastCommentsWebSocket_NoProtocolRestriction() throws Exception {
        String url = "wss://ws.fastcomments.com/sub?urlId=demo%3Ahttps%3A%2F%2Ffastcomments.com%2F&userIdWS=anon&tenantIdWS=demo";
        testWsDefault(url);
    }

    private void testWs(String url, boolean expectEcho) throws Exception {
        Log.d(TAG, "--- Testing (HTTP/1.1): " + url);

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder result = new StringBuilder();

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.SECONDS)
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build();

        Request request = new Request.Builder().url(url).build();

        client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                Log.d(TAG, "OPEN: code=" + response.code() + " proto=" + response.protocol());
                result.append("OPEN;");
                if (expectEcho) ws.send("hello");
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                Log.d(TAG, "MESSAGE: " + text.substring(0, Math.min(100, text.length())));
                result.append("MSG;");
                latch.countDown();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.e(TAG, "FAILURE: " + t.getClass().getName() + ": " + t.getMessage());
                if (response != null) {
                    Log.e(TAG, "  response: " + response.code() + " " + response.message());
                } else {
                    Log.e(TAG, "  response: null");
                }
                result.append("FAIL:" + t.getClass().getSimpleName() + ";");
                latch.countDown();
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                Log.d(TAG, "CLOSING: " + code + " " + reason);
                ws.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                Log.d(TAG, "CLOSED: " + code + " " + reason);
            }
        });

        boolean got = latch.await(15, TimeUnit.SECONDS);
        client.dispatcher().executorService().shutdown();

        String r = result.toString();
        Log.d(TAG, "Result: " + r + " (latch=" + got + ")");

        if (r.contains("FAIL")) {
            fail("WebSocket failed: " + r);
        }
        if (!got) {
            // Connection stayed open but no message — that's ok for FC (only sends on events)
            if (!r.contains("OPEN")) {
                fail("WebSocket never opened: " + r);
            }
            Log.d(TAG, "Connection open, no messages (expected for idle FC connection)");
        }
    }

    private void testWsDefault(String url) throws Exception {
        Log.d(TAG, "--- Testing (default protocols): " + url);

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder result = new StringBuilder();

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build();

        Request request = new Request.Builder().url(url).build();

        client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                Log.d(TAG, "OPEN (default): code=" + response.code() + " proto=" + response.protocol());
                result.append("OPEN;");
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                Log.d(TAG, "MESSAGE (default): " + text.substring(0, Math.min(100, text.length())));
                result.append("MSG;");
                latch.countDown();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.e(TAG, "FAILURE (default): " + t.getClass().getName() + ": " + t.getMessage());
                if (response != null) Log.e(TAG, "  response: " + response.code());
                else Log.e(TAG, "  response: null");
                result.append("FAIL:" + t.getClass().getSimpleName() + ";");
                latch.countDown();
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                Log.d(TAG, "CLOSING (default): " + code);
                ws.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                Log.d(TAG, "CLOSED (default): " + code);
            }
        });

        boolean got = latch.await(15, TimeUnit.SECONDS);
        client.dispatcher().executorService().shutdown();

        String r = result.toString();
        Log.d(TAG, "Result (default): " + r + " (latch=" + got + ")");

        if (r.contains("FAIL")) {
            fail("WebSocket failed (default): " + r);
        }
    }
}
