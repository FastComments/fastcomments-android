package com.fastcomments.sdk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.fastcomments.api.PublicApi;
import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.core.FastCommentsSSO;
import com.fastcomments.invoker.ApiCallback;
import com.fastcomments.invoker.ApiException;
import com.fastcomments.model.*;
import com.google.gson.Gson;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Main SDK class for interacting with FastComments API
 */
public class FastCommentsSDK {

    private final Context context;
    private UserSessionInfo currentUser;
    private CommentWidgetConfig config;
    private final PublicApi api;
    private final Handler mainHandler;
    private final static Gson gson = new Gson();

    public FastCommentsSDK(Context context) {
        this.context = context;
        this.api = new PublicApi();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Configure the SDK with widget configuration
     *
     * @param config CommentWidgetConfig object
     * @return FastCommentsSDK instance for chaining
     */
    public FastCommentsSDK configure(CommentWidgetConfig config) {
        this.config = config;
        return this;
    }

    /**
     * Set the current user for SSO authentication
     *
     * @param userInfo User session information
     * @return FastCommentsSDK instance for chaining
     */
    public FastCommentsSDK setCurrentUser(UserSessionInfo userInfo) {
        this.currentUser = userInfo;
        return this;
    }

    /**
     * Get the current widget configuration
     *
     * @return CommentWidgetConfig object
     */
    public CommentWidgetConfig getConfig() {
        return config;
    }

    /**
     * Get the current user information
     *
     * @return UserSessionInfo object
     */
    public UserSessionInfo getCurrentUser() {
        return currentUser;
    }

    /**
     * Callback interface for comment loading
     */
    public interface CommentsCallback {
        void onSuccess(GetCommentsResponseWithPresencePublicComment response);

        void onError(Exception e);
    }

    /**
     * Load comments asynchronously
     *
     * @param callback Callback to receive the response
     */
    public void getComments(final CommentsCallback callback) {
        getComments(null, null, null, true, null, null, callback);
    }

    /**
     * Load comments asynchronously with pagination and threading options
     *
     * @param page         The page number (starting from 0)
     * @param skip         Number of comments to skip
     * @param limit        Maximum number of comments to return
     * @param asTree       Whether to return comments in tree structure
     * @param maxTreeDepth Maximum depth of reply tree
     * @param parentId     Parent comment ID to fetch replies for
     * @param callback     Callback to receive the response
     */
    public void getComments(
            Integer page,
            Integer skip,
            Integer limit,
            Boolean asTree,
            Integer maxTreeDepth,
            String parentId,
            final FCCallback<GetComments200Response> callback) {

        if (config == null) {
            callback.onError(new IllegalStateException("SDK not configured. Call configure() first."));
            return;
        }

        SortDirections direction = config.defaultSortDirection;

        String ssoParam = null;
        if (config.sso != null) {
            ssoParam = config.sso.prepareToSend();
        }

        // If page is not provided, use the one from config
        Double pageParam = null;
        if (page != null) {
            pageParam = page.doubleValue();
        } else if (config.startingPage != null) {
            pageParam = config.startingPage.doubleValue();
        }

        // Convert integer parameters to doubles (API expectation)
        Double skipParam = skip != null ? skip.doubleValue() : null;
        Double limitParam = limit != null ? limit.doubleValue() : null;
        Double maxTreeDepthParam = maxTreeDepth != null ? maxTreeDepth.doubleValue() : null;

        // For pagination of comments within a parent
        Double skipChildren = null;
        Double limitChildren = config.maxReplyDepth != null ? config.maxReplyDepth.doubleValue() : null;

        try {
            // Make the API call asynchronously
            api.getCommentsAsync(
                    config.tenantId,
                    urlId,
                    pageParam,
                    direction,
                    ssoParam,
                    skipParam,
                    skipChildren,
                    limitParam,
                    limitChildren,
                    null, // lastGenDate
                    null, // fetchPageForCommentId
                    true, // includeConfig
                    config.countAll, // countAll
                    null, // includei10n
                    config.locale, // locale
                    null, // modules
                    null, // isCrawler
                    true, // includeNotificationCount
                    asTree, // asTree - hierarchical comment structure
                    maxTreeDepthParam, // maxTreeDepth
                    null, // useFullTranslationIds
                    parentId, // parentId - for loading replies to a specific comment
                    null, // searchText
                    null, // hashTags
                    config.userId, // userId
                    null, // customConfigStr
                    new CallbackWrapper<GetComments200Response>().wrap(mainHandler, callback, new FCCallback<GetComments200Response>() {
                        @Override
                        public boolean onFailure(APIError error) {
                            return CONTINUE;
                        }

                        @Override
                        public boolean onSuccess(GetComments200Response response) {
                            GetCommentsResponseWithPresencePublicComment commentsResponse = response.getGetCommentsResponseWithPresencePublicComment();

                            // If the response has a custom config, merge it with our config
                            if (commentsResponse.getCustomConfig() != null) {
                                config.mergeWith(commentsResponse.getCustomConfig());
                            }

                            return CONTINUE;
                        }
                    })
            );
        } catch (ApiException e) {
            CallbackWrapper.handleAPIException(mainHandler, callback, e);
        }
    }

    /**
     * Create comment data object from the provided parameters
     */
    private CommentData createCommentData(String commentText, String parentId) {
        CommentData commentData = new CommentData();
        commentData.setComment(commentText);
        commentData.setDate(Double.valueOf(new Date().getTime()));
        commentData.setUrlId(config.urlId);
        commentData.setUrl(config.url != null ? config.url : config.urlId);

        // Set parent ID if this is a reply
        if (parentId != null && !parentId.isEmpty()) {
            commentData.setParentId(parentId);
        }

        // Set user info
        if (currentUser != null) {
            if (currentUser.getActualInstance() instanceof AuthenticatedUserDetails) {
                AuthenticatedUserDetails authenticatedUser = currentUser.getAuthenticatedUserDetails();
                commentData.setUserId(authenticatedUser.getId());
                commentData.setCommenterName(authenticatedUser.getUsername());
                commentData.setAvatarSrc(authenticatedUser.getAvatarSrc());
                commentData.setCommenterEmail(authenticatedUser.getEmail());
            } else if (currentUser.getActualInstance() instanceof AnonUserDetails) {
                AnonUserDetails anonUser = currentUser.getAnonUserDetails();
                commentData.setCommenterName(anonUser.getUsername());
                commentData.setCommenterEmail(anonUser.getEmail());
            }
        }

        // Set page title if available
        if (config.pageTitle != null && !config.pageTitle.isEmpty()) {
            commentData.setPageTitle(config.pageTitle);
        }

        // Set metadata if available
        if (config.commentMeta != null && !config.commentMeta.isEmpty()) {
            commentData.setMeta(config.commentMeta);
        }

        return commentData;
    }

    /**
     * Post a new comment asynchronously
     *
     * @param commentText Comment text content
     * @param parentId    Parent comment ID for replies (null for top-level comments)
     */
    public void postComment(String commentText, String parentId, ApiCallback<CreateComment200Response> callback) {
        if (commentText == null || commentText.isEmpty()) {
            mainHandler.post(() -> callback.onError(new IllegalArgumentException("Comment text cannot be empty")));
            return;
        }

        try {
            // Create comment data
            CommentData commentData = createCommentData(commentText, parentId);

            // Convert SSO configuration if present
            String ssoParam = null;
            if (config.sso != null) {
                ssoParam = convertSSOToBase64(config.sso);
            }

            // Generate a unique broadcast ID for this comment
            String broadcastId = "android-" + System.currentTimeMillis();

            // Make the API call asynchronously
            api.createCommentAsync(
                    config.tenantId,
                    config.urlId,
                    broadcastId,
                    commentData,
                    null, // sessionId
                    config.getSSOToken(),
                    new ApiCallback<CreateComment200Response>() {
                        @Override
                        public void onFailure(ApiException e, int i, Map<String, List<String>> map) {

                        }

                        @Override
                        public void onSuccess(CreateComment200Response createComment200Response, int i, Map<String, List<String>> map) {
                            createComment200Response.getAPIError()
                        }

                        @Override
                        public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {

                        }

                        @Override
                        public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {

                        }
                    }
            );
        } catch (ApiException e) {
            mainHandler.post(() -> callback.onError(new Exception("API call failed: " + e.getMessage(), e)));
        }
    }

    /**
     * Create a CommentWidgetConfig with the specified tenant ID and URL ID
     *
     * @param tenantId Your FastComments tenant ID
     * @param urlId    URL ID for the comment thread
     * @return CommentWidgetConfig object
     */
    public static CommentWidgetConfig createConfig(String tenantId, String urlId) {
        CommentWidgetConfig config = new CommentWidgetConfig();
        config.tenantId = tenantId;
        config.urlId = urlId;
        return config;
    }

    /**
     * Create a CommentWidgetConfig with SSO enabled
     *
     * @param tenantId Your FastComments tenant ID
     * @param urlId    URL ID for the comment thread
     * @param sso      SSO configuration
     * @return CommentWidgetConfig object
     */
    public static CommentWidgetConfig createConfigWithSSO(String tenantId, String urlId, FastCommentsSSO sso) {
        CommentWidgetConfig config = createConfig(tenantId, urlId);
        config.sso = sso;
        return config;
    }
}