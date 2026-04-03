package com.fastcomments;

import android.content.Intent;
import android.os.Bundle;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fastcomments.core.sso.FastCommentsSSO;
import com.fastcomments.core.sso.SecureSSOUserData;
import com.fastcomments.model.APIError;
import com.fastcomments.model.CreateFeedPostParams;
import com.fastcomments.model.FeedPost;
import com.fastcomments.sdk.FCCallback;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Base class for UI tests.
 * Handles test tenant creation/teardown, SSO token generation,
 * activity launching, comment seeding, and sync client initialization.
 *
 * Port of iOS UITestBase.swift.
 */
public class UITestBase {

    private static final String HOST = "https://fastcomments.com";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    protected String testTenantId;
    protected String testTenantEmail;
    protected String testTenantApiKey;
    protected SyncClient sync;
    protected ActivityScenario<TestActivity> scenario;
    protected ActivityScenario<TestLiveChatActivity> liveChatScenario;
    protected ActivityScenario<TestFeedActivity> feedScenario;

    private String e2eApiKey;
    private OkHttpClient httpClient;

    // ---- Setup / Teardown ----

    @Before
    public void setUp() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        String syncUrl = args.getString("FC_SYNC_URL", "http://10.0.2.2:9999");
        e2eApiKey = args.getString("E2E_API_KEY", "");

        // Only create SyncClient if a role is explicitly set (dual-emulator tests)
        String role = args.getString("FC_ROLE");
        if (role != null) {
            sync = new SyncClient(syncUrl, role);
        }

