package com.fastcomments.sdk;

import static com.fastcomments.model.LiveEventType.NEW_FEED_POST;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.fastcomments.api.PublicApi;
import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.invoker.ApiCallback;
import com.fastcomments.invoker.ApiException;
import com.fastcomments.model.APIEmptyResponse;
import com.fastcomments.model.APIError;
import com.fastcomments.model.CreateFeedPostParams;
import com.fastcomments.model.CreateFeedPostPublic200Response;
import com.fastcomments.model.CreateFeedPostResponse;
import com.fastcomments.model.DeleteFeedPostPublic200Response;
import com.fastcomments.model.FeedPost;
import com.fastcomments.model.FeedPostMediaItem;
import com.fastcomments.model.FeedPostMediaItemAsset;
import com.fastcomments.model.FeedPostStats;
import com.fastcomments.model.FeedPostsStatsResponse;
import com.fastcomments.model.GetFeedPostsStats200Response;
import com.fastcomments.model.MediaAsset;
import com.fastcomments.model.SizePreset;
import com.fastcomments.model.UploadImageResponse;
import com.fastcomments.model.GetFeedPostsPublic200Response;
import com.fastcomments.model.LiveEvent;
import com.fastcomments.model.LiveEventType;
import com.fastcomments.model.PublicFeedPostsResponse;
import com.fastcomments.model.ReactBodyParams;
import com.fastcomments.model.ReactFeedPostPublic200Response;
import com.fastcomments.model.UserSessionInfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import android.net.Uri;
import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

import android.provider.OpenableColumns;
import android.database.Cursor;

/**
 * SDK class for handling FastComments Feed functionality
 */
public class FastCommentsFeedSDK {

    private UserSessionInfo currentUser;
    private final CommentWidgetConfig config;
    private final PublicApi api;
    private final Handler mainHandler;

    private List<FeedPost> feedPosts = new ArrayList<>();
    private Map<String, FeedPost> postsById = new HashMap<>(); // Map for quick lookup by ID
    private Map<String, Integer> likeCounts = new HashMap<>(); // Map for tracking like counts
    public boolean hasMore = false;
    public String lastPostId = null; // Used for cursor-based pagination with afterId
    public int pageSize = 10;
    public String blockingErrorMessage = null;
    public Set<String> broadcastIdsSent;
    public int newPostsCount = 0;
    private Map<String, Map<String, Boolean>> myReacts = new HashMap<>(); // Map of postId to reaction types

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
        this.api.getApiClient().setLenientOnJson(true);
        this.broadcastIdsSent = new HashSet<>(0);
        this.liveEventSubscriber = new com.fastcomments.pubsub.LiveEventSubscriber();
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
     * A class to hold feed pagination and state information
     */
    public static class FeedState implements Serializable {
        private static final long serialVersionUID = 1L;

        private String lastPostId;
        private boolean hasMore;
        private int pageSize;
        private int newPostsCount;
        private List<FeedPost> feedPosts;
        private Map<String, Map<String, Boolean>> myReacts;
        private Map<String, Integer> likeCounts;

        public FeedState() {
            // Default constructor
        }

        // Getters and setters
        public String getLastPostId() {
            return lastPostId;
        }

        public void setLastPostId(String lastPostId) {
            this.lastPostId = lastPostId;
        }

        public boolean isHasMore() {
            return hasMore;
        }

        public void setHasMore(boolean hasMore) {
            this.hasMore = hasMore;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public int getNewPostsCount() {
            return newPostsCount;
        }

        public void setNewPostsCount(int newPostsCount) {
            this.newPostsCount = newPostsCount;
        }

        public List<FeedPost> getFeedPosts() {
            return feedPosts;
        }

        public void setFeedPosts(List<FeedPost> feedPosts) {
            this.feedPosts = feedPosts;
        }

        public Map<String, Map<String, Boolean>> getMyReacts() {
            return myReacts;
        }

        public void setMyReacts(Map<String, Map<String, Boolean>> myReacts) {
            this.myReacts = myReacts;
        }

        public Map<String, Integer> getLikeCounts() {
            return likeCounts;
        }

        public void setLikeCounts(Map<String, Integer> likeCounts) {
            this.likeCounts = likeCounts;
        }
    }

