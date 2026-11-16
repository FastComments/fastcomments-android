package com.fastcomments.testing;

import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * E2E API calls shared by the SDK's integration tests and the app's UI tests. Both source sets compile this directory.
 */
public final class E2eTestApi {

    private static final MediaType JSON = MediaType.get("application/json");

    private E2eTestApi() {}

    /** Saves a tenant-wide widget config (domain and urlId "*") for a test tenant. */
    public static void createCustomConfig(
            OkHttpClient client, String host, String e2eApiKey, String tenantId, JSONObject widgetConfig)
            throws IOException, JSONException {
        JSONObject body = new JSONObject()
                .put("tenantId", tenantId)
                .put("name", "Android Test Config")
                .put("domain", "*")
                .put("urlId", "*")
                .put("config", widgetConfig);
        Request request = new Request.Builder()
                .url(host + "/test-e2e/api/custom-configs?API_KEY=" + e2eApiKey)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("createCustomConfig failed with status " + response.code());
            }
        }
    }
}