        // Cookie jar for tenant signup session
        Map<String, List<Cookie>> cookieStore = new HashMap<>();
        httpClient = new OkHttpClient.Builder()
                .cookieJar(new CookieJar() {
                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.put(url.host(), cookies);
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        List<Cookie> cookies = cookieStore.get(url.host());
                        return cookies != null ? cookies : new ArrayList<>();
                    }
                })
                .followRedirects(true)
                .build();
    }

    /** Called by UserA's setUp to create a fresh test tenant with a fixed email. */
    protected void createTestTenant(String email) throws Exception {
        testTenantEmail = email;

        // Delete any leftover tenant from a previous run
        deleteTenantByEmail(email);

        // 1. Sign up tenant
        String name = email.split("@")[0];
        RequestBody signupBody = new FormBody.Builder()
                .add("username", name)
                .add("email", email)
                .add("companyName", name)
                .add("domains", name + ".example.com")
                .add("packageId", "adv")
                .add("noTracking", "true")
                .build();

        Request signupRequest = new Request.Builder()
                .url(HOST + "/auth/tenant-signup")
                .post(signupBody)
                .build();

        try (Response response = httpClient.newCall(signupRequest).execute()) {
            if (!response.isSuccessful() && response.code() != 302) {
                fail("Tenant signup failed with status: " + response.code());
            }
        }

        // 2. Get tenant ID via e2e test API
        Request tenantRequest = new Request.Builder()
                .url(HOST + "/test-e2e/api/tenant/by-email/" + testTenantEmail
                        + "?API_KEY=" + e2eApiKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(tenantRequest).execute()) {
            String body = response.body().string();
            JSONObject json = new JSONObject(body);
            JSONObject tenant = json.getJSONObject("tenant");
            testTenantId = tenant.getString("_id");
        }
        assertNotNull("Should have tenant ID", testTenantId);

        // 3. Scrape API key from api-secret page using signup session cookies
        Request apiSecretRequest = new Request.Builder()
                .url(HOST + "/auth/my-account/api-secret")
                .get()
                .build();

        try (Response response = httpClient.newCall(apiSecretRequest).execute()) {
            String html = response.body().string();
            Pattern pattern = Pattern.compile("value=\"([A-Z0-9]+)\"");
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                testTenantApiKey = matcher.group(1);
            }
        }
        assertNotNull("Should have API key", testTenantApiKey);
    }

    @After
    public void tearDown() throws Exception {
        if (scenario != null) {
            scenario.close();
        }
        if (liveChatScenario != null) {
            liveChatScenario.close();
        }
        if (feedScenario != null) {
            feedScenario.close();
        }

        if (testTenantEmail != null && e2eApiKey != null && !e2eApiKey.isEmpty()) {
            try {
                Request deleteRequest = new Request.Builder()
                        .url(HOST + "/test-e2e/api/tenant/by-email/" + testTenantEmail
                                + "?API_KEY=" + e2eApiKey)
                        .delete()
                        .build();
                try (Response ignored = httpClient.newCall(deleteRequest).execute()) {
                    // Best effort
                }
            } catch (Exception ignored) {}
        }
    }

    /** Best-effort delete of a test tenant by email via the e2e API. */
    private void deleteTenantByEmail(String email) {
        if (e2eApiKey == null || e2eApiKey.isEmpty()) return;
        try {
            Request request = new Request.Builder()
                    .url(HOST + "/test-e2e/api/tenant/by-email/" + email + "?API_KEY=" + e2eApiKey)
                    .delete()
                    .build();
            try (Response ignored = httpClient.newCall(request).execute()) {
                // Best effort — tenant may not exist yet
            }
        } catch (Exception ignored) {}
    }

    // ---- SSO ----

    protected String makeSecureSSOToken(String userId) {
        return makeSecureSSOToken(userId, false);
    }

    protected String makeSecureSSOToken(String userId, boolean isAdmin) {
        try {
            SecureSSOUserData userData = new SecureSSOUserData(
                    userId,
                    "tester-" + userId.substring(0, Math.min(8, userId.length())) + "@fctest.com",
                    "Tester " + userId.substring(0, Math.min(6, userId.length())),
                    ""
            );
            if (isAdmin) {
                userData.isAdmin = true;
            }
            FastCommentsSSO sso = FastCommentsSSO.createSecure(testTenantApiKey, userData);
            return sso.prepareToSend();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSO token", e);
        }
    }

    // ---- Activity Launch ----

    protected void launchActivity(String urlId, String ssoToken) {
        if (scenario != null) {
            scenario.close();
        }
        Intent intent = new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                TestActivity.class
        );
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("tenantId", testTenantId);
        intent.putExtra("urlId", urlId);
        intent.putExtra("sso", ssoToken);
        scenario = ActivityScenario.launch(intent);
    }

    protected void launchLiveChatActivity(String urlId, String ssoToken) {
        if (liveChatScenario != null) {
            liveChatScenario.close();
        }
        Intent intent = new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                TestLiveChatActivity.class
        );
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("tenantId", testTenantId);
        intent.putExtra("urlId", urlId);
        intent.putExtra("sso", ssoToken);
        liveChatScenario = ActivityScenario.launch(intent);
    }

    protected void launchFeedActivity(String urlId, String ssoToken) {
        if (feedScenario != null) {
            feedScenario.close();
        }
        Intent intent = new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                TestFeedActivity.class
        );
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("tenantId", testTenantId);
        intent.putExtra("urlId", urlId);
        intent.putExtra("sso", ssoToken);
        feedScenario = ActivityScenario.launch(intent);
    }

    // ---- Feed Operations ----

    /**
     * Create a feed post via the typed SDK. Blocks until complete.
     * Must be called after launchFeedActivity().
     */
    protected FeedPost createFeedPostViaSDK(String title, String contentHTML) {
        AtomicReference<FeedPost> resultRef = new AtomicReference<>();
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        feedScenario.onActivity(activity -> {
            CreateFeedPostParams params = new CreateFeedPostParams();
            params.setTitle(title);
            params.setContentHTML(contentHTML);

            activity.feedSDK.createPost(params, new FCCallback<FeedPost>() {
                @Override
                public boolean onFailure(APIError error) {
                    errorRef.set(error);
                    latch.countDown();
                    return FCCallback.CONSUME;
                }

                @Override
                public boolean onSuccess(FeedPost feedPost) {
                    resultRef.set(feedPost);
                    latch.countDown();
                    return FCCallback.CONSUME;
                }
            });
        });

        try {
            assertTrue("createFeedPostViaSDK timed out", latch.await(15, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new RuntimeException("createFeedPostViaSDK interrupted", e);
        }
        if (errorRef.get() != null) {
            fail("createFeedPostViaSDK failed: " + errorRef.get().getReason());
        }
        return resultRef.get();
    }

    // ---- Comment Operations ----

    /**
     * Seed a comment via the REST API. Returns the comment ID from the response.
     */
    protected String seedComment(String urlId, String text, String ssoToken) {
        return seedComment(urlId, text, ssoToken, null);
    }

    /**
     * Seed a comment via the REST API with optional parentId. Returns the comment ID.
     */
    protected String seedComment(String urlId, String text, String ssoToken, String parentId) {
        try {
            String broadcastId = UUID.randomUUID().toString();
            String url = HOST + "/comments/" + testTenantId + "/"
                    + "?broadcastId=" + broadcastId
                    + "&urlId=" + urlId
                    + "&sso=" + java.net.URLEncoder.encode(ssoToken, "UTF-8");

            JSONObject body = new JSONObject();
            body.put("comment", text);
            body.put("commenterName", "Tester");
            body.put("commenterEmail", "tester@fctest.com");
            body.put("url", urlId);
            body.put("urlId", urlId);
            if (parentId != null) {
                body.put("parentId", parentId);
            }

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body.toString(), JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    fail("seedComment failed: " + response.code());
                    return null;
                }
                String responseBody = response.body() != null ? response.body().string() : "";
                JSONObject json = new JSONObject(responseBody);
                // Try to extract comment ID from response
                if (json.has("comment")) {
                    JSONObject comment = json.getJSONObject("comment");
                    return comment.optString("_id", comment.optString("id", null));
                }
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException("seedComment failed", e);
        }
    }

    /** Fetch the latest comment ID for a urlId via admin API. Retries up to 3 times. */
    protected String fetchLatestCommentId(String urlId) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                Request request = new Request.Builder()
                        .url(HOST + "/api/v1/comments?tenantId=" + testTenantId
                                + "&urlId=" + urlId + "&limit=1")
                        .addHeader("x-api-key", testTenantApiKey)
                        .get()
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "";
                    JSONObject json = new JSONObject(body);
                    JSONArray comments = json.getJSONArray("comments");
                    if (comments.length() > 0) {
                        JSONObject first = comments.getJSONObject(0);
                        String id = first.optString("_id", first.optString("id", null));
                        if (id != null) return id;
                    }
                }
            } catch (Exception e) {
                if (attempt == 3) {
                    fail("fetchLatestCommentId failed after 3 attempts: " + e.getMessage());
                }
            }

            if (attempt < 3) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
        return null;
    }

    // ---- Admin Operations ----

    /** Update a comment via the admin API. Uses PATCH with x-api-key header. */
    protected boolean adminUpdateComment(String commentId, JSONObject params) {
        try {
            Request request = new Request.Builder()
                    .url(HOST + "/api/v1/comments/" + commentId + "?tenantId=" + testTenantId)
                    .addHeader("x-api-key", testTenantApiKey)
                    .addHeader("Content-Type", "application/json")
                    .patch(RequestBody.create(params.toString(), JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "";
                    fail("adminUpdateComment failed: status=" + response.code() + " body=" + body.substring(0, Math.min(200, body.length())));
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            fail("adminUpdateComment failed: " + e.getMessage());
            return false;
        }
    }

    /** Pin a comment via the public API (requires admin SSO token). */
    protected void pinComment(String commentId, String adminSsoToken) {
        try {
            String broadcastId = UUID.randomUUID().toString();
            String encodedSso = java.net.URLEncoder.encode(adminSsoToken, "UTF-8");
            Request request = new Request.Builder()
                    .url(HOST + "/comments/" + testTenantId + "/" + commentId + "/pin"
                            + "?broadcastId=" + broadcastId + "&sso=" + encodedSso)
                    .post(RequestBody.create("", null))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                android.util.Log.d("UITestBase", "pinComment response: " + response.code() + " body=" + body.substring(0, Math.min(200, body.length())));
                if (!response.isSuccessful()) {
                    fail("pinComment failed: status=" + response.code() + " body=" + body.substring(0, Math.min(200, body.length())));
                }
            }
        } catch (Exception e) {
            fail("pinComment failed: " + e.getMessage());
        }
    }

    /** Lock a comment via the public API (requires admin SSO token). */
    protected void lockComment(String commentId, String adminSsoToken) {
        try {
            String broadcastId = UUID.randomUUID().toString();
            String encodedSso = java.net.URLEncoder.encode(adminSsoToken, "UTF-8");
            Request request = new Request.Builder()
                    .url(HOST + "/comments/" + testTenantId + "/" + commentId + "/lock"
                            + "?broadcastId=" + broadcastId + "&sso=" + encodedSso)
                    .post(RequestBody.create("", null))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "";
                    fail("lockComment failed: status=" + response.code() + " body=" + body.substring(0, Math.min(200, body.length())));
                }
            }
        } catch (Exception e) {
            fail("lockComment failed: " + e.getMessage());
        }
    }

    // ---- Polling ----

    /** Poll a condition every 50ms until true or timeout. */
    protected void pollUntil(long timeoutMs, PollCondition condition) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.check()) {
            if (System.currentTimeMillis() > deadline) {
                return;
            }
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
    }

    @FunctionalInterface
    protected interface PollCondition {
        boolean check();
    }
}
