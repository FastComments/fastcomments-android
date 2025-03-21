package com.fastcomments.sdk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.fastcomments.api.PublicApi;
import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.invoker.ApiCallback;
import com.fastcomments.invoker.ApiException;
import com.fastcomments.model.*;
import com.fastcomments.pubsub.LiveEventSubscriber;
import com.fastcomments.pubsub.SubscribeToChangesResult;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

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
    public int pageSize = 30;
    public long lastGenDate;
    public Set<String> broadcastIdsSent;
    public String blockingErrorMessage;

    private SubscribeToChangesResult liveEventSubscription;
    private final LiveEventSubscriber liveEventSubscriber;
    private String tenantIdWS;
    private String urlIdWS;
    private String userIdWS;
    private String editKey;

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
        this.liveEventSubscriber = new LiveEventSubscriber();

        // Set up the presence status listener on the comments tree
        this.commentsTree.setPresenceStatusListener(this::fetchPresenceForUsers);
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

        // Reset any existing error message
        blockingErrorMessage = null;
        
        getCommentsAndRelatedData(new FCCallback<GetCommentsResponseWithPresencePublicComment>() {
            @Override
            public boolean onFailure(APIError error) {
                // Set blockingErrorMessage from translatedError or reason
                if (error.getTranslatedError() != null && !error.getTranslatedError().isEmpty()) {
                    blockingErrorMessage = error.getTranslatedError();
                } else if (error.getReason() != null && !error.getReason().isEmpty()) {
                    blockingErrorMessage = error.getReason();
                }
                // Note: No fallback string here - the UI will handle this with R.string.generic_loading_error
                
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
                if (response.getUrlIdClean() != null) {
                    config.urlId = response.getUrlIdClean();
                }

                // Extract WebSocket parameters for live events
                boolean needsWebsocketReconnect = false;
                
                if (response.getTenantIdWS() != null) {
                    tenantIdWS = response.getTenantIdWS();
                }
                if (response.getUrlIdWS() != null) {
                    urlIdWS = response.getUrlIdWS();
                }
                if (response.getUserIdWS() != null) {
                    // Check if userIdWS has changed, which requires WebSocket reconnection
                    if (userIdWS != null && !Objects.equals(response.getUserIdWS(), userIdWS)) {
                        needsWebsocketReconnect = true;
                    }
                    userIdWS = response.getUserIdWS();
                }

                // Update the total server count
                commentCountOnServer = response.getCommentCount() != null ? response.getCommentCount() : 0;

                // Determine if we have more comments to load from the response
                hasMore = response.getHasMore() != null ? response.getHasMore() : false;

                commentsTree.build(response.getComments());

                // Subscribe to live events if we have all required parameters
                // or if we need to reconnect due to userIdWS change
                if ((tenantIdWS != null && urlIdWS != null && userIdWS != null) && 
                    (liveEventSubscription == null || needsWebsocketReconnect)) {
                    subscribeToLiveEvents();
                }

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
        getCommentsAndRelatedData(currentSkip, pageSize, 1, true, true, callback);
    }

    /**
     * Load comments asynchronously with pagination and threading options.
     */
    public void getCommentsAndRelatedData(
            Integer skip,
            Integer limit,
            Integer maxTreeDepth,
            boolean includeConfig,
            boolean includeNotificationCount,
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
                    .includeConfig(includeConfig)
                    .countAll(Boolean.TRUE.equals(config.countAll))
                    .countChildren(true)
                    .locale(config.locale)
                    .includeNotificationCount(includeNotificationCount)
                    .executeAsync(new ApiCallback<GetComments200Response>() {
                        @Override
                        public void onFailure(ApiException e, int i, Map<String, List<String>> map) {
                            callback.onFailure(CallbackWrapper.createErrorFromException(e));
                        }

                        @Override
                        public void onSuccess(GetComments200Response response, int i, Map<String, List<String>> map) {
                            if (response.getActualInstance() instanceof APIError) {
                                APIError error = (APIError) response.getActualInstance();
                                
                                // Set blockingErrorMessage from translatedError or reason
                                if (error.getTranslatedError() != null && !error.getTranslatedError().isEmpty()) {
                                    blockingErrorMessage = error.getTranslatedError();
                                } else if (error.getReason() != null && !error.getReason().isEmpty()) {
                                    blockingErrorMessage = error.getReason();
                                }
                                
                                callback.onFailure(error);
                            } else {
                                final GetCommentsResponseWithPresencePublicComment commentsResponse = response.getGetCommentsResponseWithPresencePublicComment();

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

        // Set user info based on currentUser
        if (currentUser != null) {
            if (currentUser.getAuthorized() != null && currentUser.getAuthorized()) {
                // User is already authenticated, set userId if available
                if (currentUser.getId() != null) {
                    commentData.setUserId(currentUser.getId());
                }
            }

            // Always set username and email even if authenticated
            // The API will use the authenticated user info if available
            if (currentUser.getUsername() != null) {
                commentData.setCommenterName(currentUser.getUsername());
            }

            if (currentUser.getEmail() != null) {
                commentData.setCommenterEmail(currentUser.getEmail());
            }

            // Set avatar source if available
            if (currentUser.getAvatarSrc() != null) {
                commentData.setAvatarSrc(currentUser.getAvatarSrc());
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
     * Posts a new comment or reply to the FastComments API
     *
     * @param commentText The text of the comment to post
     * @param parentId    The ID of the parent comment (for replies), or null for top-level comments
     * @param callback    Callback to receive the response
     */
    public void postComment(String commentText, String parentId, final FCCallback<PublicComment> callback) {
        if (commentText == null || commentText.trim().isEmpty()) {
            callback.onFailure(new APIError()
                    .status(ImportedAPIStatusFAILED.FAILED)
                    .reason("Comment text is required")
                    .code("empty_comment"));
            return;
        }

        // Create a unique broadcast ID to identify this comment in live events
        String broadcastId = UUID.randomUUID().toString();

        // Track broadcast ID before sending
        broadcastIdsSent.add(broadcastId);

        // Create comment data
        CommentData commentData = createCommentData(commentText, parentId);

        try {
            // Make the API call
            api.createComment(config.tenantId, config.urlId, broadcastId, commentData)
                    .sso(config.getSSOToken())
                    .executeAsync(new ApiCallback<CreateComment200Response>() {
                        @Override
                        public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                            callback.onFailure(CallbackWrapper.createErrorFromException(e));
                        }

                        @Override
                        public void onSuccess(CreateComment200Response result, int statusCode, Map<String, List<String>> responseHeaders) {
                            if (result.getActualInstance() instanceof APIError) {
                                callback.onFailure((APIError) result.getActualInstance());
                            } else {
                                SaveCommentsResponseWithPresence response = result.getSaveCommentsResponseWithPresence();
                                if (response.getUser() != null) {
                                    currentUser = response.getUser();
                                }
                                if (response.getUserIdWS() != null && !Objects.equals(response.getUserIdWS(), userIdWS)) {
                                    userIdWS = response.getUserIdWS();
                                    
                                    // Reconnect websocket with new user ID
                                    if (tenantIdWS != null && urlIdWS != null && userIdWS != null) {
                                        subscribeToLiveEvents();
                                    }
                                }
                                // The API should return the complete comment
                                PublicComment createdComment = response.getComment();
                                callback.onSuccess(createdComment);
                            }
                        }

                        @Override
                        public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
                            // Not used
                        }

                        @Override
                        public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
                            // Not used
                        }
                    });
        } catch (ApiException e) {
            CallbackWrapper.handleAPIException(mainHandler, callback, e);
        }
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

        getCommentsAndRelatedData(currentSkip, pageSize, 0, false, false, new FCCallback<GetCommentsResponseWithPresencePublicComment>() {
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
                commentCountOnServer = response.getCommentCount() != null ? response.getCommentCount() : commentCountOnServer;

                // Determine if we have more comments to load
                hasMore = response.getHasMore() != null ? response.getHasMore() : false;

                // Append the new comments to the existing ones
                mainHandler.post(() -> {
                    commentsTree.appendComments(response.getComments());
                    callback.onSuccess(response);
                });
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
                commentCountOnServer = response.getCommentCount() != null ? response.getCommentCount() : 0;

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
     *
     * @return true if "Load All" should be shown (less than 1000 comments)
     */
    public boolean shouldShowLoadAll() {
        return commentCountOnServer < 1000;
    }

    /**
     * Get the count of remaining comments to load
     *
     * @return The number of comments that can be loaded next
     */
    public int getCountRemainingToShow() {
        int remaining = commentCountOnServer - commentsTree.totalSize();
        return Math.min(Math.max(remaining, 0), pageSize);
    }

    /**
     * Delete a vote from a comment
     *
     * @param commentId The ID of the comment
     * @param voteId    The ID of the vote to delete
     * @param callback  Callback to receive the response
     */
    public void deleteCommentVote(String commentId, String voteId, final FCCallback<VoteDeleteResponse> callback) {
        deleteCommentVote(commentId, voteId, null, null, callback);
    }

    /**
     * Delete a vote from a comment with anonymous user credentials
     *
     * @param commentId      The ID of the comment
     * @param voteId         The ID of the vote to delete
     * @param commenterName  Name for anonymous user (optional, can be null if user is authenticated)
     * @param commenterEmail Email for anonymous user (optional, can be null if user is authenticated)
     * @param callback       Callback to receive the response
     */
    public void deleteCommentVote(String commentId, String voteId, String commenterName, String commenterEmail, final FCCallback<VoteDeleteResponse> callback) {
        if (commentId == null || commentId.isEmpty()) {
            callback.onFailure(new APIError().status(ImportedAPIStatusFAILED.FAILED).reason("Comment ID is required").code("invalid_comment_id"));
            return;
        }

        if (voteId == null || voteId.isEmpty()) {
            callback.onFailure(new APIError().status(ImportedAPIStatusFAILED.FAILED).reason("Vote ID is required").code("invalid_vote_id"));
            return;
        }

        // Create a unique broadcast ID
        String broadcastId = UUID.randomUUID().toString();

        // Track broadcast ID before sending
        broadcastIdsSent.add(broadcastId);

        try {
            // Make the API call - for delete votes, we don't actually need to pass commenter info
            // because the vote is identified by its ID, but we'll keep the method signature consistent
            api.deleteCommentVote(config.tenantId, commentId, voteId, config.urlId, broadcastId)
                    .sso(config.getSSOToken())
                    .executeAsync(new ApiCallback<DeleteCommentVote200Response>() {
                        @Override
                        public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                            callback.onFailure(CallbackWrapper.createErrorFromException(e));
                        }

                        @Override
                        public void onSuccess(DeleteCommentVote200Response result, int statusCode, Map<String, List<String>> responseHeaders) {
                            if (result.getActualInstance() instanceof APIError) {
                                callback.onFailure((APIError) result.getActualInstance());
                            } else {
                                VoteDeleteResponse response = result.getVoteDeleteResponse();
                                callback.onSuccess(response);
                            }
                        }

                        @Override
                        public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
                            // Not used
                        }

                        @Override
                        public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
                            // Not used
                        }
                    });
        } catch (ApiException e) {
            CallbackWrapper.handleAPIException(mainHandler, callback, e);
        }
    }

    /**
     * Vote on a comment (upvote or downvote)
     *
     * @param commentId The ID of the comment to vote on
     * @param isUpvote  True for upvote, false for downvote
     * @param callback  Callback to receive the response
     */
    public void voteComment(String commentId, boolean isUpvote, final FCCallback<VoteResponse> callback) {
        voteComment(commentId, isUpvote, null, null, callback);
    }

    /**
     * Subscribe to FastComments live events using WebSockets
     */
    private void subscribeToLiveEvents() {
        // Close any existing subscription first
        if (liveEventSubscription != null) {
            liveEventSubscription.close();
            liveEventSubscription = null;
        }

        if (config == null || Boolean.TRUE.equals(config.disableLiveCommenting)) {
            return;
        }

        if (tenantIdWS == null || urlIdWS == null || userIdWS == null) {
            Log.e("FastCommentsSDK", "Missing WebSocket parameters, live commenting disabled");
            return;
        }

        // Set connection status change handler
        liveEventSubscriber.setOnConnectionStatusChange(this::handleConnectionStatusChange);

        // Subscribe to live events
        liveEventSubscription = liveEventSubscriber.subscribeToChanges(
                config,
                tenantIdWS,
                config.urlId,
                urlIdWS,
                userIdWS,
                this::checkCommentVisibility,
                this::handleLiveEvent
        );
    }

    /**
     * Handle WebSocket connection status changes
     */
    private void handleConnectionStatusChange(boolean isConnected, Long lastEventTime) {
        if (isConnected) {
            // WebSocket is connected, fetch initial presence status
            fetchUserPresenceStatuses();
        }
    }

    /**
     * Fetch presence statuses for users in visible comments
     */
    private void fetchUserPresenceStatuses() {
        // Extract user IDs from visible comments
        Set<String> userIds = new HashSet<>();

        for (RenderableNode node : commentsTree.visibleNodes) {
            if (node instanceof RenderableComment) {
                RenderableComment comment = (RenderableComment) node;
                String userId = comment.getComment().getUserId();
                String anonUserId = comment.getComment().getAnonUserId();

                if (userId != null) {
                    userIds.add(userId);
                }
                if (anonUserId != null) {
                    userIds.add(anonUserId);
                }
            }
        }

        // If no users found, no need to proceed
        if (userIds.isEmpty()) {
            return;
        }

        // Create a comma-separated string of user IDs
        StringBuilder userIdsCSV = new StringBuilder();
        for (String userId : userIds) {
            if (userIdsCSV.length() > 0) {
                userIdsCSV.append(",");
            }
            userIdsCSV.append(userId);
        }

        fetchPresenceForUsers(userIdsCSV.toString());
    }

    /**
     * Fetch presence statuses for specific user IDs
     *
     * @param userIdsCSV Comma-separated list of user IDs
     */
    private void fetchPresenceForUsers(String userIdsCSV) {
        if (userIdsCSV == null || userIdsCSV.isEmpty()) {
            return;
        }

        // Call the API to get presence statuses
        try {
            api.getUserPresenceStatuses(config.tenantId, urlIdWS, userIdsCSV)
                    .executeAsync(new ApiCallback<GetUserPresenceStatuses200Response>() {
                        @Override
                        public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                            // Log error but continue - this is not critical functionality
                            Log.e("FastCommentsSDK", "Failed to get user presence statuses: " + e.getMessage());
                        }

                        @Override
                        public void onSuccess(GetUserPresenceStatuses200Response result, int statusCode, Map<String, List<String>> responseHeaders) {
                            if (result.getActualInstance() instanceof APIError) {
                                // Log error but continue
                                Log.e("FastCommentsSDK", "API error when getting user presence statuses: " +
                                        ((APIError) result.getActualInstance()).getReason());
                                return;
                            }

                            // Process presence statuses
                            final Map<String, Boolean> statuses = result.getGetUserPresenceStatusesResponse().getUserIdsOnline();
                            mainHandler.post(() -> {
                                for (Map.Entry<String, Boolean> entry : statuses.entrySet()) {
                                    String userId = entry.getKey();
                                    boolean isOnline = entry.getValue();
                                    commentsTree.updateUserPresence(userId, isOnline);
                                }
                            });
                        }

                        @Override
                        public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
                            // Not used
                        }

                        @Override
                        public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
                            // Not used
                        }
                    });
        } catch (ApiException e) {
            // Log error but continue - this is not critical functionality
            Log.e("FastCommentsSDK", "Failed to get user presence statuses: " + e.getMessage());
        }
    }

    /**
     * Check if comments can be seen based on our filtering/blocking logic
     */
    private void checkCommentVisibility(List<String> commentIds, Consumer<Map<String, String>> resultCallback) {
        // For now, we'll assume all comments are visible
        // This can be enhanced later with visibility checking logic if needed
        resultCallback.accept(null);
    }

    /**
     * Handle a live event from the FastComments WebSocket
     */
    private void handleLiveEvent(LiveEvent eventData) {
        // Skip events from our own broadcasts
        if (eventData.getBroadcastId() != null && broadcastIdsSent.contains(eventData.getBroadcastId())) {
            broadcastIdsSent.remove(eventData.getBroadcastId());
            return;
        }

        try {
            LiveEventType eventType = eventData.getType();

            if (eventType == null) {
                return;
            }

            mainHandler.post(() -> {
                switch (eventType) {
                    case NEW_COMMENT:
                        handleNewComment(eventData);
                        break;
                    case UPDATED_COMMENT:
                        handleUpdatedComment(eventData);
                        break;
                    case DELETED_COMMENT:
                        handleDeletedComment(eventData);
                        break;
                    case NEW_VOTE:
                        handleNewVote(eventData);
                        break;
                    case DELETED_VOTE:
                        handleDeletedVote(eventData);
                        break;
                    case THREAD_STATE_CHANGE:
                        handleThreadStateChange(eventData);
                        break;
                    case P_U:
                        handlePresenceChange(eventData);
                        break;
                    case UPDATE_BADGES:
                        handleBadgeUpdate(eventData);
                        break;
                    default:
                        // Ignore other event types for now
                        break;
                }
            });
        } catch (Exception e) {
            System.err.println("FastComments: Error handling live event: " + e.getMessage());
        }
    }

    /**
     * Handle a new comment event
     */
    private void handleNewComment(LiveEvent eventData) {
        if (eventData.getComment() == null) {
            return;
        }

        // Get the comment from the event
        PubSubComment pubSubComment = eventData.getComment();

        // Convert PubSubComment to PublicComment for the CommentsTree
        PublicComment newComment = new PublicComment();
        copyEventToComment(pubSubComment, newComment);

        // Determine if we should show comments immediately based on config
        boolean showLiveRightAway = config.showLiveRightAway != null && config.showLiveRightAway;

        // Add to comments tree
        commentsTree.addComment(newComment, showLiveRightAway);

        // Increment the server comment count
        commentCountOnServer++;
    }

    public void copyEventToComment(PubSubComment pubSubComment, PublicComment comment) {
        comment.setId(pubSubComment.getId());
        comment.setCommentHTML(pubSubComment.getCommentHTML());
        comment.setCommenterName(pubSubComment.getCommenterName());

        // Handle date conversion to OffsetDateTime
        try {
            // Get the ISO-8601 date string from pubSubComment
            String dateString = pubSubComment.getDate();
            if (dateString != null && !dateString.isEmpty()) {
                // Parse ISO-8601 string to OffsetDateTime
                java.time.OffsetDateTime offsetDateTime = java.time.OffsetDateTime.parse(dateString);
                comment.setDate(offsetDateTime);
            }
        } catch (Exception e) {
            System.err.println("FastComments: Error converting date: " + e.getMessage());
        }

        comment.setUserId(pubSubComment.getUserId());
        comment.setParentId(pubSubComment.getParentId());
        comment.setVotesUp(pubSubComment.getVotesUp());
        comment.setVotesDown(pubSubComment.getVotesDown());
        comment.setAvatarSrc(pubSubComment.getAvatarSrc());
        comment.setVerified(pubSubComment.getVerified());
    }

    /**
     * Handle an updated comment event
     */
    private void handleUpdatedComment(LiveEvent eventData) {
        if (eventData.getComment() == null) {
            return;
        }

        // Get the comment from the event
        PubSubComment pubSubComment = eventData.getComment();

        // Find and update the comment in our tree
        PublicComment existingComment = commentsTree.getPublicComment(pubSubComment.getId());
        if (existingComment != null) {
            copyEventToComment(pubSubComment, existingComment);
        }
    }

    /**
     * Handle a deleted comment event
     */
    private void handleDeletedComment(LiveEvent eventData) {
        if (eventData.getComment() == null) {
            return;
        }

        // Get the comment ID from the event
        String commentId = eventData.getComment().getId();

        // Remove the comment from our tree
        boolean removed = commentsTree.removeComment(commentId);

        // Decrement the server comment count if successfully removed
        if (removed && commentCountOnServer > 0) {
            commentCountOnServer--;
        }
    }

    /**
     * Handle a new vote event
     */
    private void handleNewVote(LiveEvent eventData) {
        if (eventData.getVote() == null) {
            return;
        }

        // Get vote data using the appropriate type
        PubSubVote vote = eventData.getVote();
        String commentId = vote.getCommentId();
        Integer direction = vote.getDirection();

        if (commentId == null) {
            return;
        }

        final boolean isUpvote = direction > 0;

        // Find and update the comment's vote count
        final RenderableComment renderableComment = commentsTree.commentsById.get(commentId);
        if (renderableComment != null) {
            final PublicComment comment = renderableComment.getComment();
            if (isUpvote) {
                comment.setVotesUp((comment.getVotesUp() != null ? comment.getVotesUp() : 0) + 1);
            } else {
                comment.setVotesDown((comment.getVotesDown() != null ? comment.getVotesDown() : 0) + 1);
            }
            commentsTree.notifyItemChanged(renderableComment);
        }
    }

    /**
     * Handle a deleted vote event
     */
    private void handleDeletedVote(LiveEvent eventData) {
        if (eventData.getVote() == null) {
            return;
        }

        // Get vote data using the appropriate type
        PubSubVote vote = eventData.getVote();
        String commentId = vote.getCommentId();
        Integer direction = vote.getDirection();

        if (commentId == null) {
            return;
        }

        final boolean isUpvote = direction > 0;

        // Find and update the comment's vote count
        final RenderableComment renderableComment = commentsTree.commentsById.get(commentId);
        if (renderableComment != null) {
            final PublicComment comment = renderableComment.getComment();
            if (isUpvote && comment.getVotesUp() != null && comment.getVotesUp() > 0) {
                comment.setVotesUp(comment.getVotesUp() - 1);
            } else if (!isUpvote && comment.getVotesDown() != null && comment.getVotesDown() > 0) {
                comment.setVotesDown(comment.getVotesDown() - 1);
            }
            commentsTree.notifyItemChanged(renderableComment);
        }
    }

    /**
     * Handle a thread state change event (e.g., thread locked)
     */
    private void handleThreadStateChange(LiveEvent eventData) {
        if (eventData.getIsClosed() != null) {
            this.isClosed = eventData.getIsClosed();
        }
    }

    /**
     * Handle a presence change event (users coming online or going offline)
     */
    private void handlePresenceChange(LiveEvent eventData) {
        // Process users who joined (came online)
        List<String> usersJoined = eventData.getUj();
        if (usersJoined != null && !usersJoined.isEmpty()) {
            for (String userId : usersJoined) {
                commentsTree.updateUserPresence(userId, true);
            }
        }

        // Process users who left (went offline)
        List<String> usersLeft = eventData.getUl();
        if (usersLeft != null && !usersLeft.isEmpty()) {
            for (String userId : usersLeft) {
                commentsTree.updateUserPresence(userId, false);
            }
        }
    }

    /**
     * Cleanup resources, including WebSocket connections
     */
    public void cleanup() {
        if (liveEventSubscription != null) {
            liveEventSubscription.close();
            liveEventSubscription = null;
        }
    }
    
    /**
     * Handle badge update events
     */
    private void handleBadgeUpdate(LiveEvent eventData) {
        if (eventData.getBadges() == null || eventData.getBadges().isEmpty() || eventData.getUserId() == null) {
            return;
        }
        
        String userId = eventData.getUserId();
        boolean isCurrentUser = (currentUser != null && 
                currentUser.getId() != null && 
                currentUser.getId().equals(userId));
        
        // Get all comments by this user
        List<RenderableComment> userComments = commentsTree.commentsByUserId.get(userId);
        if (userComments != null && !userComments.isEmpty()) {
            // Check for new badges by comparing with existing badges
            List<CommentUserBadgeInfo> newBadges = new ArrayList<>();
            
            // Use the first comment to check which badges are new
            // (All user's comments should have the same badges)
            RenderableComment firstComment = userComments.get(0);
            List<CommentUserBadgeInfo> existingBadges = firstComment.getComment().getBadges();
            
            // Determine which badges are new
            for (CommentUserBadgeInfo updatedBadge : eventData.getBadges()) {
                boolean isNewBadge = true;
                
                if (existingBadges != null) {
                    for (CommentUserBadgeInfo existingBadge : existingBadges) {
                        if (existingBadge.getId().equals(updatedBadge.getId())) {
                            isNewBadge = false;
                            break;
                        }
                    }
                }
                
                if (isNewBadge) {
                    newBadges.add(updatedBadge);
                }
            }
            
            // Update all comments by this user with the new badges
            for (RenderableComment comment : userComments) {
                comment.getComment().setBadges(eventData.getBadges());
                commentsTree.notifyItemChanged(comment);
            }
            
            // Show badge award dialogs if this is for the current user and we have new badges
            if (isCurrentUser && !newBadges.isEmpty()) {
                // Use the current user's context (need to get through the adapter)
                CommentsAdapter adapter = commentsTree.getAdapter();
                if (adapter != null && adapter.getContext() != null) {
                    Context context = adapter.getContext();
                    // Queue up dialog for each new badge
                    for (CommentUserBadgeInfo badge : newBadges) {
                        showBadgeAwardDialog(context, badge);
                    }
                }
            }
        } else {
            // If no existing comments to compare with, just set the badges
            // and show all badges if it's the current user (they might be all new)
            if (isCurrentUser) {
                CommentsAdapter adapter = commentsTree.getAdapter();
                if (adapter != null && adapter.getContext() != null) {
                    Context context = adapter.getContext();
                    // Show all badges in case they're all new
                    for (CommentUserBadgeInfo badge : eventData.getBadges()) {
                        showBadgeAwardDialog(context, badge);
                    }
                }
            }
        }
    }
    
    /**
     * Shows a dialog when the current user is awarded a badge
     */
    private void showBadgeAwardDialog(Context context, CommentUserBadgeInfo badge) {
        if (badge == null) {
            return;
        }
        
        mainHandler.post(() -> {
            BadgeAwardDialog dialog = new BadgeAwardDialog(context);
            dialog.show(badge);
        });
    }

    /**
     * Refresh the live events connection
     * Call this when the app returns from background or after a network reconnection
     */
    public void refreshLiveEvents() {
        if (tenantIdWS != null && urlIdWS != null && userIdWS != null) {
            subscribeToLiveEvents();
        }
    }
    
    /**
     * Edit a comment
     *
     * @param commentId    The ID of the comment to edit
     * @param newText      The new text for the comment
     * @param callback     Callback to receive the response
     */
    public void editComment(String commentId, String newText, final FCCallback<PickFCommentApprovedOrCommentHTML> callback) {
        if (commentId == null || commentId.isEmpty()) {
            callback.onFailure(new APIError()
                    .status(ImportedAPIStatusFAILED.FAILED)
                    .reason("Comment ID is required")
                    .code("invalid_comment_id"));
            return;
        }
        
        if (newText == null || newText.trim().isEmpty()) {
            callback.onFailure(new APIError()
                    .status(ImportedAPIStatusFAILED.FAILED)
                    .reason("Comment text is required")
                    .code("empty_comment"));
            return;
        }
        
        // Create a unique broadcast ID
        String broadcastId = UUID.randomUUID().toString();
        
        // Track broadcast ID before sending
        broadcastIdsSent.add(broadcastId);
        
        // Create comment text update request
        CommentTextUpdateRequest updateRequest = new CommentTextUpdateRequest();
        updateRequest.comment(newText);
        
        try {
            // Make the API call
            api.setCommentText(config.tenantId, commentId, broadcastId, editKey, updateRequest)
                    .sso(config.getSSOToken())
                    .executeAsync(new ApiCallback<SetCommentText200Response>() {
                        @Override
                        public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                            callback.onFailure(CallbackWrapper.createErrorFromException(e));
                        }

                        @Override
                        public void onSuccess(SetCommentText200Response result, int statusCode, Map<String, List<String>> responseHeaders) {
                            if (result.getActualInstance() instanceof APIError) {
                                callback.onFailure((APIError) result.getActualInstance());
                            } else {
                                PublicAPISetCommentTextResponse response = result.getPublicAPISetCommentTextResponse();
                                if (response.getComment() != null) {
                                    callback.onSuccess(response.getComment());
                                } else {
                                    callback.onFailure(new APIError()
                                            .status(ImportedAPIStatusFAILED.FAILED)
                                            .reason("No comment returned")
                                            .code("edit_comment_error"));
                                }
                            }
                        }

                        @Override
                        public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
                            // Not used
                        }

                        @Override
                        public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
                            // Not used
                        }
                    });
        } catch (ApiException e) {
            CallbackWrapper.handleAPIException(mainHandler, callback, e);
        }
    }
    
    /**
     * Flag a comment
     *
     * @param commentId    The ID of the comment to flag
     * @param reason       The reason for flagging the comment
     * @param callback     Callback to receive the response
     */
    public void flagComment(String commentId, final FCCallback<BlockSuccess> callback) {
        if (commentId == null || commentId.isEmpty()) {
            callback.onFailure(new APIError()
                    .status(ImportedAPIStatusFAILED.FAILED)
                    .reason("Comment ID is required")
                    .code("invalid_comment_id"));
            return;
        }
        
        try {
            // Make the API call
            api.flagComment(config.tenantId, commentId, true)
                    .sso(config.getSSOToken())
                    .executeAsync(new ApiCallback<FlagComment200Response>() {
                        @Override
                        public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                            callback.onFailure(CallbackWrapper.createErrorFromException(e));
                        }

                        @Override
                        public void onSuccess(FlagComment200Response result, int statusCode, Map<String, List<String>> responseHeaders) {
                            if (result.getActualInstance() instanceof APIError) {
                                callback.onFailure((APIError) result.getActualInstance());
                            } else {
                                BlockSuccess response = result.getBlockSuccess();
                                callback.onSuccess(response);
                            }
                        }

                        @Override
                        public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
                            // Not used
                        }

                        @Override
                        public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
                            // Not used
                        }
                    });
        } catch (ApiException e) {
            CallbackWrapper.handleAPIException(mainHandler, callback, e);
        }
    }
    
    /**
     * Block a user based on their comment
     *
     * @param commentId    The ID of the comment to block the user from
     * @param callback     Callback to receive the response
     */
    public void blockUserFromComment(String commentId, final FCCallback<BlockSuccess> callback) {
        if (commentId == null || commentId.isEmpty()) {
            callback.onFailure(new APIError()
                    .status(ImportedAPIStatusFAILED.FAILED)
                    .reason("Comment ID is required")
                    .code("invalid_comment_id"));
            return;
        }
        
        try {
            // Make the API call
            api.blockFromComment(config.tenantId, commentId)
                    .sso(config.getSSOToken())
                    .executeAsync(new ApiCallback<BlockFromComment200Response>() {
                        @Override
                        public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                            callback.onFailure(CallbackWrapper.createErrorFromException(e));
                        }

                        @Override
                        public void onSuccess(BlockFromComment200Response result, int statusCode, Map<String, List<String>> responseHeaders) {
                            if (result.getActualInstance() instanceof APIError) {
                                callback.onFailure((APIError) result.getActualInstance());
                            } else {
                                BlockSuccess response = result.getBlockSuccess();
                                callback.onSuccess(response);
                            }
                        }

                        @Override
                        public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
                            // Not used
                        }

                        @Override
                        public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
                            // Not used
                        }
                    });
        } catch (ApiException e) {
            CallbackWrapper.handleAPIException(mainHandler, callback, e);
        }
    }

    /**
     * Vote on a comment (upvote or downvote) with anonymous user credentials
     *
     * @param commentId      The ID of the comment to vote on
     * @param isUpvote       True for upvote, false for downvote
     * @param commenterName  Name for anonymous user (optional, can be null if user is authenticated)
     * @param commenterEmail Email for anonymous user (optional, can be null if user is authenticated)
     * @param callback       Callback to receive the response
     */
    public void voteComment(String commentId, boolean isUpvote, String commenterName, String commenterEmail, final FCCallback<VoteResponse> callback) {
        if (commentId == null || commentId.isEmpty()) {
            callback.onFailure(new APIError().status(ImportedAPIStatusFAILED.FAILED).reason("Comment ID is required").code("invalid_comment_id"));
            return;
        }

        // Create a unique broadcast ID
        String broadcastId = UUID.randomUUID().toString();

        // Track broadcast ID before sending
        broadcastIdsSent.add(broadcastId);

        // Create vote parameters
        VoteBodyParams voteParams = new VoteBodyParams()
                .voteDir(isUpvote ? VoteBodyParams.VoteDirEnum.UP : VoteBodyParams.VoteDirEnum.DOWN);

        // Set user info based on what's available
        if (currentUser != null && currentUser.getAuthorized() != null && currentUser.getAuthorized()) {
            // User is authenticated, API will use their session info
        } else if (commenterName != null && !commenterName.isEmpty()) {
            // Using provided anonymous credentials
            voteParams.commenterName(commenterName);

            if (commenterEmail != null && !commenterEmail.isEmpty()) {
                voteParams.commenterEmail(commenterEmail);
            }
        }

        // Set URL if available
        if (config.url != null && !config.url.isEmpty()) {
            voteParams.url(config.url);
        }

        try {
            // Make the API call
            api.voteComment(config.tenantId, commentId, config.urlId, broadcastId, voteParams)
                    .sso(config.getSSOToken())
                    .executeAsync(new ApiCallback<VoteComment200Response>() {
                        @Override
                        public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                            callback.onFailure(CallbackWrapper.createErrorFromException(e));
                        }

                        @Override
                        public void onSuccess(VoteComment200Response result, int statusCode, Map<String, List<String>> responseHeaders) {
                            if (result.getActualInstance() instanceof APIError) {
                                callback.onFailure((APIError) result.getActualInstance());
                            } else {
                                VoteResponse response = result.getVoteResponse();
                                callback.onSuccess(response);
                            }
                        }

                        @Override
                        public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
                            // Not used
                        }

                        @Override
                        public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
                            // Not used
                        }
                    });
        } catch (ApiException e) {
            CallbackWrapper.handleAPIException(mainHandler, callback, e);
        }
    }
}