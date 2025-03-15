package com.fastcomments.sdk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.fastcomments.api.PublicApi;
import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.invoker.ApiCallback;
import com.fastcomments.invoker.ApiException;
import com.fastcomments.model.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Main SDK class for interacting with FastComments API
 */
public class FastCommentsSDK {

    private UserSessionInfo currentUser;
    private CommentWidgetConfig config;
    private final PublicApi api;
    private final Handler mainHandler;
    public Map<String, RenderableComment> commentsById;
    public List<RenderableComment> commentsTree;
    public int commentCountOnClient;
    public int commentCountOnServer;
    public int newRootCommentCount;
    public boolean isSiteAdmin;
    public boolean isClosed;
    public boolean hasBillingIssue;
    public boolean commentsVisible;
    public boolean isDemo;
    public boolean hasMore;
    public int currentPage;
    public long lastGenDate;
    public Set<String> broadcastIdsSent;
    public String blockingErrorMessage;

    public FastCommentsSDK(CommentWidgetConfig config) {
        this.api = new PublicApi();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.commentsById = new HashMap<>(30);
        this.commentsTree = new ArrayList<>(30);
        this.broadcastIdsSent = new HashSet<>(0);
        this.config = config;
        this.api.getApiClient().setBasePath("http://localhost:3001");
    }

    public FastCommentsSDK() {
        this(null);
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
    public void load(FCCallback<GetCommentsResponseWithPresencePublicComment> callback) {
        getComments(new FCCallback<GetCommentsResponseWithPresencePublicComment>() {
            @Override
            public boolean onFailure(APIError error) {
                callback.onFailure(error);
                return CONSUME;
            }

            @Override
            public boolean onSuccess(GetCommentsResponseWithPresencePublicComment response) {
                // If the response has a custom config, merge it with our config
                if (response.getCustomConfig() != null) {
                    config.mergeWith(response.getCustomConfig());
                }
                commentsTree = RenderableComment.buildCommentTree(commentsById, response.getComments());
                callback.onSuccess(response);
                return CONSUME;
            }
        });
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
        Integer pageParam = null;
        if (page != null) {
            pageParam = page;
        } else if (config.startingPage != null) {
            pageParam = config.startingPage;
        }

        try {
            // Make the API call asynchronously
            api.getComments(config.tenantId, config.urlId)
                    .page(pageParam)
                    .direction(direction)
                    .sso(config.getSSOToken())
                    .asTree(BooleanQueryParam.TRUE)
                    .maxTreeDepth(maxTreeDepth)
                    .parentId(parentId)
                    .skip(skip)
                    .skipChildren(skip)
                    .limit(limit)
                    .limitChildren(limit)
                    .lastGenDate(lastGenDate)
                    .includeConfig(BooleanQueryParam.TRUE)
                    .countAll(Boolean.TRUE.equals(config.countAll) ? BooleanQueryParam.TRUE : BooleanQueryParam.FALSE)
                    .locale(config.locale)
                    .includeNotificationCount(BooleanQueryParam.TRUE)
                    .executeAsync(new ApiCallback<GetComments200Response>() {
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
                    });
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
//            if (currentUser.getActualInstance() instanceof AuthenticatedUserDetails) {
//                AuthenticatedUserDetails authenticatedUser = currentUser.getAuthenticatedUserDetails();
//                commentData.setUserId(authenticatedUser.getId());
//                commentData.setCommenterName(authenticatedUser.getUsername());
//                commentData.setAvatarSrc(authenticatedUser.getAvatarSrc());
//                commentData.setCommenterEmail(authenticatedUser.getEmail());
//            } else if (currentUser.getActualInstance() instanceof AnonUserDetails) {
//                AnonUserDetails anonUser = currentUser.getAnonUserDetails();
//                commentData.setCommenterName(anonUser.getUsername());
//                commentData.setCommenterEmail(anonUser.getEmail());
//            }
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