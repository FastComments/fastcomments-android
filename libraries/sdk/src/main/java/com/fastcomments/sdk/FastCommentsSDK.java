package com.fastcomments.sdk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.fastcomments.api.PublicApi;
import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.invoker.ApiCallback;
import com.fastcomments.invoker.ApiException;
import com.fastcomments.model.*;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Main SDK class for interacting with FastComments API
 */
public class FastCommentsSDK {

    private final Context context;
    private UserSessionInfo currentUser;
    private CommentWidgetConfig config;
    private final PublicApi api;
    private final Handler mainHandler;
    private Map<String, RenderableComment> commentsById;
    private List<RenderableComment> commentsTree;
    private List<RenderableComment> FastCommentsWidgetComment;
    private int commentCountOnClient;
    private int commentCountOnServer;
    private int newRootCommentCount;
    private boolean isSiteAdmin;
    private boolean isClosed;
    private boolean hasBillingIssue;
    private boolean commentsVisible;
    private boolean isDemo;
    private boolean hasMore;
    private int currentPage;
    private long lastGenDate;
    private Set<String> broadcastIdsSent;
    private String blockingErrorMessage;

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
     * When interacting with the SDK, the SDK can be stateful. Call load() to get comments and
     * setup any other required state.
     */
    public void load() {
        // If the response has a custom config, merge it with our config
        if (commentsResponse.getCustomConfig() != null) {
            config.mergeWith(commentsResponse.getCustomConfig());
        }
    }

    /**
     * Load comments asynchronously
     *
     * @param callback Callback to receive the response
     */
    public void getComments(FCCallback<GetCommentsResponseWithPresencePublicComment> callback) {
        getComments(currentPage, null, null, 1, null, callback);
    }

    /**
     * Load comments asynchronously with pagination and threading options.
     */
    public void getComments(
            Integer page,
            Integer skip,
            Integer limit,
            Integer maxTreeDepth,
            String parentId,
            final FCCallback<GetCommentsResponseWithPresencePublicComment> callback) {

        SortDirections direction = config.defaultSortDirection;

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
                    config.urlId,
                    pageParam,
                    direction,
                    config.getSSOToken(),
                    skipParam,
                    skipChildren,
                    limitParam,
                    limitChildren,
                    null, // lastGenDate
                    null, // fetchPageForCommentId
                    BooleanQueryParam.TRUE, // includeConfig
                    config.countAll ? BooleanQueryParam.TRUE : BooleanQueryParam.FALSE, // countAll
                    null, // includei10n
                    config.locale, // locale
                    null, // modules
                    null, // isCrawler
                    BooleanQueryParam.TRUE, // includeNotificationCount
                    BooleanQueryParam.TRUE, // asTree - hierarchical comment structure
                    maxTreeDepthParam, // maxTreeDepth
                    null, // useFullTranslationIds
                    parentId, // parentId - for loading replies to a specific comment
                    null, // searchText
                    null, // hashTags
                    config.userId, // userId
                    null, // customConfigStr
                    new ApiCallback<GetComments200Response>() {
                        @Override
                        public void onFailure(ApiException e, int i, Map<String, List<String>> map) {
                            callback.onFailure(CallbackWrapper.createErrorFromException(e));
                        }

                        @Override
                        public void onSuccess(GetComments200Response getComments200Response, int i, Map<String, List<String>> map) {
                            if (getComments200Response.getActualInstance() instanceof APIError) {
                                callback.onFailure((APIError) getComments200Response.getActualInstance());
                            } else {
                                final GetCommentsResponseWithPresencePublicComment commentsResponse = getComments200Response.getGetCommentsResponseWithPresencePublicComment();

                                callback.onSuccess(commentsResponse);
                            }
                        }

                        @Override
                        public void onUploadProgress(long l, long l1, boolean b) {

                        }

                        @Override
                        public void onDownloadProgress(long l, long l1, boolean b) {

                        }
                    }
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
     * Create a CommentWidgetConfig with the specified tenant ID and URL ID.
     * This is just to help discover the d
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
}