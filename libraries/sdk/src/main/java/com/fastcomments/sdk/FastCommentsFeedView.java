package com.fastcomments.sdk;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
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

import com.fastcomments.model.APIError;
import com.fastcomments.model.FeedPost;
import com.fastcomments.model.FeedPostMediaItem;
import com.fastcomments.model.GetFeedPostsResponse;
import com.fastcomments.model.PublicFeedPostsResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * FastCommentsFeedView displays a feed of posts from FastComments with infinite scrolling
 */
public class FastCommentsFeedView extends FrameLayout {

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
                        
                        // Preload next set of images
                        int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
                        if (adapter != null && lastVisiblePosition + 5 < totalItemCount) {
                            adapter.preloadImages(lastVisiblePosition + 1, 5);
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
        this.sdk = sdk;
        if (adapter == null) {
            initAdapter(getContext());
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
                        adapter.updatePosts(posts);
                    }

                    if (listener != null) {
                        listener.onFeedLoaded(posts);
                    }
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
                    if (error != null && error.getReason() != null) {
                        errorMessage = error.getReason();
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
                        adapter.updatePosts(posts);
                    }

                    if (listener != null) {
                        listener.onFeedLoaded(posts);
                    }
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
                    if (error != null && error.getReason() != null) {
                        errorMessage = error.getReason();
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
                // Handle failure - could show error toast here
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
     * Clean up resources when the view is detached
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (sdk != null) {
            sdk.cleanup();
        }
    }
}