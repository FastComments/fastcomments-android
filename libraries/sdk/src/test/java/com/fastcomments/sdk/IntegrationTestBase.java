package com.fastcomments.sdk;

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.core.sso.FastCommentsSSO;
import com.fastcomments.core.sso.SecureSSOUserData;
import com.fastcomments.model.APIEmptyResponse;
import com.fastcomments.model.APIError;
import com.fastcomments.model.PublicComment;
import com.fastcomments.model.SetCommentTextResult;
import com.fastcomments.model.VoteResponse;
import com.fastcomments.model.VoteDeleteResponse;
import com.fastcomments.model.BlockSuccess;
import com.fastcomments.model.UnblockSuccess;
import com.fastcomments.model.ChangeCommentPinStatusResponse;
import com.fastcomments.model.CreateFeedPostParams;
import com.fastcomments.model.FeedPost;
import com.fastcomments.model.GetCommentsResponseWithPresencePublicComment;
import com.fastcomments.model.PublicFeedPostsResponse;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

/**
 * Base class for integration tests that hit the real FastComments API.
 *
 * Follows the same pattern as the iOS IntegrationTestBase:
 * - Each test creates a fresh tenant via signup with an @fctest.com email (no rate limits)
 * - Retrieves its API key (secret) for secure SSO
 * - Cleans up comments and deletes the test tenant in tearDown
 */
public class IntegrationTestBase {

    private static final long DEFAULT_TIMEOUT_MS = 15000;
    private static final long DEFAULT_INTERVAL_MS = 200;

    private final List<String> urlIdsToCleanup = new ArrayList<>();
    private final List<FastCommentsSDK> sdksToCleanup = new ArrayList<>();
    private final List<FastCommentsFeedSDK> feedSdksToCleanup = new ArrayList<>();

    private String testTenantId;
    private String testTenantEmail;
    private String testTenantApiKey;
    private OkHttpClient httpClient;

    protected String getTenantId() {
        return testTenantId;
    }

    // ---- Setup / Teardown ----

    @Before
    public void setUp() throws Exception {
        // Cookie jar to retain the signup session for scraping the API secret page
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

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        testTenantEmail = "android-test-" + suffix + "@fctest.com";

        // 1. Sign up tenant via form POST (no rate limits on @fctest.com)
        RequestBody signupBody = new FormBody.Builder()
                .add("username", "android-test-" + suffix)
                .add("email", testTenantEmail)
                .add("companyName", "Android Test " + suffix)
                .add("domains", "test-" + suffix + ".example.com")
                .add("packageId", "adv")
                .add("noTracking", "true")
                .build();

        Request signupRequest = new Request.Builder()
                .url(TestConfig.HOST + "/auth/tenant-signup")
                .post(signupBody)
                .build();

        try (Response response = httpClient.newCall(signupRequest).execute()) {
            if (!response.isSuccessful() && response.code() != 302) {
                fail("Tenant signup failed with status: " + response.code());
            }
        }

        // 2. Get tenant ID via e2e test API
        Request tenantRequest = new Request.Builder()
                .url(TestConfig.HOST + "/test-e2e/api/tenant/by-email/" + testTenantEmail
                        + "?API_KEY=" + TestConfig.E2E_API_KEY)
                .get()
                .build();

        try (Response response = httpClient.newCall(tenantRequest).execute()) {
            String body = response.body().string();
            JSONObject json = new JSONObject(body);
            JSONObject tenant = json.getJSONObject("tenant");
            testTenantId = tenant.getString("_id");
        }

        if (testTenantId == null || testTenantId.isEmpty()) {
            fail("Could not get tenant ID for " + testTenantEmail);
        }

        // 3. Get API key/secret by fetching the API secret page with the signup session
        Request apiSecretRequest = new Request.Builder()
                .url(TestConfig.HOST + "/auth/my-account/api-secret")
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

        if (testTenantApiKey == null || testTenantApiKey.isEmpty()) {
            fail("Could not extract API key from api-secret page");
        }
    }

