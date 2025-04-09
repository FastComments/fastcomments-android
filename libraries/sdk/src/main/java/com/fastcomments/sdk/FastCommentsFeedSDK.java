package com.fastcomments.sdk;

import static com.fastcomments.model.LiveEventType.NEW_FEED_POST;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.fastcomments.api.DefaultApi;
import com.fastcomments.api.PublicApi;
import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.invoker.ApiCallback;
import com.fastcomments.invoker.ApiException;
import com.fastcomments.model.APIError;
import com.fastcomments.model.CreateFeedPostParams;
import com.fastcomments.model.CreateFeedPostPublic200Response;
import com.fastcomments.model.CreateFeedPostResponse;
import com.fastcomments.model.FeedPost;
import com.fastcomments.model.GetFeedPostsPublic200Response;
import com.fastcomments.model.GetFeedPostsResponse;
import com.fastcomments.model.LiveEvent;
import com.fastcomments.model.LiveEventType;
import com.fastcomments.model.PublicFeedPostsResponse;
import com.fastcomments.model.UserSessionInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * SDK class for handling FastComments Feed functionality
 */
public class FastCommentsFeedSDK {

    private UserSessionInfo currentUser;
    private final CommentWidgetConfig config;
    private final PublicApi api;
    private final Handler mainHandler;

    private List<FeedPost> feedPosts = new ArrayList<>();
    public boolean hasMore = false;
    public String lastPostId = null; // Used for cursor-based pagination with afterId
    public int pageSize = 10;
    public String blockingErrorMessage = null;
    public Set<String> broadcastIdsSent;
    public int newPostsCount = 0;

    private com.fastcomments.pubsub.SubscribeToChangesResult liveEventSubscription;
    private final com.fastcomments.pubsub.LiveEventSubscriber liveEventSubscriber;
    private String tenantIdWS;
    private String urlIdWS;
    private String userIdWS;

    /**
     * Constructs a FastCommentsFeedSDK instance with the given configuration
     *
     * @param config CommentWidgetConfig object containing credentials and other settings
     */
    public FastCommentsFeedSDK(CommentWidgetConfig config) {
        this.api = new PublicApi();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.config = config;
        this.api.getApiClient().setBasePath(FastCommentsSDK.getAPIBasePath(config));
        this.broadcastIdsSent = new HashSet<>(0);
        this.liveEventSubscriber = new com.fastcomments.pubsub.LiveEventSubscriber();
    }

    /**
     * When user information is available, this method should be called to set the current user
     *
     * @param userInfo The user information to set
     */
    public void setCurrentUser(UserSessionInfo userInfo) {
        this.currentUser = userInfo;
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
     * Get the list of feed posts
     *
     * @return List of FeedPost objects
     */
    public List<FeedPost> getFeedPosts() {
        return feedPosts;
    }

    /**
     * Initial load method that fetches the first page of feed posts
     *
     * @param callback Callback to receive the response
     */
    public void load(FCCallback<PublicFeedPostsResponse> callback) {
        // Reset pagination for initial load
        lastPostId = null;  // Reset the cursor for pagination

        // Reset any existing error message
        blockingErrorMessage = null;

        // Reset new posts count
        newPostsCount = 0;

        loadFeedPosts(new FCCallback<PublicFeedPostsResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                callback.onFailure(error);
                return CONSUME;
            }

            @Override
            public boolean onSuccess(PublicFeedPostsResponse response) {
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
     * Loads feed posts with the current pagination state
     *
     * @param callback Callback to receive the response
     */
    private void loadFeedPosts(FCCallback<PublicFeedPostsResponse> callback) {
        try {
            api.getFeedPostsPublic(config.tenantId)
                    .afterId(lastPostId)
                    .limit(pageSize)
                    .executeAsync(new ApiCallback<GetFeedPostsPublic200Response>() {
                        @Override
                        public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                            APIError error = CallbackWrapper.createErrorFromException(e);
                            if (error.getTranslatedError() != null && !error.getTranslatedError().isEmpty()) {
                                blockingErrorMessage = error.getTranslatedError();
                            } else if (error.getReason() != null && !error.getReason().isEmpty()) {
                                blockingErrorMessage = "Feed could not load! Details: " + error.getReason();
                            }
                            callback.onFailure(error);
                        }

                        @Override
                        public void onSuccess(GetFeedPostsPublic200Response response, int statusCode, Map<String, List<String>> responseHeaders) {
                            if (response.getActualInstance() instanceof APIError) {
                                APIError error = (APIError) response.getActualInstance();
                                if (error.getTranslatedError() != null && !error.getTranslatedError().isEmpty()) {
                                    blockingErrorMessage = error.getTranslatedError();
                                } else if (error.getReason() != null && !error.getReason().isEmpty()) {
                                    blockingErrorMessage = "Feed could not load! Details: " + error.getReason();
                                }
                                callback.onFailure(error);
                            } else {
                                mainHandler.post(() -> {
                                    final List<FeedPost> posts = response.getPublicFeedPostsResponse().getFeedPosts();

                                    // Only clear the list if this is an initial load (no lastPostId)
                                    // This ensures we don't clear when paginating or loading more
                                    if (lastPostId == null) {
                                        feedPosts.clear();
                                    }

                                    if (!posts.isEmpty()) {
                                        feedPosts.addAll(posts);

                                        // Update lastPostId for pagination if we have posts
                                        FeedPost lastPost = posts.get(posts.size() - 1);
                                        lastPostId = lastPost.getId();
                                    }

                                    // Check if there are more posts to load
                                    // If we got posts back and size equals page size, assume more posts are available
                                    hasMore = !posts.isEmpty() && posts.size() >= pageSize;

                                    callback.onSuccess(response.getPublicFeedPostsResponse());
                                });
                            }
                        }

                        @Override
                        public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
                        }

                        @Override
                        public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
                        }
                    });
        } catch (ApiException e) {
            CallbackWrapper.handleAPIException(mainHandler, callback, e);
        }
    }