    /**
     * Save the current pagination state for later restoration
     *
     * @return FeedState containing all pagination and content state
     */
    public FeedState savePaginationState() {
        FeedState state = new FeedState();
        state.setLastPostId(lastPostId);
        state.setHasMore(hasMore);
        state.setPageSize(pageSize);
        state.setNewPostsCount(newPostsCount);
        state.setFeedPosts(new ArrayList<>(feedPosts));
        state.setMyReacts(new HashMap<>(myReacts));
        state.setLikeCounts(new HashMap<>(likeCounts));
        return state;
    }

    /**
     * Restore the pagination state from a previously saved state
     *
     * @param state The FeedState to restore from
     */
    public void restorePaginationState(FeedState state) {
        if (state == null) {
            return;
        }

        this.lastPostId = state.getLastPostId();
        this.hasMore = state.isHasMore();
        this.pageSize = state.getPageSize();
        this.newPostsCount = state.getNewPostsCount();

        // Restore feed posts if available
        if (state.getFeedPosts() != null) {
            this.feedPosts.clear();
            this.feedPosts.addAll(state.getFeedPosts());

            // Rebuild postsById map
            this.postsById.clear();
            for (FeedPost post : state.getFeedPosts()) {
                if (post.getId() != null) {
                    this.postsById.put(post.getId(), post);
                }
            }
        }

        // Restore reaction states if available
        if (state.getMyReacts() != null) {
            this.myReacts.clear();
            this.myReacts.putAll(state.getMyReacts());
        }

        // Restore like counts if available  
        if (state.getLikeCounts() != null) {
            this.likeCounts.clear();
            this.likeCounts.putAll(state.getLikeCounts());
        }
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

        loadFeedPosts(callback);
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
                    .sso(config.getSSOToken())
                    .includeUserInfo(lastPostId == null) // only include for initial req
                    .executeAsync(new ApiCallback<GetFeedPostsPublic200Response>() {
                        @Override
                        public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                            APIError error = CallbackWrapper.createErrorFromException(e);
                            if (error.getTranslatedError() != null && !error.getTranslatedError().isEmpty()) {
                                blockingErrorMessage = error.getTranslatedError();
                            } else if (error.getReason() != null && !error.getReason().isEmpty()) {
                                blockingErrorMessage = "Feed could not load! Details: " + error.getReason();
                            }

                            // Log the error, particularly for JsonSyntaxException
                            String errorMessage = "API Error: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
                            if (e.getCause() != null) {
                                errorMessage += " Cause: " + e.getCause().getClass().getSimpleName();
                                if (e.getCause().getMessage() != null) {
                                    errorMessage += " (" + e.getCause().getMessage() + ")";
                                }
                            }
                            Log.e("FastCommentsFeedSDK", errorMessage, e);

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

                                // Log this error case (API returned success but with error object)
                                Log.e("FastCommentsFeedSDK", "API returned success status but with error object: " +
                                        (error.getReason() != null ? error.getReason() : "unknown reason"));

                                callback.onFailure(error);
                            } else {
                                mainHandler.post(() -> {
                                    final PublicFeedPostsResponse publicResponse = response.getPublicFeedPostsResponse();

                                    boolean needsWebsocketReconnect = false;

                                    if (publicResponse.getUser() != null) {
                                        currentUser = publicResponse.getUser();
                                    }

                                    if (publicResponse.getTenantIdWS() != null) {
                                        tenantIdWS = publicResponse.getTenantIdWS();
                                    }

                                    if (publicResponse.getUrlIdWS() != null) {
                                        urlIdWS = publicResponse.getUrlIdWS();
                                    }

                                    if (publicResponse.getUserIdWS() != null) {
                                        // Check if userIdWS has changed, which requires WebSocket reconnection
                                        if (userIdWS != null && !Objects.equals(publicResponse.getUserIdWS(), userIdWS)) {
                                            needsWebsocketReconnect = true;
                                        }
                                        userIdWS = publicResponse.getUserIdWS();
                                    }

                                    // Subscribe to live events if we have all required parameters
                                    // or if we need to reconnect due to userIdWS change
                                    if ((tenantIdWS != null && urlIdWS != null && userIdWS != null) &&
                                            (liveEventSubscription == null || needsWebsocketReconnect)) {
                                        subscribeToLiveEvents();
                                    }

                                    final List<FeedPost> posts = publicResponse.getFeedPosts();

                                    // Process the myReacts from the response if available
                                    if (publicResponse.getMyReacts() != null) {
                                        // Only clear reactions if this is an initial load
                                        if (lastPostId == null) {
                                            myReacts.clear();
                                        }
                                        // Add all the myReacts for the posts
                                        myReacts.putAll(publicResponse.getMyReacts());
                                    }

                                    // Only clear the list if this is an initial load (no lastPostId)
                                    // This ensures we don't clear when paginating or loading more
                                    if (lastPostId == null) {
                                        feedPosts.clear();
                                        postsById.clear();
                                        likeCounts.clear();
                                    }

                                    if (!posts.isEmpty()) {
                                        // Add posts to list and maps
                                        for (FeedPost post : posts) {
                                            if (post.getId() != null) {
                                                // Store post by ID for quick lookup
                                                postsById.put(post.getId(), post);

                                                // Calculate initial like count from post's reacts
                                                if (post.getReacts() != null && post.getReacts().containsKey("l")) {
                                                    likeCounts.put(post.getId(), post.getReacts().get("l").intValue());
                                                } else {
                                                    likeCounts.put(post.getId(), 0);
                                                }
                                            }
                                        }

                                        // Add to main post list
                                        feedPosts.addAll(posts);

                                        // Update lastPostId for pagination if we have posts
                                        FeedPost lastPost = posts.get(posts.size() - 1);
                                        lastPostId = lastPost.getId();
                                    }

                                    // Check if there are more posts to load
                                    // If we got posts back and size equals page size, assume more posts are available
                                    hasMore = !posts.isEmpty() && posts.size() >= pageSize;

                                    callback.onSuccess(publicResponse);
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
     * Check if the current user has reacted to a post with a specific reaction type
     *
     * @param postId    The ID of the post to check
     * @param reactType The reaction type to check for (e.g., "l" for like)
     * @return true if the user has reacted with the specified type, false otherwise
     */
    public boolean hasUserReactedToPost(String postId, String reactType) {
        if (postId == null || reactType == null || myReacts.isEmpty()) {
            return false;
        }

        Map<String, Boolean> reactions = myReacts.get(postId);
        return reactions != null && reactions.containsKey(reactType) && Boolean.TRUE.equals(reactions.get(reactType));
    }

    /**
     * Get the like count for a post
     *
     * @param postId ID of the post
     * @return Like count or 0 if not found
     */
    public int getPostLikeCount(String postId) {
        if (postId == null) {
            return 0;
        }

        Integer count = likeCounts.get(postId);
        return count != null ? count : 0;
    }

    /**
     * Like a feed post
     *
     * @param postId   The ID of the post to like
     * @param callback Callback to receive the response
     */
    public void likePost(String postId, FCCallback<FeedPost> callback) {
        try {
            // Get post from lookup map
            FeedPost post = postsById.get(postId);

            if (post != null) {
                // Check if user has already liked the post to toggle
                boolean isUndo = hasUserReactedToPost(postId, "l");

                // Optimistically update UI while API call is in progress
                int currentLikes = getPostLikeCount(postId);
                if (isUndo) {
                    // Remove like - decrement count
                    if (currentLikes > 0) {
                        likeCounts.put(postId, currentLikes - 1);
                    }

                    // Update reaction status
                    Map<String, Boolean> reactions = myReacts.get(postId);
                    if (reactions != null) {
                        reactions.put("l", false);
                    }
                } else {
                    // Add like - increment count
                    likeCounts.put(postId, currentLikes + 1);

                    // Update reaction status
                    Map<String, Boolean> reactions = myReacts.get(postId);
                    if (reactions == null) {
                        reactions = new HashMap<>();
                        myReacts.put(postId, reactions);
                    }
                    reactions.put("l", true);
                }

                // Make a copy of the updated post to return
                final FeedPost updatedPost = post;

                // Now call the actual API to make the server-side update
                try {
                    ReactBodyParams reactParams = new ReactBodyParams();
                    reactParams.reactType("l");

                    api.reactFeedPostPublic(config.tenantId, postId, reactParams)
                            .sso(config.getSSOToken())
                            .isUndo(isUndo)
                            .executeAsync(new ApiCallback<ReactFeedPostPublic200Response>() {
                                @Override
                                public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                                    // API call failed - revert the optimistic update
                                    if (isUndo) {
                                        // Revert back to liked state
                                        likeCounts.put(postId, currentLikes);
                                        Map<String, Boolean> reactions = myReacts.get(postId);
                                        if (reactions != null) {
                                            reactions.put("l", true);
                                        }
                                    } else {
                                        // Revert back to unliked state
                                        likeCounts.put(postId, currentLikes);
                                        Map<String, Boolean> reactions = myReacts.get(postId);
                                        if (reactions != null) {
                                            reactions.put("l", false);
                                        }
                                    }

                                    mainHandler.post(() -> {
                                        APIError error = CallbackWrapper.createErrorFromException(e);
                                        callback.onFailure(error);
                                    });
                                }

                                @Override
                                public void onSuccess(ReactFeedPostPublic200Response result, int statusCode, Map<String, List<String>> responseHeaders) {
                                    // API call succeeded, our optimistic update was correct
                                    mainHandler.post(() -> {
                                        callback.onSuccess(updatedPost);
                                    });
                                }

                                @Override
                                public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
                                    // Not needed for this API call
                                }

                                @Override
                                public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
                                    // Not needed for this API call
                                }
                            });
                } catch (ApiException e) {
                    // Handle API exception
                    CallbackWrapper.handleAPIException(mainHandler, callback, e);
                }

                return;
            }

            // Post not found in the map
            mainHandler.post(() -> {
                APIError error = new APIError();
                error.setReason("Post not found");
                callback.onFailure(error);
            });
        } catch (Exception e) {
            mainHandler.post(() -> {
                APIError error = new APIError();
                error.setReason("Error toggling like: " + e.getMessage());
                callback.onFailure(error);
            });
        }
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
        // Clear listener references to prevent memory leaks
        this.onPostDeletedListener = null;
        
        // Clear collections to help GC
        if (feedPosts != null) {
            feedPosts.clear();
        }
        if (postsById != null) {
            postsById.clear();
        }
        if (likeCounts != null) {
            likeCounts.clear();
        }
        if (myReacts != null) {
            myReacts.clear();
        }
        if (broadcastIdsSent != null) {
            broadcastIdsSent.clear();
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
     * Uploads an image to the server with CrossPlatform preset
     *
     * @param context  Android context needed for file operations
     * @param imageUri URI of the image to upload
     * @param callback Callback to receive the uploaded media item
     */
    public void uploadImage(Context context, Uri imageUri, FCCallback<FeedPostMediaItem> callback) {
        try {
            if (imageUri == null) {
                APIError error = new APIError();
                error.setReason("Invalid image URI");
                callback.onFailure(error);
                return;
            }

            if (context == null) {
                APIError error = new APIError();
                error.setReason("Context must not be null");
                callback.onFailure(error);
                return;
            }

            // Create a temporary file from the URI
            final File imageFile;
            try {
                // Get filename from URI
                String fileName = getFileNameFromUri(context, imageUri);
                if (fileName == null) {
                    fileName = "image_" + System.currentTimeMillis();
                }

                // Get extension
                String extension = getFileExtension(fileName);
                if (extension == null || extension.isEmpty()) {
                    extension = "jpg"; // Default extension
                }

                // Create temp file
                File tempFile = File.createTempFile("upload_", "." + extension, context.getCacheDir());
                imageFile = tempFile;

                // Copy the image content from URI to the file
                copyUriToFile(context, imageUri, imageFile);
            } catch (IOException e) {
                APIError error = new APIError();
                error.setReason("Failed to prepare image for upload: " + e.getMessage());
                callback.onFailure(error);
                return;
            }

            if (!imageFile.exists()) {
                APIError error = new APIError();
                error.setReason("Failed to create image file for upload");
                callback.onFailure(error);
                return;
            }

            try {
                api.uploadImage(config.tenantId, imageFile)
                        .urlId("FEEDS")
                        .sizePreset(SizePreset.CROSS_PLATFORM)
                        .executeAsync(new ApiCallback<UploadImageResponse>() {
                            @Override
                            public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                                // Clean up the temp file
                                imageFile.delete();

                                APIError error = CallbackWrapper.createErrorFromException(e);
                                callback.onFailure(error);
                            }

                            @Override
                            public void onSuccess(UploadImageResponse result, int statusCode, Map<String, List<String>> responseHeaders) {
                                // Clean up the temp file
                                imageFile.delete();

                                mainHandler.post(() -> {
                                    if (result == null) {
                                        APIError error = new APIError();
                                        error.setReason("Empty response from server");
                                        callback.onFailure(error);
                                        return;
                                    }

                                    try {
                                        FeedPostMediaItem mediaItem = new FeedPostMediaItem();
                                        List<FeedPostMediaItemAsset> assets = new ArrayList<>();
                                        if (result.getMedia() != null) {
                                            for (MediaAsset media : result.getMedia()) {
                                                assets.add(
                                                        new FeedPostMediaItemAsset()
                                                                .h(media.getH())
                                                                .w(media.getW())
                                                                .src(media.getSrc())
                                                );
                                            }
                                        } else if (result.getUrl() != null) {
                                            assets.add(
                                                    new FeedPostMediaItemAsset()
                                                            .h(1000)
                                                            .w(1000)
                                                            .src(result.getUrl())
                                            );
                                        }

                                        mediaItem.setSizes(assets);

                                        callback.onSuccess(mediaItem);
                                    } catch (Exception e) {
                                        APIError error = new APIError();
                                        error.setReason("Failed to parse upload response: " + e.getMessage());
                                        callback.onFailure(error);
                                    }
                                });
                            }

                            @Override
                            public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
                                // Could notify about upload progress if needed
                            }

                            @Override
                            public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
                                // Not needed for upload
                            }
                        });
            } catch (ApiException e) {
                // Clean up the temp file
                imageFile.delete();

                CallbackWrapper.handleAPIException(mainHandler, callback, e);
            }
        } catch (Exception e) {
            APIError error = new APIError();
            error.setReason("Failed to upload image: " + e.getMessage());
            callback.onFailure(error);
        }
    }

    /**
     * Uploads multiple images with CrossPlatform preset
     *
     * @param context   Android context needed for file operations
     * @param imageUris List of image URIs to upload
     * @param callback  Callback to receive the uploaded media items
     */
    public void uploadImages(Context context, List<Uri> imageUris, FCCallback<List<FeedPostMediaItem>> callback) {
        if (imageUris == null || imageUris.isEmpty()) {
            APIError error = new APIError();
            error.setReason("No images to upload");
            callback.onFailure(error);
            return;
        }

        final List<FeedPostMediaItem> uploadedItems = new ArrayList<>();
        final AtomicReference<APIError> uploadError = new AtomicReference<>();
        int[] countRemaining = new int[]{imageUris.size()};

        for (Uri uri : imageUris) {
            uploadImage(context, uri, new FCCallback<FeedPostMediaItem>() {
                @Override
                public boolean onFailure(APIError error) {
                    uploadError.set(error);
                    countRemaining[0]--;
                    if (countRemaining[0] == 0) {
                        mainHandler.post(() -> callback.onFailure(error));
                    }
                    return CONSUME;
                }

                @Override
                public boolean onSuccess(FeedPostMediaItem mediaItem) {
                    synchronized (uploadedItems) {
                        uploadedItems.add(mediaItem);
                    }
                    countRemaining[0]--;
                    if (countRemaining[0] == 0) {
                        mainHandler.post(() -> callback.onSuccess(uploadedItems));
                    }
                    return CONSUME;
                }
            });
        }
    }

    /**
     * Get filename from URI
     */
    private String getFileNameFromUri(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e("FastCommentsFeedSDK", "Failed to get filename from URI", e);
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot >= 0) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return null;
    }