    @After
    public void tearDown() throws Exception {
        // Close all SDK WebSocket connections
        for (FastCommentsSDK sdk : sdksToCleanup) {
            try { sdk.cleanup(); } catch (Exception ignored) {}
        }
        for (FastCommentsFeedSDK sdk : feedSdksToCleanup) {
            try { sdk.cleanup(); } catch (Exception ignored) {}
        }

        // Clean up comments for each urlId
        for (String urlId : urlIdsToCleanup) {
            cleanupComments(urlId);
        }

        // Delete the test tenant via e2e API
        if (testTenantEmail != null) {
            try {
                Request deleteRequest = new Request.Builder()
                        .url(TestConfig.HOST + "/test-e2e/api/tenant/by-email/" + testTenantEmail
                                + "?API_KEY=" + TestConfig.E2E_API_KEY)
                        .delete()
                        .build();
                try (Response ignored = httpClient.newCall(deleteRequest).execute()) {
                    // Best effort
                }
            } catch (Exception ignored) {}
        }

        urlIdsToCleanup.clear();
        sdksToCleanup.clear();
        feedSdksToCleanup.clear();
        testTenantId = null;
        testTenantEmail = null;
        testTenantApiKey = null;
    }

    private void cleanupComments(String urlId) {
        // Best-effort cleanup — failures here must not cause test failures.
        // The test tenant will be deleted anyway, which removes all its data.
    }

    // ---- SDK Factories ----

    protected String makeUrlId(String testName) {
        String sanitized = testName.replaceAll("[^a-zA-Z0-9-]", "");
        String urlId = "android-test-" + sanitized + "-" + System.currentTimeMillis();
        urlIdsToCleanup.add(urlId);
        return urlId;
    }

    protected String makeSSOToken() {
        return makeSSOToken(UUID.randomUUID().toString());
    }

    protected String makeSSOToken(String userId) {
        try {
            SecureSSOUserData userData = new SecureSSOUserData(
                    userId,
                    "tester-" + userId.substring(0, Math.min(8, userId.length())) + "@fctest.com",
                    "Tester " + userId.substring(0, Math.min(6, userId.length())),
                    ""
            );
            FastCommentsSSO sso = FastCommentsSSO.createSecure(testTenantApiKey, userData);
            return sso.prepareToSend();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSO token", e);
        }
    }

    protected String makeAdminSSOToken() {
        return makeAdminSSOToken(UUID.randomUUID().toString());
    }

    protected String makeAdminSSOToken(String userId) {
        try {
            SecureSSOUserData userData = new SecureSSOUserData(
                    userId,
                    "admin-" + userId.substring(0, Math.min(8, userId.length())) + "@fctest.com",
                    "Admin " + userId.substring(0, Math.min(6, userId.length())),
                    ""
            );
            userData.isAdmin = true;
            FastCommentsSSO sso = FastCommentsSSO.createSecure(testTenantApiKey, userData);
            return sso.prepareToSend();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create admin SSO token", e);
        }
    }

    protected FastCommentsSDK makeSDK(String testName) {
        String urlId = makeUrlId(testName);
        return makeSDKWithUrlId(urlId, makeSSOToken());
    }

    protected FastCommentsSDK makeSDKWithUrlId(String urlId) {
        return makeSDKWithUrlId(urlId, makeSSOToken());
    }

    protected FastCommentsSDK makeSDKWithUrlId(String urlId, String ssoToken) {
        FastCommentsSDK sdk = makeSDKInternal(urlId, ssoToken);
        sdksToCleanup.add(sdk);
        return sdk;
    }

    private FastCommentsSDK makeSDKInternal(String urlId, String ssoToken) {
        CommentWidgetConfig config = new CommentWidgetConfig();
        config.tenantId = testTenantId;
        config.urlId = urlId;
        config.sso = ssoToken;
        FastCommentsSDK sdk = new FastCommentsSDK(config);
        sdk.commentsTree.setAdapter(mock(CommentsAdapter.class));
        return sdk;
    }

    protected FastCommentsSDK makeAdminSDK(String testName) {
        String urlId = makeUrlId(testName);
        return makeAdminSDKWithUrlId(urlId);
    }

    protected FastCommentsSDK makeAdminSDKWithUrlId(String urlId) {
        return makeSDKWithUrlId(urlId, makeAdminSSOToken());
    }

    // ---- Sync Wrappers ----