    /**
     * Load more feed posts (next page)
     * This uses cursor-based pagination with the lastPostId
     *
     * @param callback Callback to receive the response
     */
    public void loadMore(FCCallback<PublicFeedPostsResponse> callback) {
        if (!hasMore) {
            APIError error = new APIError();
            error.setReason("No more posts to load");
            callback.onFailure(error);
            return;
        }

        loadFeedPosts(new FCCallback<PublicFeedPostsResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                callback.onFailure(error);
                return CONSUME;
            }

            @Override
            public boolean onSuccess(PublicFeedPostsResponse response) {
                callback.onSuccess(response);
                return CONSUME;
            }
        });
    }

    /**
     * Refresh the feed by loading the most recent posts
     *
     * @param callback Callback to receive the response
     */
    public void refresh(FCCallback<PublicFeedPostsResponse> callback) {
        lastPostId = null;  // Reset cursor-based pagination
        loadFeedPosts(callback);
    }

    /**
     * Like a feed post
     *
     * @param postId   The ID of the post to like
     * @param callback Callback to receive the response
     */
    public void likePost(String postId, FCCallback<APIError> callback) {
        // This is a placeholder for the like functionality
        // In a real implementation, this would call the API to like a post
        // For now, we'll just return success
        mainHandler.post(() -> {
            callback.onSuccess(null);
        });
    }

    /**
     * Clean up any resources used by the SDK
     */
    public void cleanup() {
        // Close WebSocket connection if active
        if (liveEventSubscription != null) {
            liveEventSubscription.close();
            liveEventSubscription = null;
        }
    }

    /**
     * Creates a FastCommentsSDK instance configured for a specific post's comments
     *
     * @param post The feed post to create a comments SDK for
     * @return A configured FastCommentsSDK instance ready to display comments for this post
     */
    public FastCommentsSDK createCommentsSDKForPost(FeedPost post) {
        // Start with the same tenant ID from the feed SDK
        com.fastcomments.core.CommentWidgetConfig config = new com.fastcomments.core.CommentWidgetConfig();
        config.tenantId = this.config.tenantId;

        // Set URL ID to the post ID - this is how comments are associated with the post
        config.urlId = "post:" + post.getId();

        // Set page title if available
        if (post.getTitle() != null) {
            config.pageTitle = post.getTitle();
        } else if (post.getContentHTML() != null) {
            // Use start of content as title if no title is available
            String contentText = android.text.Html.fromHtml(post.getContentHTML(),
                    android.text.Html.FROM_HTML_MODE_COMPACT).toString();
            // Limit to 100 characters
            if (contentText.length() > 100) {
                contentText = contentText.substring(0, 97) + "...";
            }
            config.pageTitle = contentText;
        }

        // Copy SSO token if available
        if (this.config.sso != null) {
            config.sso = this.config.sso;
        }

        // Create a new FastCommentsSDK with this config
        return new FastCommentsSDK(config);
    }

    /**
     * Creates a new feed post
     *
     * @param params   The CreateFeedPostParams containing post data
     * @param callback Callback to receive the response
     */
    public void createPost(CreateFeedPostParams params, FCCallback<FeedPost> callback) {
        try {
            // Create a unique broadcast ID to identify this post in live events
            String broadcastId = UUID.randomUUID().toString();

            // Track broadcast ID before sending
            broadcastIdsSent.add(broadcastId);

            api.createFeedPostPublic(config.tenantId, params)
                    .broadcastId(broadcastId)
                    .sso(config.sso)
                    .executeAsync(new ApiCallback<CreateFeedPostPublic200Response>() {
                        @Override
                        public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                            APIError error = CallbackWrapper.createErrorFromException(e);
                            callback.onFailure(error);
                        }

                        @Override
                        public void onSuccess(CreateFeedPostPublic200Response response, int statusCode, Map<String, List<String>> responseHeaders) {
                            mainHandler.post(() -> {
                                if (response.getActualInstance() instanceof APIError) {
                                    APIError error = (APIError) response.getActualInstance();
                                    callback.onFailure(error);
                                } else {
                                    CreateFeedPostResponse feedResponse = response.getCreateFeedPostResponse();
                                    FeedPost createdPost = feedResponse.getFeedPost();

                                    // Add post to the local list at the beginning
                                    if (feedPosts.isEmpty()) {
                                        feedPosts.add(createdPost);
                                    } else {
                                        feedPosts.add(0, createdPost); // Add at the beginning
                                    }

                                    callback.onSuccess(createdPost);
                                }
                            });
                        }

                        @Override
                        public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
                        }

                        @Override
                        public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
                        }
                    });
        } catch (ApiException e) {
            CallbackWrapper.handleAPIException(mainHandler, callback, e);
        }
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
            Log.e("FastCommentsFeedSDK", "Missing WebSocket parameters, live feed disabled");
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
                this::checkPostVisibility,
                this::handleLiveEvent
        );
    }

    /**
     * Handle WebSocket connection status changes
     */
    private void handleConnectionStatusChange(boolean isConnected, Long lastEventTime) {
        // You could add logic here to handle connection status changes
        // For example, show/hide a "connected" indicator
    }

    /**
     * Check if posts can be seen based on filtering/blocking logic
     */
    private void checkPostVisibility(List<String> postIds, java.util.function.Consumer<Map<String, String>> resultCallback) {
        // For now, we'll assume all posts are visible
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
            final LiveEventType eventType = eventData.getType();

            if (eventType == null) {
                return;
            }

            mainHandler.post(() -> {
                // Ignore other event types for now
                if (eventType == NEW_FEED_POST) {
                    handleNewFeedPost(eventData);
                }
            });
        } catch (Exception e) {
            System.err.println("FastComments: Error handling live event: " + e.getMessage());
        }
    }

    /**
     * Handle a new feed post event
     */
    private void handleNewFeedPost(LiveEvent eventData) {
        if (eventData.getFeedPost() == null) {
            return;
        }

        // Increment new posts count
        newPostsCount++;
    }

    /**
     * Handle an updated feed post event
     */
    private void handleUpdatedFeedPost(LiveEvent eventData) {
        if (eventData.getFeedPost() == null) {
            return;
        }

        // Get the feed post from the event
        String postId = eventData.getFeedPost().getId();

        // Update the post in our list if it exists
        for (int i = 0; i < feedPosts.size(); i++) {
            FeedPost post = feedPosts.get(i);
            if (post.getId() != null && post.getId().equals(postId)) {
                // If we find the post, we could update its properties here
                // This would require converting from PubSubFeedPost to FeedPost
                // For now, we'll just note that an update happened
                break;
            }
        }
    }

    /**
     * Handle a deleted feed post event
     */
    private void handleDeletedFeedPost(LiveEvent eventData) {
        if (eventData.getFeedPost() == null) {
            return;
        }

        // Get the feed post ID from the event
        String postId = eventData.getFeedPost().getId();

        // Remove the post from our list if it exists
        for (int i = 0; i < feedPosts.size(); i++) {
            FeedPost post = feedPosts.get(i);
            if (post.getId() != null && post.getId().equals(postId)) {
                feedPosts.remove(i);
                break;
            }
        }
    }

    /**
     * Load new feed posts that have come in since the initial load
     *
     * @param callback Callback to receive the response
     */
    public void loadNewPosts(FCCallback<PublicFeedPostsResponse> callback) {
        if (newPostsCount <= 0) {
            // No new posts to load
            callback.onSuccess(null);
            return;
        }

        // Reset new posts count
        newPostsCount = 0;

        // Reload from the beginning
        String originalLastPostId = lastPostId;
        lastPostId = null;

        loadFeedPosts(new FCCallback<PublicFeedPostsResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                // Restore original last post ID
                lastPostId = originalLastPostId;
                callback.onFailure(error);
                return CONSUME;
            }

            @Override
            public boolean onSuccess(PublicFeedPostsResponse response) {
                // The new posts are now loaded in feedPosts
                callback.onSuccess(response);
                return CONSUME;
            }
        });
    }
}