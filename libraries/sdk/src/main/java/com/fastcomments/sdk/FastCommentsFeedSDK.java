package com.fastcomments.sdk;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.fastcomments.api.DefaultApi;
import com.fastcomments.api.PublicApi;
import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.invoker.ApiCallback;
import com.fastcomments.invoker.ApiException;
import com.fastcomments.model.APIError;
import com.fastcomments.model.FeedPost;
import com.fastcomments.model.GetFeedPosts200Response;
import com.fastcomments.model.GetFeedPostsResponse;
import com.fastcomments.model.UserSessionInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    /**
     * Constructs a FastCommentsFeedSDK instance with the given configuration
     *
     * @param config CommentWidgetConfig object containing credentials and other settings
     */
    public FastCommentsFeedSDK(CommentWidgetConfig config) {
        this.api = new PublicApi();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.config = config;
        this.api.getApiClient().setBasePath("https://fastcomments.com");
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
    public void load(FCCallback<GetFeedPostsResponse> callback) {
        // Reset pagination for initial load
        lastPostId = null;  // Reset the cursor for pagination

        // Reset any existing error message
        blockingErrorMessage = null;

        loadFeedPosts(callback);
    }

    /**
     * Loads feed posts with the current pagination state
     *
     * @param callback Callback to receive the response
     */
    private void loadFeedPosts(FCCallback<GetFeedPostsResponse> callback) {
        try {
            api.getFeedPostsPublic(config.tenantId)
                    .afterId(lastPostId)
                    .limit(pageSize)
                    .executeAsync(new ApiCallback<GetFeedPosts200Response>() {
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
                        public void onSuccess(GetFeedPosts200Response response, int statusCode, Map<String, List<String>> responseHeaders) {
                            if (response.getActualInstance() instanceof APIError) {
                                APIError error = (APIError) response.getActualInstance();
                                if (error.getTranslatedError() != null && !error.getTranslatedError().isEmpty()) {
                                    blockingErrorMessage = error.getTranslatedError();
                                } else if (error.getReason() != null && !error.getReason().isEmpty()) {
                                    blockingErrorMessage = "Feed could not load! Details: " + error.getReason();
                                }
                                callback.onFailure(error);
                            } else {
                                GetFeedPostsResponse feedResponse = response.getGetFeedPostsResponse();
                                processResponse(feedResponse, callback);
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
     * Process the feed posts response and update the internal state
     *
     * @param response The response from the API
     * @param callback Callback to receive the processed response
     */
    private void processResponse(GetFeedPostsResponse response, FCCallback<GetFeedPostsResponse> callback) {
        mainHandler.post(() -> {
            List<FeedPost> posts = response.getFeedPosts();

            // Only clear the list if this is an initial load (no lastPostId)
            // This ensures we don't clear when paginating or loading more
            if (lastPostId == null) {
                feedPosts.clear();
            }

            if (posts != null && !posts.isEmpty()) {
                feedPosts.addAll(posts);

                // Update lastPostId for pagination if we have posts
                FeedPost lastPost = posts.get(posts.size() - 1);
                lastPostId = lastPost.getId();
            }

            // Check if there are more posts to load
            // If we got posts back and size equals page size, assume more posts are available
            hasMore = posts != null && !posts.isEmpty() && posts.size() >= pageSize;

            callback.onSuccess(response);
        });
    }

    /**
     * Load more feed posts (next page)
     * This uses cursor-based pagination with the lastPostId
     *
     * @param callback Callback to receive the response
     */
    public void loadMore(FCCallback<GetFeedPostsResponse> callback) {
        if (!hasMore) {
            APIError error = new APIError();
            error.setReason("No more posts to load");
            callback.onFailure(error);
            return;
        }

        // Using cursor-based pagination with lastPostId
        // No need to track the page number
        loadFeedPosts(new FCCallback<GetFeedPostsResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                callback.onFailure(error);
                return CONSUME;
            }

            @Override
            public boolean onSuccess(GetFeedPostsResponse response) {
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
    public void refresh(FCCallback<GetFeedPostsResponse> callback) {
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
        // Nothing to clean up yet
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
}