    protected GetCommentsResponseWithPresencePublicComment loadSync(FastCommentsSDK sdk) throws Exception {
        AtomicReference<GetCommentsResponseWithPresencePublicComment> resultRef = new AtomicReference<>();
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.load(new FCCallback<GetCommentsResponseWithPresencePublicComment>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(GetCommentsResponseWithPresencePublicComment response) {
                resultRef.set(response);
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("load() failed: " + errorRef.get().getReason());
        }
        return resultRef.get();
    }

    protected PublicComment postCommentSync(FastCommentsSDK sdk, String text) throws Exception {
        return postCommentSync(sdk, text, null);
    }

    protected PublicComment postCommentSync(FastCommentsSDK sdk, String text, String parentId) throws Exception {
        AtomicReference<PublicComment> resultRef = new AtomicReference<>();
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.postComment(text, parentId, new FCCallback<PublicComment>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(PublicComment response) {
                resultRef.set(response);
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("postComment() failed: " + errorRef.get().getReason());
        }
        return resultRef.get();
    }

    protected APIError postCommentSyncExpectFailure(FastCommentsSDK sdk, String text) throws Exception {
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        AtomicReference<PublicComment> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.postComment(text, null, new FCCallback<PublicComment>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(PublicComment response) {
                resultRef.set(response);
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (resultRef.get() != null) {
            fail("Expected postComment() to fail but it succeeded");
        }
        return errorRef.get();
    }

    protected VoteResponse voteCommentSync(FastCommentsSDK sdk, String commentId, boolean isUpvote) throws Exception {
        AtomicReference<VoteResponse> resultRef = new AtomicReference<>();
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.voteComment(commentId, isUpvote, null, null, new FCCallback<VoteResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(VoteResponse response) {
                resultRef.set(response);
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("voteComment() failed: " + errorRef.get().getReason());
        }
        return resultRef.get();
    }

    protected VoteDeleteResponse deleteVoteSync(FastCommentsSDK sdk, String commentId, String voteId) throws Exception {
        AtomicReference<VoteDeleteResponse> resultRef = new AtomicReference<>();
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.deleteCommentVote(commentId, voteId, new FCCallback<VoteDeleteResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(VoteDeleteResponse response) {
                resultRef.set(response);
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("deleteCommentVote() failed: " + errorRef.get().getReason());
        }
        return resultRef.get();
    }

    protected void deleteCommentSync(FastCommentsSDK sdk, String commentId) throws Exception {
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.deleteComment(commentId, new FCCallback<APIEmptyResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(APIEmptyResponse response) {
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("deleteComment() failed: " + errorRef.get().getReason());
        }
    }

    protected SetCommentTextResult editCommentSync(FastCommentsSDK sdk, String commentId, String newText) throws Exception {
        AtomicReference<SetCommentTextResult> resultRef = new AtomicReference<>();
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.editComment(commentId, newText, new FCCallback<SetCommentTextResult>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(SetCommentTextResult response) {
                resultRef.set(response);
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("editComment() failed: " + errorRef.get().getReason());
        }
        return resultRef.get();
    }

    protected void loadMoreSync(FastCommentsSDK sdk) throws Exception {
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.loadMore(new FCCallback<GetCommentsResponseWithPresencePublicComment>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(GetCommentsResponseWithPresencePublicComment response) {
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("loadMore() failed: " + errorRef.get().getReason());
        }
    }

    protected void flagCommentSync(FastCommentsSDK sdk, String commentId) throws Exception {
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.flagComment(commentId, new FCCallback<APIEmptyResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(APIEmptyResponse response) {
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("flagComment() failed: " + errorRef.get().getReason());
        }
    }

    protected void blockUserSync(FastCommentsSDK sdk, String commentId) throws Exception {
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.blockUserFromComment(commentId, new FCCallback<BlockSuccess>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(BlockSuccess response) {
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("blockUserFromComment() failed: " + errorRef.get().getReason());
        }
    }

    protected void unflagCommentSync(FastCommentsSDK sdk, String commentId) throws Exception {
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.unflagComment(commentId, new FCCallback<APIEmptyResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(APIEmptyResponse response) {
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("unflagComment() failed: " + errorRef.get().getReason());
        }
    }

    protected void pinCommentSync(FastCommentsSDK sdk, String commentId) throws Exception {
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.pinComment(commentId, new FCCallback<ChangeCommentPinStatusResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(ChangeCommentPinStatusResponse response) {
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("pinComment() failed: " + errorRef.get().getReason());
        }
    }

    protected APIError pinCommentSyncExpectFailure(FastCommentsSDK sdk, String commentId) throws Exception {
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        AtomicReference<ChangeCommentPinStatusResponse> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.pinComment(commentId, new FCCallback<ChangeCommentPinStatusResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(ChangeCommentPinStatusResponse response) {
                resultRef.set(response);
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);
        return errorRef.get();
    }

    protected void unpinCommentSync(FastCommentsSDK sdk, String commentId) throws Exception {
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.unpinComment(commentId, new FCCallback<ChangeCommentPinStatusResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(ChangeCommentPinStatusResponse response) {
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("unpinComment() failed: " + errorRef.get().getReason());
        }
    }

    protected void lockCommentSync(FastCommentsSDK sdk, String commentId) throws Exception {
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.lockComment(commentId, new FCCallback<APIEmptyResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(APIEmptyResponse response) {
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("lockComment() failed: " + errorRef.get().getReason());
        }
    }

    protected APIError lockCommentSyncExpectFailure(FastCommentsSDK sdk, String commentId) throws Exception {
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        AtomicReference<APIEmptyResponse> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.lockComment(commentId, new FCCallback<APIEmptyResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(APIEmptyResponse response) {
                resultRef.set(response);
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);
        return errorRef.get();
    }

    protected void unlockCommentSync(FastCommentsSDK sdk, String commentId) throws Exception {
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.unlockComment(commentId, new FCCallback<APIEmptyResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(APIEmptyResponse response) {
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("unlockComment() failed: " + errorRef.get().getReason());
        }
    }

    protected void unblockUserSync(FastCommentsSDK sdk, String commentId) throws Exception {
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.unblockUserFromComment(commentId, new FCCallback<UnblockSuccess>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(UnblockSuccess response) {
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("unblockUserFromComment() failed: " + errorRef.get().getReason());
        }
    }

    // ---- Feed SDK Factories & Wrappers ----

    protected FastCommentsFeedSDK makeFeedSDK(String testName) {
        String urlId = makeUrlId(testName);
        CommentWidgetConfig config = new CommentWidgetConfig();
        config.tenantId = testTenantId;
        config.urlId = urlId;
        config.sso = makeSSOToken();
        FastCommentsFeedSDK sdk = new FastCommentsFeedSDK(config);
        feedSdksToCleanup.add(sdk);
        return sdk;
    }

    protected PublicFeedPostsResponse loadFeedSync(FastCommentsFeedSDK sdk) throws Exception {
        AtomicReference<PublicFeedPostsResponse> resultRef = new AtomicReference<>();
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.load(new FCCallback<PublicFeedPostsResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(PublicFeedPostsResponse response) {
                resultRef.set(response);
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("Feed load() failed: " + errorRef.get().getReason());
        }
        return resultRef.get();
    }

    protected FeedPost createFeedPostSync(FastCommentsFeedSDK sdk, String title, String content) throws Exception {
        AtomicReference<FeedPost> resultRef = new AtomicReference<>();
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        CreateFeedPostParams params = new CreateFeedPostParams();
        params.setTitle(title);
        params.setContentHTML(content);

        sdk.createPost(params, new FCCallback<FeedPost>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(FeedPost response) {
                resultRef.set(response);
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("createPost() failed: " + errorRef.get().getReason());
        }
        return resultRef.get();
    }

    protected void deleteFeedPostSync(FastCommentsFeedSDK sdk, String postId) throws Exception {
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.deleteFeedPost(postId, new FCCallback<APIEmptyResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(APIEmptyResponse response) {
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("deleteFeedPost() failed: " + errorRef.get().getReason());
        }
    }

    protected void likeFeedPostSync(FastCommentsFeedSDK sdk, String postId) throws Exception {
        AtomicReference<APIError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        sdk.likePost(postId, new FCCallback<FeedPost>() {
            @Override
            public boolean onFailure(APIError error) {
                errorRef.set(error);
                latch.countDown();
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(FeedPost response) {
                latch.countDown();
                return FCCallback.CONSUME;
            }
        });

        awaitLatch(latch);

        if (errorRef.get() != null) {
            fail("likePost() failed: " + errorRef.get().getReason());
        }
    }

    // ---- Polling / Waiting ----

    protected void waitFor(BooleanSupplier condition) throws Exception {
        waitFor(DEFAULT_TIMEOUT_MS, DEFAULT_INTERVAL_MS, condition);
    }

    protected void waitFor(long timeoutMs, long intervalMs, BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for condition after " + timeoutMs + "ms");
                return;
            }
            ShadowLooper.idleMainLooper();
            Thread.sleep(intervalMs);
        }
    }

    private void awaitLatch(CountDownLatch latch) throws Exception {
        long deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;
        while (latch.getCount() > 0) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for SDK callback after " + DEFAULT_TIMEOUT_MS + "ms");
                return;
            }
            ShadowLooper.idleMainLooper();
            Thread.sleep(50);
        }
    }
}
