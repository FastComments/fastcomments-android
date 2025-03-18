package com.fastcomments.sdk;

import android.os.Handler;
import android.os.Looper;

import com.fastcomments.api.PublicApi;
import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.invoker.ApiCallback;
import com.fastcomments.invoker.ApiException;
import com.fastcomments.model.*;

import java.util.Date;
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
    public final CommentsTree commentsTree;
    public int commentCountOnServer;
    public int newRootCommentCount;
    public boolean isSiteAdmin;
    public boolean isClosed;
    public boolean hasBillingIssue;
    public boolean commentsVisible;
    public boolean isDemo;
    public boolean hasMore;
    public int currentPage;
    public int currentSkip;
    public int pageSize = 30; // Default page size for pagination
    public long lastGenDate;
    public Set<String> broadcastIdsSent;
    public String blockingErrorMessage;

    public FastCommentsSDK(CommentWidgetConfig config) {
        this.api = new PublicApi();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.broadcastIdsSent = new HashSet<>(0);
        this.config = config;
        this.api.getApiClient().setBasePath("https://fastcomments.com");
        this.commentsTree = new CommentsTree();
        this.currentSkip = 0;
        this.currentPage = 0;
        this.hasMore = false;
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
        // Reset pagination for initial load
        currentSkip = 0;
        currentPage = 0;
        
        getCommentsAndRelatedData(new FCCallback<GetCommentsResponseWithPresencePublicComment>() {
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
                if (response.getUser() != null) {
                    currentUser = response.getUser();
                }
                
                // Update the total server count
                commentCountOnServer = response.getCount() != null ? response.getCount().intValue() : 0;
                
                // Determine if we have more comments to load from the response
                hasMore = response.getHasMore() != null ? response.getHasMore() : false;
                
                commentsTree.build(response.getComments());
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
    public void getCommentsAndRelatedData(FCCallback<GetCommentsResponseWithPresencePublicComment> callback) {
        getCommentsAndRelatedData(currentSkip, pageSize, 1, callback);
    }

    /**
     * Load comments asynchronously with pagination and threading options.
     */
    public void getCommentsAndRelatedData(
            Integer skip,
            Integer limit,
            Integer maxTreeDepth,
            final FCCallback<GetCommentsResponseWithPresencePublicComment> callback) {

        SortDirections direction = config.defaultSortDirection;

        try {
            // Make the API call asynchronously
            api.getComments(config.tenantId, config.urlId)
                    .direction(direction)
                    .sso(config.getSSOToken())
                    .asTree(true)
                    .maxTreeDepth(maxTreeDepth)
                    .skip(skip)
                    .skipChildren(skip)
                    .limit(limit)
                    .limitChildren(limit)
                    .lastGenDate(lastGenDate)
                    .includeConfig(true)
                    .countAll(Boolean.TRUE.equals(config.countAll))
                    .countChildren(true)
                    .locale(config.locale)
                    .includeNotificationCount(true)
                    .executeAsync(new ApiCallback<GetComments200Response>() {
                        @Override
                        public void onFailure(ApiException e, int i, Map<String, List<String>> map) {
                            callback.onFailure(CallbackWrapper.createErrorFromException(e));
                        }

                        @Override
                        public void onSuccess(GetComments200Response response, int i, Map<String, List<String>> map) {
                            if (response.getActualInstance() instanceof APIError) {
                                callback.onFailure((APIError) response.getActualInstance());
                            } else {
                                final GetCommentsResponseWithPresencePublicComment commentsResponse = response.getGetCommentsResponseWithPresencePublicComment();

                                if (commentsResponse.getUrlIdClean() != null) {
                                    config.urlId = commentsResponse.getUrlIdClean();
                                }
                                // TODO tenantIdWS
                                // TODO urlIdWS
                                // TODO userIdWS

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
     * Load comments asynchronously with pagination and threading options. Doesn't include other info (notification counts, page config, etc).
     */
    public void getCommentsForParent(
            Integer skip,
            Integer limit,
            Integer maxTreeDepth,
            String parentId,
            final FCCallback<GetCommentsResponseWithPresencePublicComment> callback) {

        SortDirections direction = config.defaultSortDirection;

        try {
            // Make the API call asynchronously
            api.getComments(config.tenantId, config.urlId)
                    .direction(direction)
                    .sso(config.getSSOToken())
                    .asTree(true)
                    .maxTreeDepth(maxTreeDepth)
                    .parentId(parentId)
                    .skip(skip)
                    .skipChildren(skip)
                    .limit(limit)
                    .limitChildren(limit)
                    .lastGenDate(lastGenDate)
                    .countChildren(true)
                    .locale(config.locale)
                    .executeAsync(new ApiCallback<GetComments200Response>() {
                        @Override
                        public void onFailure(ApiException e, int i, Map<String, List<String>> map) {
                            callback.onFailure(CallbackWrapper.createErrorFromException(e));
                        }

                        @Override
                        public void onSuccess(GetComments200Response response, int i, Map<String, List<String>> map) {
                            if (response.getActualInstance() instanceof APIError) {
                                callback.onFailure((APIError) response.getActualInstance());
                            } else {
                                final GetCommentsResponseWithPresencePublicComment commentsResponse = response.getGetCommentsResponseWithPresencePublicComment();
                                commentsTree.addForParent(parentId, commentsResponse.getComments());
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
     * Load more comments for pagination
     *
     * @param callback Callback to receive the response
     */
    public void loadMore(FCCallback<GetCommentsResponseWithPresencePublicComment> callback) {
        // Increment skip value to get the next page
        currentSkip += pageSize;
        currentPage++;
        
        getCommentsAndRelatedData(new FCCallback<GetCommentsResponseWithPresencePublicComment>() {
            @Override
            public boolean onFailure(APIError error) {
                // If there's a failure, reset the pagination values to the previous state
                currentSkip -= pageSize;
                currentPage--;
                callback.onFailure(error);
                return CONSUME;
            }

            @Override
            public boolean onSuccess(GetCommentsResponseWithPresencePublicComment response) {
                // Update the total server count
                commentCountOnServer = response.getCount() != null ? response.getCount().intValue() : commentCountOnServer;
                
                // Determine if we have more comments to load
                hasMore = response.getHasMore() != null ? response.getHasMore() : false;
                
                // Append the new comments to the existing ones
                commentsTree.appendComments(response.getComments());
                callback.onSuccess(response);
                return CONSUME;
            }
        });
    }

    /**
     * Load all remaining comments at once
     *
     * @param callback Callback to receive the response
     */
    public void loadAll(FCCallback<GetCommentsResponseWithPresencePublicComment> callback) {
        // Temporarily set the pageSize to a large number to get all comments
        int originalPageSize = pageSize;
        pageSize = 1000; // A large enough value to get all comments
        
        // Reset skip to ensure we get all comments from the beginning
        currentSkip = 0;
        
        getCommentsAndRelatedData(new FCCallback<GetCommentsResponseWithPresencePublicComment>() {
            @Override
            public boolean onFailure(APIError error) {
                // Restore original page size
                pageSize = originalPageSize;
                callback.onFailure(error);
                return CONSUME;
            }

            @Override
            public boolean onSuccess(GetCommentsResponseWithPresencePublicComment response) {
                // Restore original page size
                pageSize = originalPageSize;
                
                // Update the total server count
                commentCountOnServer = response.getCount() != null ? response.getCount().intValue() : 0;
                
                // We loaded all comments, so there are no more
                hasMore = false;
                
                // Replace all comments with the new ones
                commentsTree.build(response.getComments());
                callback.onSuccess(response);
                return CONSUME;
            }
        });
    }
    
    /**
     * Check if we should show the "Load All" button based on total comment count
     * @return true if "Load All" should be shown (less than 1000 comments)
     */
    public boolean shouldShowLoadAll() {
        return commentCountOnServer < 1000;
    }

    /**
     * Get the count of remaining comments to load
     * @return The number of comments that can be loaded next
     */
    public int getCountRemainingToShow() {
        int remaining = commentCountOnServer - commentsTree.totalSize();
        return Math.min(Math.max(remaining, 0), pageSize);
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