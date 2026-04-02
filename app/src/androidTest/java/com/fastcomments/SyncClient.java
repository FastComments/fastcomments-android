package com.fastcomments;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * HTTP client for coordinating dual-emulator tests via the sync server.
 * Port of iOS SyncClient.swift.
 *
 * All calls are synchronous (blocking) — intended for use on test threads only.
 */
public class SyncClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String baseUrl;
    private final String role;
    private final OkHttpClient client;

    public SyncClient(String syncUrl, String role) {
        this.baseUrl = syncUrl;
        this.role = role;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)  // /wait can block up to 120s
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /** Signal that this role is ready for a specific round. */
    public void signalReady(String round) {
        Request request = new Request.Builder()
                .url(baseUrl + "/ready?role=" + role + "&round=" + round)
                .post(RequestBody.create("", null))
                .build();
        execute(request);
    }

    /** Block until the specified role signals ready for a round. */
    public void waitFor(String waitRole, String round) {
        waitFor(waitRole, round, 120);
    }

    /** Block until the specified role signals ready for a round, with timeout. */
    public void waitFor(String waitRole, String round, int timeoutSec) {
        Request request = new Request.Builder()
                .url(baseUrl + "/wait?waitFor=" + waitRole + "&round=" + round + "&timeout=" + timeoutSec)
                .get()
                .build();
        execute(request);
    }

    /** Store JSON data for a round. */
    public void postData(String round, JSONObject data) {
        Request request = new Request.Builder()
                .url(baseUrl + "/data?round=" + round)
                .post(RequestBody.create(data.toString(), JSON))
                .build();
        execute(request);
    }

    /** Retrieve JSON data for a round. */
    public JSONObject getData(String round) {
        Request request = new Request.Builder()
                .url(baseUrl + "/data?round=" + round)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";
            return new JSONObject(body);
        } catch (Exception e) {
            throw new RuntimeException("SyncClient.getData failed for round=" + round, e);
        }
    }

    private void execute(Request request) {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Sync request failed: " + response.code() + " " + request.url());
            }
        } catch (IOException e) {
            throw new RuntimeException("SyncClient request failed: " + request.url(), e);
        }
    }
}
