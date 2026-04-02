package com.fastcomments;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.*;

import static org.junit.Assert.*;

/**
 * Tests whether OkHttp protocol pings vs text pings keep connections alive on the emulator.
 */
@RunWith(AndroidJUnit4.class)
public class WebSocketPingTest {

    private static final String TAG = "WsPingTest";
    private static final String URL = "wss://ws.fastcomments.com/sub?urlId=demo%3Ahttps%3A%2F%2Ffastcomments.com%2F&userIdWS=pingtest&tenantIdWS=demo";

    @Test
    public void testProtocolPingsOnly() throws Exception {
        Log.d(TAG, "=== Protocol pings only (pingInterval=5s) ===");
        long[] diedAt = {0};
        long start = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(1);

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.SECONDS)
                .pingInterval(5, TimeUnit.SECONDS)
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build();

        client.newWebSocket(new Request.Builder().url(URL).build(), new WebSocketListener() {
            public void onOpen(WebSocket ws, Response r) {
                Log.d(TAG, "OPEN (protocol pings)");
            }
            public void onMessage(WebSocket ws, String t) {
                Log.d(TAG, "MSG: " + t.substring(0, Math.min(60, t.length())));
            }
            public void onFailure(WebSocket ws, Throwable t, Response r) {
                diedAt[0] = System.currentTimeMillis() - start;
                Log.d(TAG, "DIED after " + diedAt[0] + "ms: " + t.getClass().getSimpleName());
                latch.countDown();
            }
        });

        boolean died = latch.await(35, TimeUnit.SECONDS);
        client.dispatcher().executorService().shutdown();

        if (died) {
            Log.d(TAG, "Protocol pings: connection died after " + diedAt[0] + "ms");
        } else {
            Log.d(TAG, "Protocol pings: connection survived 35s!");
        }
        // Just log, don't assert — we expect this to fail on emulator
    }

    @Test
    public void testTextPingsOnly() throws Exception {
        Log.d(TAG, "=== Text pings only (send 'ping' every 5s) ===");
        long[] diedAt = {0};
        long start = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(1);

        // NO pingInterval — only text pings
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.SECONDS)
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build();

        client.newWebSocket(new Request.Builder().url(URL + "2").build(), new WebSocketListener() {
            Timer timer;
            public void onOpen(WebSocket ws, Response r) {
                Log.d(TAG, "OPEN (text pings)");
                timer = new Timer();
                timer.scheduleAtFixedRate(new TimerTask() {
                    public void run() {
                        try {
                            ws.send("ping");
                            Log.d(TAG, "Sent text 'ping'");
                        } catch (Exception e) {
                            Log.d(TAG, "Text ping failed: " + e.getMessage());
                        }
                    }
                }, 5000, 5000);
            }
            public void onMessage(WebSocket ws, String t) {
                Log.d(TAG, "MSG: " + t.substring(0, Math.min(60, t.length())));
            }
            public void onFailure(WebSocket ws, Throwable t, Response r) {
                diedAt[0] = System.currentTimeMillis() - start;
                Log.d(TAG, "DIED after " + diedAt[0] + "ms: " + t.getClass().getSimpleName());
                if (timer != null) timer.cancel();
                latch.countDown();
            }
        });

        boolean died = latch.await(35, TimeUnit.SECONDS);
        client.dispatcher().executorService().shutdown();

        if (died) {
            Log.d(TAG, "Text pings: connection died after " + diedAt[0] + "ms");
            fail("Text pings did not keep connection alive — died after " + diedAt[0] + "ms");
        } else {
            Log.d(TAG, "Text pings: connection survived 35s!");
        }
    }
}