    /**
     * Copy content from URI to file
     */
    private void copyUriToFile(Context context, Uri uri, File destFile) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(destFile)) {
            if (inputStream == null) {
                throw new IOException("Failed to open input stream from URI");
            }
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }
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
                    .sso(config.getSSOToken())
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
                // Handle different types of live events
                if (eventType == NEW_FEED_POST) {
                    handleNewFeedPost(eventData);
                } else if (eventType == LiveEventType.DELETED_FEED_POST) {
                    handleDeletedFeedPost(eventData);
                } else if (eventType == LiveEventType.UPDATED_FEED_POST) {
                    handleUpdatedFeedPost(eventData);
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
     * Fetch post stats for specific posts to get updated comment counts and reactions
     *
     * @param postIds  List of post IDs to fetch stats for
     * @param callback Callback to receive the response
     */
    public void getFeedPostsStats(List<String> postIds, FCCallback<GetFeedPostsStats200Response> callback) {
        if (postIds == null || postIds.isEmpty()) {
            APIError error = new APIError();
            error.setReason("Post IDs are required");
            callback.onFailure(error);
            return;
        }

        try {
            api.getFeedPostsStats(config.tenantId, postIds)
                    .sso(config.getSSOToken())
                    .executeAsync(new ApiCallback<GetFeedPostsStats200Response>() {
                        @Override
                        public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                            callback.onFailure(CallbackWrapper.createErrorFromException(e));
                        }

                        @Override
                        public void onSuccess(GetFeedPostsStats200Response result, int statusCode, Map<String, List<String>> responseHeaders) {
                            if (result.getActualInstance() instanceof APIError) {
                                callback.onFailure((APIError) result.getActualInstance());
                            } else {
                                // Update cached posts with the new stats
                                final FeedPostsStatsResponse statsResponse = result.getFeedPostsStatsResponse();
                                Map<String, FeedPostStats> statsMap = statsResponse.getStats();

                                // Update posts in our cache
                                for (Map.Entry<String, FeedPostStats> entry : statsMap.entrySet()) {
                                    String postId = entry.getKey();
                                    FeedPostStats updatedStats = entry.getValue();

                                    // Find the post in our cache
                                    FeedPost cachedPost = postsById.get(postId);
                                    if (cachedPost != null) {
                                        // Update comment count
                                        cachedPost.setCommentCount(updatedStats.getCommentCount());

                                        // Update reactions
                                        cachedPost.setReacts(updatedStats.getReacts());

                                        // Update like count in our tracking map
                                        if (updatedStats.getReacts() != null && updatedStats.getReacts().containsKey("l")) {
                                            likeCounts.put(postId, updatedStats.getReacts().get("l").intValue());
                                        } else {
                                            likeCounts.put(postId, 0);
                                        }
                                    }
                                }

                                callback.onSuccess(result);
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
     * Handle a deleted feed post event
     */
    private void handleDeletedFeedPost(LiveEvent eventData) {
        if (eventData.getFeedPost() == null) {
            return;
        }

        // Get the feed post ID from the event
        String postId = eventData.getFeedPost().getId();
        boolean wasRemoved = false;

        // Remove the post from our list if it exists
        for (int i = 0; i < feedPosts.size(); i++) {
            FeedPost post = feedPosts.get(i);
            if (post != null && post.getId() != null && post.getId().equals(postId)) {
                feedPosts.remove(i);
                // Also remove from our lookup maps
                postsById.remove(postId);
                likeCounts.remove(postId);
                wasRemoved = true;
                // Break after removing to avoid index issues
                break;
            }
        }
        
        if (wasRemoved) {
            // Log deletion for debugging
            Log.d("FastCommentsFeedSDK", "Post with ID " + postId + " was deleted via live event");
            
            // Notify any callback listeners about the post deletion
            // This allows the UI to update when a post is deleted by someone else
            if (onPostDeletedListener != null) {
                onPostDeletedListener.onPostDeleted(postId);
            }
        }
    }
    
    /**
     * Interface for notifying when a post is deleted via live event
     */
    public interface OnPostDeletedListener {
        void onPostDeleted(String postId);
    }
    
    private OnPostDeletedListener onPostDeletedListener;
    
    /**
     * Set a listener to be notified when posts are deleted via live events
     * 
     * @param listener The listener to notify
     */
    public void setOnPostDeletedListener(OnPostDeletedListener listener) {
        this.onPostDeletedListener = listener;
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

    /**
     * Delete a feed post
     *
     * @param postId   The ID of the post to delete
     * @param callback Callback to receive the response
     */
    public void deleteFeedPost(String postId, FCCallback<APIEmptyResponse> callback) {
        if (postId == null || postId.isEmpty()) {
            APIError error = new APIError();
            error.setReason("Post ID is required");
            callback.onFailure(error);
            return;
        }

        try {
            api.deleteFeedPostPublic(config.tenantId, postId)
                    .sso(config.getSSOToken())
                    .executeAsync(new ApiCallback<DeleteFeedPostPublic200Response>() {
                        @Override
                        public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                            APIError error = CallbackWrapper.createErrorFromException(e);
                            callback.onFailure(error);
                        }

                        @Override
                        public void onSuccess(DeleteFeedPostPublic200Response result, int statusCode, Map<String, List<String>> responseHeaders) {
                            if (result.getActualInstance() instanceof APIError) {
                                APIError error = (APIError) result.getActualInstance();
                                callback.onFailure(error);
                            } else {
                                mainHandler.post(() -> {
                                    // Remove the post from our local list
                                    for (int i = 0; i < feedPosts.size(); i++) {
                                        FeedPost post = feedPosts.get(i);
                                        if (post != null && post.getId() != null && post.getId().equals(postId)) {
                                            feedPosts.remove(i);
                                            postsById.remove(postId);
                                            likeCounts.remove(postId);
                                            break;
                                        }
                                    }

                                    callback.onSuccess(new APIEmptyResponse());
                                });
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