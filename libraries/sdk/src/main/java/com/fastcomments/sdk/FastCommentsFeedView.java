package com.fastcomments.sdk;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.fastcomments.model.APIEmptyResponse;
import com.fastcomments.model.APIError;
import com.fastcomments.model.FeedPost;
import com.fastcomments.model.FeedPostMediaItem;
import com.fastcomments.model.GetFeedPostsResponse;
import com.fastcomments.model.GetFeedPostsStats200Response;
import com.fastcomments.model.PublicFeedPostsResponse;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FastCommentsFeedView displays a feed of posts from FastComments with infinite scrolling
 * Includes support for scroll position retention and state restoration
 */
public class FastCommentsFeedView extends FrameLayout {
    
    /**
     * Class to store view state information
     */
    public static class ViewState implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private int scrollPosition;
        private FastCommentsFeedSDK.FeedState feedState;
        
        public ViewState() {
            // Default constructor
        }
        
        public int getScrollPosition() { return scrollPosition; }
        public void setScrollPosition(int position) { this.scrollPosition = position; }
        
        public FastCommentsFeedSDK.FeedState getFeedState() { return feedState; }
        public void setFeedState(FastCommentsFeedSDK.FeedState state) { this.feedState = state; }
    }

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private ProgressBar feedProgressBar;
    private ProgressBar loadMoreProgressBar;
    private TextView emptyStateView;
    private TextView errorStateView;
    
    private FeedPostsAdapter adapter;
    private FastCommentsFeedSDK sdk;
    private Handler handler;
    private List<FeedPost> feedPosts = new ArrayList<>();
    private OnFeedViewInteractionListener listener;
    
    // Polling for post stats
    private static final long POLLING_INTERVAL_MS = 30 * 1000; // 30 seconds
    private boolean isPollingEnabled = true;
    private Runnable pollStatsRunnable;

    /**
     * Interface for feed view interaction callbacks
     */
    public interface OnFeedViewInteractionListener {
        void onFeedLoaded(List<FeedPost> posts);
        void onFeedError(String errorMessage);
        void onPostSelected(FeedPost post);
        /**
         * Called when the user wants to view or add comments for a post
         * 
         * @param post The post to show comments for
         */
        void onCommentsRequested(FeedPost post);
    }

    // Standard View constructors for inflation from XML
    public FastCommentsFeedView(@NonNull Context context) {
        super(context);
        init(context, null, null);
    }

    public FastCommentsFeedView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, null);
    }

    public FastCommentsFeedView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, null);
    }

    // Custom constructors with SDK
    public FastCommentsFeedView(@NonNull Context context, FastCommentsFeedSDK sdk) {
        super(context);
        init(context, null, sdk);
    }

    public FastCommentsFeedView(@NonNull Context context, @Nullable AttributeSet attrs, FastCommentsFeedSDK sdk) {
        super(context, attrs);
        init(context, attrs, sdk);
    }

    public FastCommentsFeedView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, FastCommentsFeedSDK sdk) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, sdk);
    }

    private void init(Context context, AttributeSet attrs, FastCommentsFeedSDK sdk) {
        LayoutInflater.from(context).inflate(R.layout.fast_comments_feed_view, this, true);

        handler = new Handler(Looper.getMainLooper());
        this.sdk = sdk;

        // Initialize views
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        recyclerView = findViewById(R.id.recyclerViewFeed);
        feedProgressBar = findViewById(R.id.feedProgressBar);
        loadMoreProgressBar = findViewById(R.id.loadMoreProgressBar);
        emptyStateView = findViewById(R.id.emptyStateView);
        errorStateView = findViewById(R.id.errorStateView);

        // Set up RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        
        // Disable item animations to prevent flicker when clicking items
        recyclerView.setItemAnimator(null);
        
        // Skip initializing the adapter if SDK is not provided yet
        // It will be initialized when setSDK is called
        if (sdk != null) {
            initAdapter(context);
        }
    }
    
    /**
     * Initialize the adapter with the SDK
     */
    private void initAdapter(Context context) {
        // Configure RecyclerView for smoother scrolling with image preloading
        recyclerView.setItemViewCacheSize(20); // Cache more items
        recyclerView.setDrawingCacheEnabled(true);
        recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        
        // Set larger prefetch to load images ahead of time
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager != null) {
            layoutManager.setInitialPrefetchItemCount(5); // Prefetch 5 items
        }
        
        // Initialize adapter
        adapter = new FeedPostsAdapter(context, feedPosts, sdk, new FeedPostsAdapter.OnFeedPostInteractionListener() {
            @Override
            public void onCommentClick(FeedPost post) {
                // Notify callback for handling comments
                if (listener != null) {
                    listener.onCommentsRequested(post);
                }
            }

            @Override
            public void onLikeClick(FeedPost post, int position) {
                // Like the post (optimistic update)
                toggleLike(post, position);
            }

            @Override
            public void onShareClick(FeedPost post) {
                // Share the post
                sharePost(post);
            }

            @Override
            public void onPostClick(FeedPost post) {
                // Select the post
                if (listener != null) {
                    listener.onPostSelected(post);
                }
            }

            @Override
            public void onLinkClick(String url) {
                // Open link in browser
                openUrl(url);
            }

            @Override
            public void onMediaClick(FeedPostMediaItem mediaItem) {
                // Handle media click
                if (mediaItem.getLinkUrl() != null) {
                    openUrl(mediaItem.getLinkUrl());
                }
            }
            
            @Override
            public void onDeletePost(FeedPost post) {
                // Confirm before deleting
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
                builder.setTitle(R.string.delete_post_title)
                        .setMessage(R.string.delete_post_confirm)
                        .setPositiveButton(R.string.delete, (dialog, which) -> {
                            // Call API to delete the post
                            deletePost(post);
                        })
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                            dialog.dismiss();
                        })
                        .show();
            }
        });
        
        recyclerView.setAdapter(adapter);

        // Set up pull-to-refresh
        swipeRefreshLayout.setOnRefreshListener(this::refresh);
        
        // Set up infinite scrolling
        setupInfiniteScrolling();
    }
    
    /**
     * Set up infinite scrolling for the RecyclerView
     */
    private void setupInfiniteScrolling() {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (sdk == null || !sdk.hasMore || isLoadingMore) {
                    return;
                }

                if (dy > 0) { // Scrolling down
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        int visibleItemCount = layoutManager.getChildCount();
                        int totalItemCount = layoutManager.getItemCount();
                        int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                        // Load more when user is near the end (last 5 items)
                        if ((visibleItemCount + firstVisibleItemPosition + 5) >= totalItemCount) {
                            loadMore();
                        }
                    }
                }
            }
        });
    }
    
    /**
     * Set the SDK instance to use with this view (for use when inflating from XML)
     * 
     * @param sdk The FastCommentsFeedSDK instance
     */
    public void setSDK(FastCommentsFeedSDK sdk) {
        // Clean up existing SDK if any
        if (this.sdk != null) {
            this.sdk.setOnPostDeletedListener(null);
        }
        
        this.sdk = sdk;
        if (adapter == null) {
            initAdapter(getContext());
        }
        
        // Register for post deletion events from live WebSocket
        if (sdk != null) {
            sdk.setOnPostDeletedListener(postId -> {
                if (handler != null && adapter != null) {
                    handler.post(() -> {
                        // Update adapter with the current list from SDK
                        // This ensures the adapter's list is in sync with the SDK after a post is deleted
                        if (sdk != null && adapter != null) {
                            adapter.updatePosts(sdk.getFeedPosts());
                        }
                        
                        // Log for debugging
                        Log.d("FastCommentsFeedView", "Received post deletion event for post ID: " + postId);
                    });
                }
            });
        }
    }
    
    /**
     * Save the complete view state including scroll position and feed data
     * @return A ViewState object containing all state information
     */
    public ViewState saveViewState() {
        ViewState state = new ViewState();
        
        // Save scroll position
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager != null) {
            state.setScrollPosition(layoutManager.findFirstVisibleItemPosition());
        }
        
        // Save SDK state
        if (sdk != null) {
            state.setFeedState(sdk.savePaginationState());
        }
        
        return state;
    }
    
    /**
     * Restore the complete view state
     * @param state The ViewState object to restore from
     */
    public void restoreViewState(ViewState state) {
        if (state == null) {
            return;
        }
        
        // Restore SDK state first
        if (sdk != null && state.getFeedState() != null) {
            sdk.restorePaginationState(state.getFeedState());
            
            // Update adapter with restored posts
            if (adapter != null && sdk.getFeedPosts() != null && !sdk.getFeedPosts().isEmpty()) {
                adapter.updatePosts(sdk.getFeedPosts());
            }
        }
        
        // Restore scroll position
        final int position = state.getScrollPosition();
        if (position >= 0 && recyclerView != null) {
            // Use post to make sure this runs after layout
            recyclerView.post(() -> recyclerView.scrollToPosition(position));
        }
    }
    
    /**
     * Save just the scroll position (use saveViewState for complete state)
     * @return The first visible item position
     */
    public int saveScrollPosition() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager != null) {
            return layoutManager.findFirstVisibleItemPosition();
        }
        return 0;
    }
    
    /**
     * Restore just the scroll position (use restoreViewState for complete state)
     * @param position The position to scroll to
     */
    public void restoreScrollPosition(int position) {
        if (position >= 0 && recyclerView != null) {
            recyclerView.scrollToPosition(position);
        }
    }

    /**
     * Set a listener for feed interactions
     * 
     * @param listener The listener to set
     */
    public void setFeedViewInteractionListener(OnFeedViewInteractionListener listener) {
        this.listener = listener;
    }

    /**
     * Flag to track when loading more posts
     */
    private boolean isLoadingMore = false;

    /**
     * Load the feed
     */
    public void load() {
        showLoading(true);
        hideError();

        sdk.load(new FCCallback<PublicFeedPostsResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                handler.post(() -> {
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);

                    String errorMessage = "Error loading feed";
                    if (sdk.blockingErrorMessage != null && !sdk.blockingErrorMessage.isEmpty()) {
                        errorMessage = sdk.blockingErrorMessage;
                    } else if (error != null && error.getTranslatedError() != null) {
                        errorMessage = error.getTranslatedError();
                    } else if (error != null && error.getReason() != null) {
                        errorMessage = error.getReason();
                    }
                    
                    // Log the error for debugging
                    Log.e("FastCommentsFeedView", "Feed loading error: " + errorMessage);
                    if (error != null && error.getReason() != null && error.getReason().contains("JsonSyntax")) {
                        Log.e("FastCommentsFeedView", "JsonSyntaxException detected in API response", 
                            new Exception("JSON parsing error occurred in API response"));
                    }

                    showError(errorMessage);

                    if (listener != null) {
                        listener.onFeedError(errorMessage);
                    }
                });
                return CONSUME;
            }

            @Override
            public boolean onSuccess(PublicFeedPostsResponse response) {
                handler.post(() -> {
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);
                    hideError();

                    List<FeedPost> posts = sdk.getFeedPosts();
                    
                    if (posts.isEmpty()) {
                        showEmptyState(true);
                    } else {
                        showEmptyState(false);
                        int firstVisiblePosition = -1;
                        if (recyclerView != null && recyclerView.getLayoutManager() != null) {
                            firstVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
                        }
                        
                        adapter.updatePosts(posts);
                        
                        // Restore position if we had one
                        if (firstVisiblePosition >= 0 && firstVisiblePosition < posts.size()) {
                            recyclerView.scrollToPosition(firstVisiblePosition);
                        }
                    }

                    if (listener != null) {
                        listener.onFeedLoaded(posts);
                    }
                    
                    // Start polling for stats updates
                    startPolling();
                });
                return CONSUME;
            }
        });
    }

    /**
     * Refresh the feed (clear and reload)
     */
    public void refresh() {
        hideError();
        
        sdk.refresh(new FCCallback<PublicFeedPostsResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                handler.post(() -> {
                    swipeRefreshLayout.setRefreshing(false);

                    String errorMessage = "Error refreshing feed";
                    if (sdk.blockingErrorMessage != null && !sdk.blockingErrorMessage.isEmpty()) {
                        errorMessage = sdk.blockingErrorMessage;
                    } else if (error != null && error.getTranslatedError() != null) {
                        errorMessage = error.getTranslatedError();
                    } else if (error != null && error.getReason() != null) {
                        errorMessage = error.getReason();
                    }
                    
                    // Log the error for debugging
                    Log.e("FastCommentsFeedView", "Feed refresh error: " + errorMessage);
                    if (error != null && error.getReason() != null && error.getReason().contains("JsonSyntax")) {
                        Log.e("FastCommentsFeedView", "JsonSyntaxException detected in API response during refresh", 
                            new Exception("JSON parsing error occurred in API response"));
                    }

                    showError(errorMessage);

                    if (listener != null) {
                        listener.onFeedError(errorMessage);
                    }
                });
                return CONSUME;
            }

            @Override
            public boolean onSuccess(PublicFeedPostsResponse response) {
                handler.post(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    hideError();

                    List<FeedPost> posts = sdk.getFeedPosts();
                    
                    if (posts.isEmpty()) {
                        showEmptyState(true);
                    } else {
                        showEmptyState(false);
                        int firstVisiblePosition = -1;
                        if (recyclerView != null && recyclerView.getLayoutManager() != null) {
                            firstVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
                        }
                        
                        adapter.updatePosts(posts);
                        
                        // Restore position if we had one
                        if (firstVisiblePosition >= 0 && firstVisiblePosition < posts.size()) {
                            recyclerView.scrollToPosition(firstVisiblePosition);
                        }
                    }

                    if (listener != null) {
                        listener.onFeedLoaded(posts);
                    }
                    
                    // Start polling for stats updates
                    startPolling();
                });
                return CONSUME;
            }
        });
    }

    /**
     * Load more posts (pagination)
     */
    public void loadMore() {
        if (isLoadingMore) {
            return;
        }

        isLoadingMore = true;
        loadMoreProgressBar.setVisibility(View.VISIBLE);

        sdk.loadMore(new FCCallback<PublicFeedPostsResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                handler.post(() -> {
                    isLoadingMore = false;
                    loadMoreProgressBar.setVisibility(View.GONE);

                    String errorMessage = "Error loading more posts";
                    if (error != null && error.getTranslatedError() != null) {
                        errorMessage = error.getTranslatedError();
                    } else if (error != null && error.getReason() != null) {
                        errorMessage = error.getReason();
                    }
                    
                    // Log the error for debugging
                    Log.e("FastCommentsFeedView", "Error loading more posts: " + errorMessage);
                    if (error != null && error.getReason() != null && error.getReason().contains("JsonSyntax")) {
                        Log.e("FastCommentsFeedView", "JsonSyntaxException detected when loading more posts", 
                            new Exception("JSON parsing error occurred in API response"));
                    }

                    // Notify listener of error (if set)
                    if (listener != null) {
                        listener.onFeedError(errorMessage);
                    }
                });
                return CONSUME;
            }

            @Override
            public boolean onSuccess(PublicFeedPostsResponse response) {
                handler.post(() -> {
                    isLoadingMore = false;
                    loadMoreProgressBar.setVisibility(View.GONE);

                    adapter.addPosts(response.getFeedPosts());
                });
                return CONSUME;
            }
        });
    }

    /**
     * Toggle like status for a post
     * 
     * @param post The post to like/unlike
     * @param position The position of the post in the adapter
     */
    private void toggleLike(FeedPost post, int position) {
        if (sdk == null || post == null || post.getId() == null) {
            return;
        }
        
        // Call the SDK to toggle the like status
        sdk.likePost(post.getId(), new FCCallback<FeedPost>() {
            @Override
            public boolean onFailure(APIError error) {
                handler.post(() -> {
                    String errorMessage;
                    if (error.getTranslatedError() != null && !error.getTranslatedError().isEmpty()) {
                        errorMessage = error.getTranslatedError();
                    } else if (error.getReason() != null && !error.getReason().isEmpty()) {
                        errorMessage = error.getReason();
                    } else {
                        errorMessage = getContext().getString(R.string.error_liking_post);
                    }

                    android.widget.Toast.makeText(
                            getContext(),
                            errorMessage,
                            android.widget.Toast.LENGTH_SHORT
                    ).show();
                });
                return CONSUME;
            }

            @Override
            public boolean onSuccess(FeedPost updatedPost) {
                // Update the post in the adapter
                handler.post(() -> {
                    adapter.updatePost(position, updatedPost);
                });
                return CONSUME;
            }
        });
    }

    /**
     * Share a post
     * 
     * @param post The post to share
     */
    private void sharePost(FeedPost post) {
        // Create a share intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        
        // Create a share message with the content and link to the post
        StringBuilder shareMessage = new StringBuilder();
        
        // Add post content if available
        if (post.getContentHTML() != null) {
            // Strip HTML tags for sharing
            String plainText = android.text.Html.fromHtml(post.getContentHTML(), 
                    android.text.Html.FROM_HTML_MODE_COMPACT).toString();
            shareMessage.append(plainText);
        }
        
        // Get the first link if available
        if (post.getLinks() != null && !post.getLinks().isEmpty() 
                && post.getLinks().get(0).getUrl() != null) {
            if (shareMessage.length() > 0) {
                shareMessage.append("\n\n");
            }
            shareMessage.append(post.getLinks().get(0).getUrl());
        }
        
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage.toString());
        
        // Start the share activity
        getContext().startActivity(Intent.createChooser(shareIntent, 
                getContext().getString(R.string.share)));
    }

    /**
     * Open a URL in the browser
     * 
     * @param url The URL to open
     */
    private void openUrl(String url) {
        if (url != null && !url.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            getContext().startActivity(intent);
        }
    }

    /**
     * Show or hide the loading indicator
     * 
     * @param isLoading Whether loading is in progress
     */
    private void showLoading(boolean isLoading) {
        feedProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        
        if (isLoading) {
            recyclerView.setVisibility(View.GONE);
            emptyStateView.setVisibility(View.GONE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Show or hide the empty state view
     * 
     * @param isEmpty Whether the feed is empty
     */
    private void showEmptyState(boolean isEmpty) {
        emptyStateView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    /**
     * Show an error message
     * 
     * @param errorMessage The error message to display
     */
    private void showError(String errorMessage) {
        errorStateView.setText(errorMessage);
        errorStateView.setVisibility(View.VISIBLE);
        
        // Log the error to help with debugging
        Log.e("FastCommentsFeedView", "Displaying error: " + errorMessage);
    }

    /**
     * Hide any displayed error message
     */
    private void hideError() {
        errorStateView.setVisibility(View.GONE);
    }

    /**
     * Creates a FastCommentsView for displaying comments for a specific post
     *
     * @param post The post to show comments for
     * @return A FastCommentsView instance configured for the post
     */
    public FastCommentsView createCommentsViewForPost(FeedPost post) {
        if (sdk == null) {
            throw new IllegalStateException("SDK must be set before creating a comments view");
        }

        // Create a FastCommentsSDK instance for this post's comments
        FastCommentsSDK commentsSDK = sdk.createCommentsSDKForPost(post);
        
        // Create and return a FastCommentsView with the new SDK
        return new FastCommentsView(getContext(), commentsSDK);
    }

    /**
     * Refreshes a specific post in the feed after comment is added
     * 
     * @param postId The ID of the post to refresh
     */
    public void refreshPost(String postId) {
        if (sdk == null || postId == null || adapter == null) {
            return;
        }
        
        // Get the updated posts list from SDK
        List<FeedPost> posts = sdk.getFeedPosts();
        if (posts == null || posts.isEmpty()) {
            return;
        }
        
        // Find the post in the posts list
        int position = -1;
        FeedPost updatedPost = null;
        
        for (int i = 0; i < posts.size(); i++) {
            FeedPost post = posts.get(i);
            if (post != null && postId.equals(post.getId())) {
                position = i;
                updatedPost = post;
                break;
            }
        }
        
        // If post is found, update it in the adapter with the refreshed data
        if (position >= 0 && updatedPost != null) {
            adapter.updatePost(position, updatedPost);
        }
    }
    
    /**
     * Initialize polling for post stats
     */
    private void initPolling() {
        pollStatsRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPollingEnabled && sdk != null && !feedPosts.isEmpty()) {
                    refreshVisiblePostStats();
                }
                
                // Schedule next run
                if (isPollingEnabled) {
                    handler.postDelayed(this, POLLING_INTERVAL_MS);
                }
            }
        };
    }
    
    /**
     * Start polling for post stats
     */
    public void startPolling() {
        if (pollStatsRunnable == null) {
            initPolling();
        }
        
        isPollingEnabled = true;
        // Remove any existing callbacks to prevent duplicates
        handler.removeCallbacks(pollStatsRunnable);
        // Start polling
        handler.postDelayed(pollStatsRunnable, POLLING_INTERVAL_MS);
    }
    
    /**
     * Stop polling for post stats
     */
    public void stopPolling() {
        isPollingEnabled = false;
        if (handler != null && pollStatsRunnable != null) {
            handler.removeCallbacks(pollStatsRunnable);
        }
    }
    
    /**
     * Refresh stats for currently visible posts
     */
    private void refreshVisiblePostStats() {
        if (sdk == null || feedPosts.isEmpty() || recyclerView == null) {
            return;
        }
        
        // Get visible post IDs
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null) {
            return;
        }
        
        // Find visible items range
        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int lastVisible = layoutManager.findLastVisibleItemPosition();
        
        // Safety check
        if (firstVisible < 0 || lastVisible < 0 || firstVisible > lastVisible) {
            return;
        }
        
        // Get post IDs for visible posts
        List<String> visiblePostIds = new ArrayList<>();
        
        // Limit the range to valid indices
        firstVisible = Math.max(0, firstVisible);
        lastVisible = Math.min(feedPosts.size() - 1, lastVisible);
        
        // Get post IDs for visible posts
        for (int i = firstVisible; i <= lastVisible; i++) {
            if (i < feedPosts.size()) {
                FeedPost post = feedPosts.get(i);
                if (post != null && post.getId() != null) {
                    visiblePostIds.add(post.getId());
                }
            }
        }
        
        // If no visible posts with IDs, return
        if (visiblePostIds.isEmpty()) {
            return;
        }
        
        // Fetch stats for visible posts
        final List<String> finalVisiblePostIds = visiblePostIds;
        sdk.getFeedPostsStats(visiblePostIds, new FCCallback<GetFeedPostsStats200Response>() {
            @Override
            public boolean onFailure(APIError error) {
                // Silent failure - we'll try again next time
                return CONSUME;
            }
            
            @Override
            public boolean onSuccess(GetFeedPostsStats200Response response) {
                // The SDK has already updated the post objects with new stats
                // Now we need to update the UI for each visible post
                handler.post(() -> {
                    // For each post ID, find its current position in the adapter
                    for (String postId : finalVisiblePostIds) {
                        for (int i = 0; i < feedPosts.size(); i++) {
                            FeedPost post = feedPosts.get(i);
                            if (post != null && postId.equals(post.getId())) {
                                // Update this item in the adapter
                                adapter.notifyItemChanged(i);
                                break;
                            }
                        }
                    }
                });
                return CONSUME;
            }
        });
    }
    
    /**
     * Delete a feed post
     * 
     * @param post The post to delete
     */
    private void deletePost(FeedPost post) {
        if (sdk == null || post == null || post.getId() == null) {
            return;
        }
        
        sdk.deleteFeedPost(post.getId(), new FCCallback<APIEmptyResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                handler.post(() -> {
                    String errorMessage;
                    if (error.getTranslatedError() != null && !error.getTranslatedError().isEmpty()) {
                        errorMessage = error.getTranslatedError();
                    } else if (error.getReason() != null && !error.getReason().isEmpty()) {
                        errorMessage = error.getReason();
                    } else {
                        errorMessage = getContext().getString(R.string.error_deleting_post);
                    }
                    
                    android.widget.Toast.makeText(
                            getContext(),
                            errorMessage,
                            android.widget.Toast.LENGTH_SHORT
                    ).show();
                });
                return CONSUME;
            }
            
            @Override
            public boolean onSuccess(APIEmptyResponse response) {
                handler.post(() -> {
                    // Success message
                    android.widget.Toast.makeText(
                            getContext(),
                            R.string.post_deleted_successfully,
                            android.widget.Toast.LENGTH_SHORT
                    ).show();
                    
                    // Update adapter with the current list from SDK
                    // This ensures the adapter's internal list matches the SDK's list
                    adapter.updatePosts(sdk.getFeedPosts());
                });
                return CONSUME;
            }
        });
    }
    
    /**
     * Clean up resources when the view is detached
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cleanup();
    }
    
    /**
     * Clean up all resources to prevent memory leaks.
     * Call this method when the view will no longer be used.
     */
    public void cleanup() {
        stopPolling();
        
        // Clear adapter data
        if (adapter != null) {
            adapter.updatePosts(new ArrayList<>());
        }
        
        // Clear SDK listener reference
        if (sdk != null) {
            sdk.setOnPostDeletedListener(null);
            sdk.cleanup();
            sdk = null;
        }
        
        // Clear local references
        listener = null;
        
        // Clear collections
        if (feedPosts != null) {
            feedPosts.clear();
        }
        
        // Clear handler callbacks
        if (handler != null && pollStatsRunnable != null) {
            handler.removeCallbacks(pollStatsRunnable);
        }
        
        // Clear polling runnable
        pollStatsRunnable = null;
    }
    
    /**
     * Returns the RecyclerView adapter used by this view.
     *
     * @return The FeedPostsAdapter instance
     */
    public FeedPostsAdapter getAdapter() {
        return adapter;
    }
    
    /**
     * Clears the feed posts from the adapter.
     * Use this method when switching fragments to avoid memory leaks.
     */
    public void clearAdapter() {
        if (adapter != null) {
            adapter.updatePosts(new ArrayList<>());
        }
    }
}