package com.fastcomments.sdk;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.fastcomments.model.FeedPost;
import com.fastcomments.model.FeedPostLink;
import com.fastcomments.model.FeedPostMediaItem;
import com.fastcomments.model.FeedPostMediaItemAsset;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FeedPostsAdapter extends RecyclerView.Adapter<FeedPostsAdapter.FeedPostViewHolder> {

    private final Context context;
    private final List<FeedPost> feedPosts;
    private final OnFeedPostInteractionListener listener;
    private final FastCommentsFeedSDK sdk;
    private final boolean useAbsoluteDates;
    private OnScrollToTopRequestedListener onScrollToTopRequestedListener;

    public interface OnFeedPostInteractionListener {
        void onCommentClick(FeedPost post);

        void onLikeClick(FeedPost post, int position);

        void onShareClick(FeedPost post);

        void onPostClick(FeedPost post);

        void onLinkClick(String url);

        void onMediaClick(FeedPostMediaItem mediaItem);

        void onDeletePost(FeedPost post);
    }

    public interface OnScrollToTopRequestedListener {
        void onScrollToTopRequested();
    }

    public FeedPostsAdapter(Context context, List<FeedPost> feedPosts, FastCommentsFeedSDK sdk, OnFeedPostInteractionListener listener) {
        this.context = context;
        this.feedPosts = feedPosts;
        this.listener = listener;
        this.sdk = sdk;
        // Set date format based on SDK configuration
        this.useAbsoluteDates = Boolean.TRUE.equals(sdk.getConfig().absoluteDates);
    }

    /**
     * Set the listener for scroll to top requests
     *
     * @param listener The listener to be notified when scroll to top is requested
     */
    public void setOnScrollToTopRequestedListener(OnScrollToTopRequestedListener listener) {
        this.onScrollToTopRequestedListener = listener;
    }

    /**
     * Get the standard image height from resources
     *
     * @return Standard image height in pixels
     */
    private int getDefaultImageHeight() {
        return context.getResources().getDimensionPixelSize(R.dimen.feed_image_height);
    }

    /**
     * Get the half-size image height from resources
     *
     * @return Half image height in pixels
     */
    private int getHalfImageHeight() {
        return context.getResources().getDimensionPixelSize(R.dimen.feed_image_half_height);
    }

    @Override
    public int getItemViewType(int position) {
        FeedPost post = feedPosts.get(position);
        return determinePostType(post).ordinal();
    }

    /**
     * Determine the type of post based on its content
     *
     * @param post The post to analyze
     * @return The FeedPostType for this post
     */
    private FeedPostType determinePostType(FeedPost post) {
        // Check if this is a post with action links
        if (post.getLinks() != null && !post.getLinks().isEmpty()) {
            return FeedPostType.TASK;
        }

        // Check for multiple images
        if (post.getMedia() != null && post.getMedia().size() > 1) {
            return FeedPostType.MULTI_IMAGE;
        }

        // Check for a single image
        if (post.getMedia() != null && post.getMedia().size() == 1) {
            return FeedPostType.SINGLE_IMAGE;
        }

        // Default to text only
        return FeedPostType.TEXT_ONLY;
    }

    @NonNull
    @Override
    public FeedPostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FeedPostType postType = FeedPostType.values()[viewType];
        int layoutResId;

        // Select the appropriate layout based on post type
        switch (postType) {
            case SINGLE_IMAGE:
                layoutResId = R.layout.feed_post_item_single_image;
                break;
            case MULTI_IMAGE:
                layoutResId = R.layout.feed_post_item_multi_image;
                break;
            case TASK:
                layoutResId = R.layout.feed_post_item_task;
                break;
            case TEXT_ONLY:
            default:
                layoutResId = R.layout.feed_post_item_text_only;
                break;
        }

        View view = LayoutInflater.from(context).inflate(layoutResId, parent, false);
        return new FeedPostViewHolder(view, postType);
    }

    @Override
    public void onBindViewHolder(@NonNull FeedPostViewHolder holder, int position) {
        FeedPost post = feedPosts.get(position);
        holder.bind(post, position);
    }

    @Override
    public void onBindViewHolder(@NonNull FeedPostViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            // No payloads, do full bind
            onBindViewHolder(holder, position);
        } else {
            // Handle partial updates based on payloads
            FeedPost post = feedPosts.get(position);
            for (Object payload : payloads) {
                if (payload instanceof UpdateType) {
                    UpdateType updateType = (UpdateType) payload;
                    if (updateType == UpdateType.STATS_UPDATE) {
                        holder.updateStatsAndLikes(post);
                    }
                }
            }
        }
    }

    @Override
    public void onViewRecycled(@NonNull FeedPostViewHolder holder) {
        super.onViewRecycled(holder);
        // Clean up ViewPager callbacks when view is recycled
        if (holder.postType == FeedPostType.MULTI_IMAGE && holder.imageViewPager != null) {
            holder.imageViewPager.unregisterOnPageChangeCallback(holder.pageChangeCallback);
        }
    }

    @Override
    public int getItemCount() {
        return feedPosts.size();
    }

    /**
     * Updates the adapter with a new list of posts
     * This is called when posts are added, removed, or changed
     *
     * @param newPosts The new list of posts to display
     */
    public void updatePosts(List<FeedPost> newPosts) {
        updatePosts(newPosts, false);
    }

    public void updatePosts(List<FeedPost> newPosts, boolean scrollToTop) {
        if (newPosts == null) {
            return;
        }

        // Create a new ArrayList to avoid reference issues
        List<FeedPost> updatedPosts = new ArrayList<>(newPosts);

        // Clear and update the adapter's internal list
        this.feedPosts.clear();
        this.feedPosts.addAll(updatedPosts);

        // Notify adapter that all data has changed
        notifyDataSetChanged();

        // Scroll to top if requested (e.g., after adding a new post)
        // Use post() to ensure this happens after the RecyclerView has updated
        if (scrollToTop && onScrollToTopRequestedListener != null) {
            // Post the scroll action to happen after the adapter update is complete
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                onScrollToTopRequestedListener.onScrollToTopRequested();
            });
        }

        // Log the update for debugging
        Log.d("FeedPostsAdapter", "Updated posts list with " + updatedPosts.size() + " posts");
    }


    public void addPosts(List<FeedPost> morePosts) {
        int startPosition = this.feedPosts.size();
        this.feedPosts.addAll(morePosts);
        notifyItemRangeInserted(startPosition, morePosts.size());
    }

    public void updatePost(int position, FeedPost updatedPost) {
        if (position >= 0 && position < feedPosts.size()) {
            feedPosts.set(position, updatedPost);
            notifyItemChanged(position);
        }
    }

    /**
     * Update a post with specific payload to trigger partial updates
     */
    public void updatePost(int position, FeedPost updatedPost, UpdateType updateType) {
        if (position >= 0 && position < feedPosts.size()) {
            feedPosts.set(position, updatedPost);
            notifyItemChanged(position, updateType);
        }
    }

    /**
     * Select the best image size based on device display metrics
     * Prioritizes images that fit well on the screen while maintaining quality
     *
     * @param sizes List of available image sizes
     * @return The most appropriate FeedPostMediaItemAsset or the first one if no optimal size is found
     */
    private FeedPostMediaItemAsset selectBestImageSize(List<FeedPostMediaItemAsset> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }

        // If there's only one size, use it
        if (sizes.size() == 1) {
            return sizes.get(0);
        }

        // Get screen width for comparison
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;

        // Target a size that's close to the screen width for optimal display
        // We'll tolerate images up to 1.5x screen width to maintain quality
        double optimalWidth = screenWidth;
        double maxAcceptableWidth = screenWidth * 1.5;

        FeedPostMediaItemAsset bestMatch = null;
        double smallestDiff = Double.MAX_VALUE;

        // First pass: find close matches to optimal width
        for (FeedPostMediaItemAsset asset : sizes) {
            if (asset == null || asset.getW() == null || asset.getSrc() == null) {
                continue;
            }

            double width = asset.getW();
            double diff = Math.abs(width - optimalWidth);

            // If width is within acceptable range and has smaller difference than current best match
            if (width <= maxAcceptableWidth && diff < smallestDiff) {
                bestMatch = asset;
                smallestDiff = diff;
            }
        }

        // If no match found in optimal range, just use the largest that's not excessively large
        if (bestMatch == null) {
            double largestAcceptableWidth = 0;

            for (FeedPostMediaItemAsset asset : sizes) {
                if (asset == null || asset.getW() == null || asset.getSrc() == null) {
                    continue;
                }

                double width = asset.getW();

                // Find largest image that's not too oversized
                if (width > largestAcceptableWidth && width <= maxAcceptableWidth * 2) {
                    bestMatch = asset;
                    largestAcceptableWidth = width;
                }
            }

            // If still no match, use the first valid asset
            if (bestMatch == null) {
                for (FeedPostMediaItemAsset asset : sizes) {
                    if (asset != null && asset.getSrc() != null) {
                        return asset;
                    }
                }
            }
        }

        // Return best match or first asset if no match found
        return bestMatch != null ? bestMatch : sizes.get(0);
    }

    /**
     * Calculate the aspect ratio-based height for an image that will be displayed at the given width
     *
     * @param asset        The media asset containing width and height information
     * @param displayWidth The width at which the image will be displayed
     * @return The calculated height based on aspect ratio, or default height if dimensions are missing
     */
    private int calculateImageHeight(FeedPostMediaItemAsset asset, int displayWidth) {
        if (asset == null || asset.getW() == null || asset.getH() == null ||
                asset.getW() <= 0 || asset.getH() <= 0) {
            return getDefaultImageHeight(); // Fall back to default height
        }

        // Calculate aspect ratio (width / height)
        double aspectRatio = (double) asset.getW() / (double) asset.getH();

        // If aspect ratio is invalid, use default height
        if (aspectRatio <= 0) {
            return getDefaultImageHeight();
        }

        // Calculate height based on aspect ratio and display width
        int calculatedHeight = (int) (displayWidth / aspectRatio);

        // setting a minimum height makes short images appear weird
        return calculatedHeight;
    }

    enum FeedPostType {
        TEXT_ONLY,
        SINGLE_IMAGE,
        MULTI_IMAGE,
        TASK
    }

    enum UpdateType {
        STATS_UPDATE
    }


    class FeedPostViewHolder extends RecyclerView.ViewHolder {
        private final FeedPostType postType;

        // Common elements in all layouts
        private final TextView userNameTextView;
        private final TextView postTimeTextView;
        private final TextView contentTextView;
        private final ImageView avatarImageView;
        private final ChipGroup tagsChipGroup;
        private final TextView likeCountTextView;
        private final Button commentButton;
        private final Button likeButton;
        private final Button shareButton;
        private final ImageButton postMenuButton;

        // Single image layout elements
        private FrameLayout mediaContainer;
        private ImageView mediaImageView;
        private ImageView playButton;

        // Multi-image layout elements
        private FrameLayout mediaGalleryContainer;
        private GridLayout imageGridLayout;
        private androidx.viewpager2.widget.ViewPager2 imageViewPager;
        private TextView imageCounterTextView;
        private List<FeedPostMediaItem> mediaItems;
        private PostImagesAdapter imagesAdapter;
        private androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback pageChangeCallback;

        // Task layout elements
        private LinearLayout taskButtonsContainer;

        FeedPostViewHolder(@NonNull View itemView, FeedPostType postType) {
            super(itemView);
            this.postType = postType;

            // Common elements
            userNameTextView = itemView.findViewById(R.id.userNameTextView);
            postTimeTextView = itemView.findViewById(R.id.postTimeTextView);
            contentTextView = itemView.findViewById(R.id.contentTextView);
            avatarImageView = itemView.findViewById(R.id.avatarImageView);
            tagsChipGroup = itemView.findViewById(R.id.tagsChipGroup);
            likeCountTextView = itemView.findViewById(R.id.likeCountTextView);
            commentButton = itemView.findViewById(R.id.commentButton);
            likeButton = itemView.findViewById(R.id.likeButton);
            shareButton = itemView.findViewById(R.id.shareButton);
            postMenuButton = itemView.findViewById(R.id.postMenuButton);

            // Type-specific elements
            switch (postType) {
                case SINGLE_IMAGE:
                    mediaContainer = itemView.findViewById(R.id.mediaContainer);
                    mediaImageView = itemView.findViewById(R.id.mediaImageView);
                    playButton = itemView.findViewById(R.id.playButton);
                    break;

                case MULTI_IMAGE:
                    mediaGalleryContainer = itemView.findViewById(R.id.mediaGalleryContainer);
                    imageGridLayout = itemView.findViewById(R.id.imageGridLayout);
                    imageViewPager = itemView.findViewById(R.id.imageViewPager);
                    imageCounterTextView = itemView.findViewById(R.id.imageCounterTextView);
                    break;

                case TASK:
                    taskButtonsContainer = itemView.findViewById(R.id.taskButtonsContainer);
                    mediaContainer = itemView.findViewById(R.id.mediaContainer);
                    mediaImageView = itemView.findViewById(R.id.mediaImageView);

                    // Make sure link preview elements are initialized
                    // The findViewById calls happen in bindTaskPost now
                    break;
            }

            // Prevent default ripple effect when clicking by making item not clickable
            itemView.setClickable(false);

            // Still allow click events without animation by sending them through the listener
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPostClick(feedPosts.get(position));
                }
            });
            
            // Apply theme colors
            applyTheme();
        }
        
        /**
         * Apply theme colors to buttons and UI elements
         */
        private void applyTheme() {
            FastCommentsTheme theme = sdk != null ? sdk.getTheme() : null;
            
            // Apply action button colors to buttons
            int actionButtonColor = ThemeColorResolver.getActionButtonColor(context, theme);
            
            if (commentButton != null) {
                commentButton.setTextColor(actionButtonColor);
            }
            if (likeButton != null) {
                likeButton.setTextColor(actionButtonColor);
            }
            if (shareButton != null) {
                shareButton.setTextColor(actionButtonColor);
            }
            if (postMenuButton != null) {
                postMenuButton.setImageTintList(ColorStateList.valueOf(actionButtonColor));
            }
        }
        
        /**
         * Apply theme colors to a dynamically created button
         */
        private void applyThemeToButton(Button button) {
            FastCommentsTheme theme = sdk != null ? sdk.getTheme() : null;
            
            // Apply action button color to the button text
            int actionButtonColor = ThemeColorResolver.getActionButtonColor(context, theme);
            button.setTextColor(actionButtonColor);
        }

        void bind(FeedPost post, int position) {
            // Handle user information
            if (post.getFromUserDisplayName() != null) {
                String userName = post.getFromUserDisplayName();
                userNameTextView.setText(userName);
                userNameTextView.setVisibility(View.VISIBLE);

                // If there's an avatar URL, load it - otherwise show a default avatar
                if (avatarImageView != null) {
                    if (post.getFromUserAvatar() != null && !post.getFromUserAvatar().isEmpty()) {
                        avatarImageView.setVisibility(View.VISIBLE);
                        AvatarFetcher.fetchTransformInto(context, post.getFromUserAvatar(), avatarImageView);
                    } else {
                        // Show default avatar image
                        avatarImageView.setVisibility(View.VISIBLE);
                        AvatarFetcher.fetchTransformInto(context, R.drawable.default_avatar, avatarImageView);
                    }
                }
            } else {
                // Hide user information for anonymous posts
                userNameTextView.setVisibility(View.GONE);
                if (avatarImageView != null) {
                    avatarImageView.setVisibility(View.GONE);
                }
            }

            postTimeTextView.setText(formatTimestamp(post.getCreatedAt()));

            // Set content
            if (post.getContentHTML() != null) {
                contentTextView.setText(Html.fromHtml(post.getContentHTML(), Html.FROM_HTML_MODE_COMPACT));
                contentTextView.setVisibility(View.VISIBLE);
            } else {
                contentTextView.setVisibility(View.GONE);
            }

            // Set tags if available
            setupTags(post);

            // Handle like count and comment count display
            updateLikeAndCommentCounts(post);

            // Handle like button state based on user's reactions from SDK
            updateLikeButtonState(post);

            // Set common button actions
            commentButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCommentClick(post);
                }
            });

            likeButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onLikeClick(post, position);
                }
            });

            shareButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onShareClick(post);
                }
            });

            // Make comment count clickable - when user taps on comment count, open comments
            likeCountTextView.setOnClickListener(v -> {
                if (listener != null && post.getCommentCount() != null && post.getCommentCount() > 0) {
                    listener.onCommentClick(post);
                }
            });

            // Post menu button setup
            if (postMenuButton != null) {
                // Determine if this is the current user's post
                boolean isCurrentUserPost = false;
                String currentUserId = sdk.getCurrentUser() != null ? sdk.getCurrentUser().getId() : null;
                String postUserId = post.getFromUserId();

                if (currentUserId != null && postUserId != null && currentUserId.equals(postUserId)) {
                    isCurrentUserPost = true;
                    postMenuButton.setVisibility(View.VISIBLE);

                    postMenuButton.setOnClickListener(v -> showPostMenu(post));
                } else {
                    postMenuButton.setVisibility(View.GONE);
                }
            }

            // Handle type-specific bindings
            switch (postType) {
                case SINGLE_IMAGE:
                    bindSingleImagePost(post);
                    break;
                case MULTI_IMAGE:
                    bindMultiImagePost(post);
                    break;
                case TASK:
                    bindTaskPost(post);
                    break;
                case TEXT_ONLY:
                    // No additional binding needed for text-only posts
                    break;
            }
        }

        private void bindSingleImagePost(FeedPost post) {
            if (post.getMedia() != null && !post.getMedia().isEmpty()) {
                FeedPostMediaItem mediaItem = post.getMedia().get(0);

                if (mediaItem.getSizes() != null && !mediaItem.getSizes().isEmpty()) {
                    // Select most appropriate size for display
                    FeedPostMediaItemAsset bestSizeAsset = selectBestImageSize(mediaItem.getSizes());

                    if (bestSizeAsset != null && bestSizeAsset.getSrc() != null) {
                        mediaContainer.setVisibility(View.VISIBLE);

                        // Calculate and set height upfront to prevent layout shifts
                        int containerWidth = mediaContainer.getWidth();
                        if (containerWidth == 0) {
                            // Container not yet measured, use screen width as estimate
                            containerWidth = context.getResources().getDisplayMetrics().widthPixels;
                        }

                        int calculatedHeight = calculateImageHeight(bestSizeAsset, containerWidth);
                        mediaImageView.getLayoutParams().height = calculatedHeight;
                        mediaImageView.requestLayout();

                        // Load image with calculated dimensions
                        Glide.with(context)
                                .load(bestSizeAsset.getSrc())
                                .override(containerWidth, calculatedHeight)
                                .transition(DrawableTransitionOptions.withCrossFade(300))
                                .error(R.drawable.image_placeholder)
                                .into(mediaImageView);

                        // Determine if it's a video
                        boolean isVideo = mediaItem.getLinkUrl() != null &&
                                (mediaItem.getLinkUrl().contains("youtube") ||
                                        mediaItem.getLinkUrl().contains("vimeo") ||
                                        mediaItem.getLinkUrl().contains(".mp4"));

                        playButton.setVisibility(isVideo ? View.VISIBLE : View.GONE);

                        // Make sure both the container and the image itself are clickable
                        mediaContainer.setClickable(true);
                        mediaContainer.setFocusable(true);
                        mediaImageView.setClickable(true);
                        mediaImageView.setFocusable(true);

                        // Set click listener for the image
                        mediaImageView.setOnClickListener(v -> handleImageClick(post, mediaItem, 0));

                        // Set click listener for media container as a backup
                        mediaContainer.setOnClickListener(v -> handleImageClick(post, mediaItem, 0));
                    } else {
                        mediaContainer.setVisibility(View.GONE);
                    }
                } else {
                    mediaContainer.setVisibility(View.GONE);
                }
            } else {
                mediaContainer.setVisibility(View.GONE);
            }
        }

        // Reference to the new 3-images layout
        private LinearLayout threeImagesLayout;
        private ImageView topImageView, bottomLeftImageView, bottomRightImageView;

        private void bindMultiImagePost(FeedPost post) {
            if (post.getMedia() != null && !post.getMedia().isEmpty()) {
                mediaItems = post.getMedia();

                // Make sure we have references to the new layout
                if (threeImagesLayout == null) {
                    threeImagesLayout = itemView.findViewById(R.id.threeImagesLayout);
                    topImageView = itemView.findViewById(R.id.topImageView);
                    bottomLeftImageView = itemView.findViewById(R.id.bottomLeftImageView);
                    bottomRightImageView = itemView.findViewById(R.id.bottomRightImageView);
                }

                // Special case for exactly 3 images - use the dedicated layout
                if (mediaItems.size() == 3) {
                    // Show 3-image layout, hide others
                    threeImagesLayout.setVisibility(View.VISIBLE);
                    imageGridLayout.setVisibility(View.GONE);
                    imageViewPager.setVisibility(View.GONE);
                    imageCounterTextView.setVisibility(View.GONE);

                    // Set up the 3 images
                    setupThreeImagesLayout(post, mediaItems);

                    // Make sure container is visible
                    mediaGalleryContainer.setVisibility(View.VISIBLE);
                }
                // For posts with 1-2 images, use grid layout
                else if (mediaItems.size() <= 2) {
                    imageGridLayout.setVisibility(View.VISIBLE);
                    threeImagesLayout.setVisibility(View.GONE);
                    imageViewPager.setVisibility(View.GONE);
                    imageCounterTextView.setVisibility(View.GONE);

                    // Clear existing views
                    imageGridLayout.removeAllViews();

                    // Set up grid layout based on number of images
                    setupImageGrid(post, mediaItems);

                    // Make sure GridLayout is actually visible
                    imageGridLayout.invalidate();
                    mediaGalleryContainer.setVisibility(View.VISIBLE);
                } else {
                    // For 4+ images, use the ViewPager
                    imageGridLayout.setVisibility(View.GONE);
                    threeImagesLayout.setVisibility(View.GONE);
                    imageViewPager.setVisibility(View.VISIBLE);
                    imageCounterTextView.setVisibility(View.VISIBLE);

                    // Pre-size the ViewPager to prevent layout shifts
                    int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                    int viewPagerHeight = getDefaultImageHeight(); // Use default height for consistency
                    
                    // Try to calculate a better height from the first image
                    if (!mediaItems.isEmpty()) {
                        FeedPostMediaItem firstItem = mediaItems.get(0);
                        if (firstItem.getSizes() != null && !firstItem.getSizes().isEmpty()) {
                            FeedPostMediaItemAsset bestAsset = selectBestImageSize(firstItem.getSizes());
                            if (bestAsset != null) {
                                viewPagerHeight = calculateImageHeight(bestAsset, screenWidth);
                            }
                        }
                    }
                    
                    // Set the ViewPager height
                    imageViewPager.getLayoutParams().height = viewPagerHeight;
                    imageViewPager.requestLayout();

                    // Create and set up the images adapter
                    imagesAdapter = new PostImagesAdapter(context, mediaItems, mediaItem -> {
                        if (listener != null) {
                            listener.onMediaClick(mediaItem);
                        }
                    });

                    // Set up the ViewPager
                    imageViewPager.setAdapter(imagesAdapter);

                    // Initial counter update
                    updateImageCounter(0);

                    // Clean up any existing callback
                    if (pageChangeCallback != null) {
                        imageViewPager.unregisterOnPageChangeCallback(pageChangeCallback);
                    }

                    // Create and register new callback
                    pageChangeCallback = new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                        @Override
                        public void onPageSelected(int position) {
                            updateImageCounter(position);
                        }
                    };
                    imageViewPager.registerOnPageChangeCallback(pageChangeCallback);
                }
            }
        }

        /**
         * Set up the grid layout for 1-3 images
         *
         * @param post The post containing the media items
         * @param mediaItems The list of media items
         */
        private void setupImageGrid(FeedPost post, List<FeedPostMediaItem> mediaItems) {
            int count = mediaItems.size();
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;

            if (count == 1) {
                // Single image takes full size
                FeedPostMediaItem mediaItem = mediaItems.get(0);
                
                // Pre-calculate and set grid height to prevent layout shifts
                if (mediaItem.getSizes() != null && !mediaItem.getSizes().isEmpty()) {
                    FeedPostMediaItemAsset bestAsset = selectBestImageSize(mediaItem.getSizes());
                    if (bestAsset != null) {
                        int calculatedHeight = calculateImageHeight(bestAsset, screenWidth);
                        imageGridLayout.getLayoutParams().height = calculatedHeight;
                        imageGridLayout.requestLayout();
                    }
                }
                
                ImageView imageView = createImageView(post, mediaItem, 0);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                        GridLayout.spec(0, 2, 1f),
                        GridLayout.spec(0, 2, 1f));
                params.width = GridLayout.LayoutParams.MATCH_PARENT;
                params.height = GridLayout.LayoutParams.MATCH_PARENT;
                imageGridLayout.addView(imageView, params);
            } else if (count == 2) {
                // Pre-calculate total height for two stacked images
                int totalHeight = 0;
                for (int i = 0; i < count; i++) {
                    FeedPostMediaItem mediaItem = mediaItems.get(i);
                    if (mediaItem.getSizes() != null && !mediaItem.getSizes().isEmpty()) {
                        FeedPostMediaItemAsset bestAsset = selectBestImageSize(mediaItem.getSizes());
                        if (bestAsset != null) {
                            int imageHeight = calculateImageHeight(bestAsset, screenWidth);
                            totalHeight += imageHeight;
                            if (i > 0) totalHeight += 4; // Add margin between images
                        }
                    }
                }
                
                // Set grid height to prevent layout shifts
                if (totalHeight > 0) {
                    imageGridLayout.getLayoutParams().height = totalHeight;
                    imageGridLayout.requestLayout();
                }
                
                // Set up GridLayout configuration for 2 rows, 1 column
                imageGridLayout.setRowCount(2);
                imageGridLayout.setColumnCount(1);
                
                // Two images stacked vertically
                for (int i = 0; i < count; i++) {
                    FeedPostMediaItem mediaItem = mediaItems.get(i);
                    ImageView imageView = createImageView(post, mediaItem, i);
                    
                    // Calculate individual image height for proper layout
                    int individualHeight = getHalfImageHeight(); // Default fallback
                    if (mediaItem.getSizes() != null && !mediaItem.getSizes().isEmpty()) {
                        FeedPostMediaItemAsset bestAsset = selectBestImageSize(mediaItem.getSizes());
                        if (bestAsset != null) {
                            individualHeight = calculateImageHeight(bestAsset, screenWidth);
                        }
                    }
                    
                    GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                            GridLayout.spec(i, 1),  // i'th row, span 1 row
                            GridLayout.spec(0, 1));  // column 0, span 1 column
                    params.width = GridLayout.LayoutParams.MATCH_PARENT;
                    params.height = GridLayout.LayoutParams.WRAP_CONTENT;
                    params.setMargins(0, i > 0 ? 4 : 0, 0, i < count - 1 ? 4 : 0); // Vertical margins between images
                    imageGridLayout.addView(imageView, params);
                }
            }
        }

        /**
         * Set up the three images layout with the given media items
         * This is a dedicated method for handling exactly 3 images
         */
        private void setupThreeImagesLayout(FeedPost post, List<FeedPostMediaItem> mediaItems) {
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int halfWidth = screenWidth / 2;
            
            // Pre-calculate layout height to prevent shifting
            FeedPostMediaItem firstItem = mediaItems.get(0);
            FeedPostMediaItem secondItem = mediaItems.get(1);
            FeedPostMediaItem thirdItem = mediaItems.get(2);
            
            int topImageHeight = 0;
            int bottomImageHeight = 0;
            
            // Calculate top image height (full width)
            if (firstItem.getSizes() != null && !firstItem.getSizes().isEmpty()) {
                FeedPostMediaItemAsset bestAsset = selectBestImageSize(firstItem.getSizes());
                if (bestAsset != null) {
                    topImageHeight = calculateImageHeight(bestAsset, screenWidth);
                }
            }
            
            // Calculate bottom images height (half width each, use the taller one)
            if (secondItem.getSizes() != null && !secondItem.getSizes().isEmpty()) {
                FeedPostMediaItemAsset bestAsset = selectBestImageSize(secondItem.getSizes());
                if (bestAsset != null) {
                    bottomImageHeight = Math.max(bottomImageHeight, calculateImageHeight(bestAsset, halfWidth));
                }
            }
            
            if (thirdItem.getSizes() != null && !thirdItem.getSizes().isEmpty()) {
                FeedPostMediaItemAsset bestAsset = selectBestImageSize(thirdItem.getSizes());
                if (bestAsset != null) {
                    bottomImageHeight = Math.max(bottomImageHeight, calculateImageHeight(bestAsset, halfWidth));
                }
            }
            
            // Set the three images layout height
            int totalHeight = topImageHeight + bottomImageHeight + 4; // 4dp margin between rows
            if (totalHeight > 0) {
                threeImagesLayout.getLayoutParams().height = totalHeight;
                threeImagesLayout.requestLayout();
                
                // Pre-size individual image views
                if (topImageHeight > 0) {
                    topImageView.getLayoutParams().height = topImageHeight;
                    topImageView.requestLayout();
                }
                
                if (bottomImageHeight > 0) {
                    bottomLeftImageView.getLayoutParams().height = bottomImageHeight;
                    bottomLeftImageView.requestLayout();
                    bottomRightImageView.getLayoutParams().height = bottomImageHeight;
                    bottomRightImageView.requestLayout();
                }
            }
            
            // Load top image (first image)
            loadImageIntoView(firstItem, topImageView);

            // Set click listener for full screen viewing - start at image 0 (first image)
            topImageView.setOnClickListener(v -> handleImageClick(post, firstItem, 0));

            // Load bottom left image (second image)
            loadImageIntoView(secondItem, bottomLeftImageView);

            // Set click listener for full screen viewing - start at image 1 (second image)
            bottomLeftImageView.setOnClickListener(v -> handleImageClick(post, secondItem, 1));

            // Load bottom right image (third image)
            loadImageIntoView(thirdItem, bottomRightImageView);

            // Set click listener for full screen viewing - start at image 2 (third image)
            bottomRightImageView.setOnClickListener(v -> handleImageClick(post, thirdItem, 2));
        }

        /**
         * Helper method to load an image into an ImageView
         */
        private void loadImageIntoView(FeedPostMediaItem mediaItem, ImageView imageView) {
            if (mediaItem.getSizes() != null && !mediaItem.getSizes().isEmpty()) {
                FeedPostMediaItemAsset bestAsset = selectBestImageSize(mediaItem.getSizes());
                if (bestAsset != null && bestAsset.getSrc() != null) {
                    // Get parent width for calculating the appropriate height
                    int parentWidth;
                    if (imageView.getParent() instanceof View) {
                        View parent = (View) imageView.getParent();
                        parentWidth = parent.getWidth();
                        // If parent width is zero (not yet measured), use screen width
                        if (parentWidth == 0) {
                            parentWidth = context.getResources().getDisplayMetrics().widthPixels;
                            // Adjust for multi-image layouts where images may not take full width
                            if (imageView != topImageView) {
                                parentWidth = parentWidth / 2; // Rough estimate for side-by-side images
                            }
                        }
                    } else {
                        // Fallback to screen width if no parent
                        parentWidth = context.getResources().getDisplayMetrics().widthPixels;
                    }

                    // Calculate height based on aspect ratio
                    int calculatedHeight = calculateImageHeight(bestAsset, parentWidth);

                    // Set the calculated height
                    imageView.getLayoutParams().height = calculatedHeight;
                    imageView.requestLayout();

                    Glide.with(context)
                            .load(bestAsset.getSrc())
                            .override(parentWidth, calculatedHeight)
                            .transition(DrawableTransitionOptions.withCrossFade(300))
                            .error(R.drawable.image_placeholder)
                            .into(imageView);
                } else {
                    imageView.setImageResource(R.drawable.image_placeholder);
                    imageView.getLayoutParams().height = getHalfImageHeight();
                    imageView.requestLayout();
                }
            } else {
                imageView.setImageResource(R.drawable.image_placeholder);
                imageView.getLayoutParams().height = getHalfImageHeight();
                imageView.requestLayout();
            }
        }

        /**
         * Create an ImageView for a media item
         *
         * @param post The post containing the media
         * @param mediaItem The media item to display
         * @param position The position of this media item in the post's media list
         * @return A configured ImageView
         */
        private ImageView createImageView(FeedPost post, FeedPostMediaItem mediaItem, int position) {
            ImageView imageView = new ImageView(context);

            // Set up ImageView properties similar to single image view
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setAdjustViewBounds(true);

            // Use full screen width for stacked images
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;

            // Load image using Glide if media item has sizes
            if (mediaItem.getSizes() != null && !mediaItem.getSizes().isEmpty()) {
                FeedPostMediaItemAsset bestSizeAsset = selectBestImageSize(mediaItem.getSizes());

                if (bestSizeAsset != null && bestSizeAsset.getSrc() != null) {
                    // Get parent width for calculating the appropriate height
                    int parentWidth;
                    if (imageView.getParent() instanceof View) {
                        View parent = (View) imageView.getParent();
                        parentWidth = parent.getWidth();
                        // If parent width is zero (not yet measured), use screen width
                        if (parentWidth == 0) {
                            parentWidth = context.getResources().getDisplayMetrics().widthPixels;
                            // Adjust for multi-image layouts where images may not take full width
                            if (imageView != topImageView) {
                                parentWidth = parentWidth / 2; // Rough estimate for side-by-side images
                            }
                        }
                    } else {
                        // Fallback to screen width if no parent
                        parentWidth = context.getResources().getDisplayMetrics().widthPixels;
                    }

                    // Set up layout parameters for wrap_content height
                    ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    imageView.setLayoutParams(layoutParams);

                    int calculatedHeight = calculateImageHeight(bestSizeAsset, parentWidth);

                    // Set the calculated height
                    imageView.getLayoutParams().height = calculatedHeight;
                    imageView.requestLayout();

                    // Let Glide load the image with natural aspect ratio
                    Glide.with(context)
                            .load(bestSizeAsset.getSrc())
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .override(parentWidth, calculatedHeight)
                            .error(R.drawable.image_placeholder)
                            .into(imageView);
                } else {
                    // Set fallback if no valid asset
                    ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    imageView.setLayoutParams(layoutParams);
                    imageView.setImageResource(R.drawable.image_placeholder);
                }
            } else {
                // Set fallback if no sizes
                ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                imageView.setLayoutParams(layoutParams);
                imageView.setImageResource(R.drawable.image_placeholder);
            }

            // Set click listener
            imageView.setOnClickListener(v -> handleImageClick(post, mediaItem, position));

            return imageView;
        }

        /**
         * Update the image counter text (e.g., "1/5")
         *
         * @param position Current page position
         */
        private void updateImageCounter(int position) {
            if (mediaItems == null || mediaItems.isEmpty() || imageCounterTextView == null) {
                return;
            }

            String counterText = String.format("%d/%d", position + 1, mediaItems.size());
            imageCounterTextView.setText(counterText);
        }

        private void bindTaskPost(FeedPost post) {
            View linkPreviewContainer = itemView.findViewById(R.id.linkPreviewContainer);
            TextView linkTitleTextView = itemView.findViewById(R.id.linkTitleTextView);
            TextView linkDescriptionTextView = itemView.findViewById(R.id.linkDescriptionTextView);
            LinearLayout richLinkButtonsContainer = itemView.findViewById(R.id.richLinkButtonsContainer);

            // Handle optional media
            if (post.getMedia() != null && !post.getMedia().isEmpty()) {
                FeedPostMediaItem mediaItem = post.getMedia().get(0);
                if (mediaItem.getSizes() != null && !mediaItem.getSizes().isEmpty()) {
                    // Select best size for display
                    FeedPostMediaItemAsset bestSizeAsset = selectBestImageSize(mediaItem.getSizes());

                    if (bestSizeAsset != null && bestSizeAsset.getSrc() != null) {
                        mediaContainer.setVisibility(View.VISIBLE);

                        // Calculate and set height upfront to prevent layout shifts
                        int containerWidth = mediaContainer.getWidth();
                        if (containerWidth == 0) {
                            // Container not yet measured, use screen width as estimate
                            containerWidth = context.getResources().getDisplayMetrics().widthPixels;
                        }

                        int calculatedHeight = calculateImageHeight(bestSizeAsset, containerWidth);
                        mediaImageView.getLayoutParams().height = calculatedHeight;
                        mediaImageView.requestLayout();

                        // Load image with calculated dimensions
                        Glide.with(context)
                                .load(bestSizeAsset.getSrc())
                                .override(containerWidth, calculatedHeight)
                                .transition(DrawableTransitionOptions.withCrossFade(300))
                                .error(R.drawable.image_placeholder)
                                .into(mediaImageView);

                        // Make sure image and container are clickable
                        mediaContainer.setClickable(true);
                        mediaContainer.setFocusable(true);
                        mediaImageView.setClickable(true);
                        mediaImageView.setFocusable(true);

                        // Set click listeners for both container and image
                        View.OnClickListener imageClickListener = v -> handleImageClick(post, mediaItem, 0);

                        mediaContainer.setOnClickListener(imageClickListener);
                        mediaImageView.setOnClickListener(imageClickListener);
                    } else {
                        mediaContainer.setVisibility(View.GONE);
                    }
                } else {
                    mediaContainer.setVisibility(View.GONE);
                }
            } else {
                mediaContainer.setVisibility(View.GONE);
            }

            // Clear button containers
            taskButtonsContainer.removeAllViews();
            richLinkButtonsContainer.removeAllViews();

            if (post.getLinks() != null && !post.getLinks().isEmpty()) {
                FeedPostLink primaryLink = post.getLinks().get(0); // Use the first link as primary

                // Check if we have a title or description to display
                boolean hasTitleOrDescription =
                        (primaryLink.getTitle() != null && !primaryLink.getTitle().isEmpty()) ||
                                (primaryLink.getDescription() != null && !primaryLink.getDescription().isEmpty());

                if (hasTitleOrDescription) {
                    // layout with title/description + button on right
                    linkPreviewContainer.setVisibility(View.VISIBLE);
                    taskButtonsContainer.setVisibility(View.GONE); // Hide regular buttons container

                    // Display link title if available
                    if (primaryLink.getTitle() != null && !primaryLink.getTitle().isEmpty()) {
                        linkTitleTextView.setText(primaryLink.getTitle());
                        linkTitleTextView.setVisibility(View.VISIBLE);
                    } else {
                        linkTitleTextView.setVisibility(View.GONE);
                    }

                    // Display link description if available
                    if (primaryLink.getDescription() != null && !primaryLink.getDescription().isEmpty()) {
                        linkDescriptionTextView.setText(primaryLink.getDescription());
                        linkDescriptionTextView.setVisibility(View.VISIBLE);
                    } else {
                        linkDescriptionTextView.setVisibility(View.GONE);
                    }

                    // Create action buttons for each link - vertical on right
                    int buttonCount = post.getLinks().size();
                    for (int i = 0; i < buttonCount; i++) {
                        FeedPostLink link = post.getLinks().get(i);
                        Button actionButton = new Button(context);

                        // Vertical button on right side
                        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);
                        buttonParams.setMargins(0, i > 0 ? 8 : 0, 0, 0); // Add top margin for subsequent buttons

                        actionButton.setLayoutParams(buttonParams);

                        // Apply modern styling with our custom background
                        actionButton.setBackgroundResource(R.drawable.task_button_background);
                        actionButton.setPadding(16, 12, 16, 12); // Increased vertical padding for taller buttons
                        
                        // Apply theme colors
                        applyThemeToButton(actionButton);

                        // Set button text
                        String buttonText = link.getText(); // Prefer the display text if available
                        if (buttonText == null || buttonText.isEmpty()) {
                            buttonText = link.getTitle(); // Try title next
                            if (buttonText == null || buttonText.isEmpty()) {
                                buttonText = link.getUrl() != null ?
                                        context.getString(R.string.view_details) :
                                        context.getString(R.string.learn_more);
                            }
                        }

                        actionButton.setText(buttonText);
                        actionButton.setTextSize(12); // Smaller text size to fit better
                        actionButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        actionButton.setSingleLine(true);
                        actionButton.setAllCaps(false); // More modern look with lowercase

                        // Set click listener
                        final String url = link.getUrl();
                        actionButton.setOnClickListener(v -> {
                            if (listener != null && url != null) {
                                listener.onLinkClick(url);
                            }
                        });

                        // For first button, add vertical margins to center it
                        if (i == 0) {
                            actionButton.setMinWidth(100); // Ensure button has reasonable width
                            buttonParams.setMargins(12, 0, 12, 0); // Add side margins
                        }

                        // Add button to rich link buttons container
                        richLinkButtonsContainer.addView(actionButton);
                    }
                } else {
                    // No title/description - use traditional horizontal button layout
                    linkPreviewContainer.setVisibility(View.GONE);
                    taskButtonsContainer.setVisibility(View.VISIBLE); // Show the regular buttons container

                    // For multiple buttons, we need to handle them differently depending on count
                    int buttonCount = post.getLinks().size();

                    // If we have 1-3 buttons, use a horizontal layout with equal width
                    if (buttonCount <= 3) {
                        // Create a horizontal layout for the buttons
                        LinearLayout buttonRow = new LinearLayout(context);
                        buttonRow.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT));
                        buttonRow.setOrientation(LinearLayout.HORIZONTAL);

                        // Set equal distribution of buttons
                        int buttonWeight = 1;

                        // Add buttons horizontally with equal weight
                        for (int i = 0; i < buttonCount; i++) {
                            FeedPostLink link = post.getLinks().get(i);
                            Button actionButton = new Button(context);

                            // Create layout params with weight for equal distribution
                            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                                    0, // 0dp width with weight for equal distribution
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    buttonWeight); // Equal weight for each button

                            // Add margins between buttons (but not on edges)
                            if (i > 0) {
                                buttonParams.setMarginStart(8); // Add margin between buttons
                            }
                            if (i < buttonCount - 1) {
                                buttonParams.setMarginEnd(8); // Add margin between buttons
                            }

                            actionButton.setLayoutParams(buttonParams);

                            // Apply modern styling with our custom background
                            actionButton.setBackgroundResource(R.drawable.task_button_background);
                            // Apply theme colors
                            applyThemeToButton(actionButton);
                            actionButton.setPadding(16, 12, 16, 12); // Increased vertical padding for taller buttons
                            actionButton.setMinHeight(48); // Set minimum height to 48dp (standard button height)

                            // Set button text
                            String buttonText = link.getText(); // Prefer the display text if available
                            if (buttonText == null || buttonText.isEmpty()) {
                                buttonText = link.getUrl() != null ?
                                        context.getString(R.string.view_details) :
                                        context.getString(R.string.learn_more);
                            }

                            actionButton.setText(buttonText);
                            actionButton.setTextSize(12); // Smaller text size to fit better
                            actionButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
                            actionButton.setSingleLine(true);
                            actionButton.setAllCaps(false); // More modern look with lowercase

                            // Set click listener
                            final String url = link.getUrl();
                            actionButton.setOnClickListener(v -> {
                                if (listener != null && url != null) {
                                    listener.onLinkClick(url);
                                }
                            });

                            // Add to the horizontal row
                            buttonRow.addView(actionButton);
                        }

                        // Add the row to the container
                        taskButtonsContainer.addView(buttonRow);
                    } else {
                        // For 4+ buttons, stack them in a vertical layout
                        // Each button gets full width for better readability
                        for (int i = 0; i < buttonCount; i++) {
                            FeedPostLink link = post.getLinks().get(i);
                            Button actionButton = new Button(context);

                            // Full width button
                            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT);

                            // Add margin between buttons
                            if (i > 0) {
                                buttonParams.setMargins(0, 8, 0, 0); // Add top margin
                            }

                            actionButton.setLayoutParams(buttonParams);

                            // Apply modern styling with our custom background
                            actionButton.setBackgroundResource(R.drawable.task_button_background);
                            // Apply theme colors
                            applyThemeToButton(actionButton);
                            actionButton.setPadding(16, 12, 16, 12); // Increased vertical padding for taller buttons
                            actionButton.setMinHeight(48); // Set minimum height to 48dp (standard button height)

                            // Set button text
                            String buttonText = link.getText(); // Prefer the display text if available
                            if (buttonText == null || buttonText.isEmpty()) {
                                buttonText = link.getUrl() != null ?
                                        context.getString(R.string.view_details) :
                                        context.getString(R.string.learn_more);
                            }

                            actionButton.setText(buttonText);
                            actionButton.setTextSize(12); // Smaller text size to fit better
                            actionButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
                            actionButton.setSingleLine(true);
                            actionButton.setAllCaps(false); // More modern look with lowercase

                            // Set click listener
                            final String url = link.getUrl();
                            actionButton.setOnClickListener(v -> {
                                if (listener != null && url != null) {
                                    listener.onLinkClick(url);
                                }
                            });

                            // Add button directly to container (vertical stacking)
                            taskButtonsContainer.addView(actionButton);
                        }
                    }
                }
            } else {
                // No links, hide both link containers
                linkPreviewContainer.setVisibility(View.GONE);
                taskButtonsContainer.setVisibility(View.GONE);
            }
        }

        private void setupTags(FeedPost post) {
            tagsChipGroup.removeAllViews();

            if (post.getTags() != null && !post.getTags().isEmpty()) {
                tagsChipGroup.setVisibility(View.VISIBLE);

                for (String tag : post.getTags()) {
                    Chip chip = new Chip(context);
                    chip.setText(tag);
                    chip.setClickable(true);
                    chip.setCheckable(false);
                    chip.setChipBackgroundColorResource(android.R.color.transparent);
                    chip.setChipStrokeWidth(1f);
                    chip.setChipStrokeColorResource(android.R.color.darker_gray);

                    tagsChipGroup.addView(chip);
                }
            } else {
                tagsChipGroup.setVisibility(View.GONE);
            }
        }

        /**
         * Get the best quality image URL for full-screen viewing
         * For full-screen viewing, we want the highest quality image available
         *
         * @param mediaItem The media item to get the URL from
         * @return The URL of the highest quality image, or null if not available
         */
        private String getBestQualityImageUrl(FeedPostMediaItem mediaItem) {
            if (mediaItem == null || mediaItem.getSizes() == null || mediaItem.getSizes().isEmpty()) {
                return null;
            }

            // Find the image with the highest resolution (largest width)
            FeedPostMediaItemAsset highestQualityAsset = null;
            double maxWidth = 0;

            for (FeedPostMediaItemAsset asset : mediaItem.getSizes()) {
                if (asset != null && asset.getSrc() != null && asset.getW() != null) {
                    double width = asset.getW();
                    if (width > maxWidth) {
                        maxWidth = width;
                        highestQualityAsset = asset;
                    }
                }
            }

            return highestQualityAsset != null ? highestQualityAsset.getSrc() : null;
        }

        /**
         * Shows the post menu with options
         *
         * @param post The post to show menu for
         */
        private void showPostMenu(FeedPost post) {
            // Create popup menu
            PopupMenu popupMenu = new PopupMenu(context, postMenuButton);
            popupMenu.inflate(R.menu.feed_post_menu);

            // Set item click listener
            popupMenu.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();

                if (itemId == R.id.menu_delete_post) {
                    // Handle delete post
                    if (listener != null) {
                        listener.onDeletePost(post);
                        return true;
                    }
                }

                return false;
            });

            // Show the popup menu
            popupMenu.show();
        }

        /**
         * Update the like count and comment count display
         */
        private void updateLikeAndCommentCounts(FeedPost post) {
            final int likeCount = sdk.getPostLikeCount(post.getId());
            final Integer commentCount = post.getCommentCount();
            
            if (likeCount > 0 || (commentCount != null && commentCount > 0)) {
                likeCountTextView.setVisibility(View.VISIBLE);
                
                StringBuilder displayText = new StringBuilder();
                
                // Add likes text if there are likes
                if (likeCount > 0) {
                    // Show heart icon only when there are likes
                    likeCountTextView.setCompoundDrawablesWithIntrinsicBounds(
                            context.getResources().getDrawable(R.drawable.heart_filled_small, null), 
                            null, null, null);
                            
                    String likesText = likeCount == 1 ?
                            context.getString(R.string.like_count_singular, likeCount) :
                            context.getString(R.string.like_count_plural, likeCount);
                    displayText.append(likesText);
                } else {
                    // No likes, so don't show heart icon
                    likeCountTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                }
                
                // Add comment count if there are comments
                if (commentCount != null && commentCount > 0) {
                    // Add separator if needed
                    if (likeCount > 0) {
                        displayText.append(" · ");
                    }
                    
                    String commentsText = commentCount == 1 ?
                            context.getString(R.string.comment_count_singular, commentCount) :
                            context.getString(R.string.comment_count_plural, commentCount);
                    displayText.append(commentsText);
                }
                
                // Set the text
                likeCountTextView.setText(displayText.toString());
            } else {
                likeCountTextView.setVisibility(View.GONE);
            }
        }

        /**
         * Update the like button state
         */
        private void updateLikeButtonState(FeedPost post) {
            final boolean userHasLiked = sdk.hasUserReactedToPost(post.getId(), "l");
            if (userHasLiked) {
                likeButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.like_filled_icon, 0, 0, 0);
                likeButton.setTextColor(context.getResources().getColor(android.R.color.holo_red_light, null));
                likeButton.setText(R.string.liked);
            } else {
                likeButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.like_icon, 0, 0, 0);
                likeButton.setText(R.string.like);
            }
        }

        /**
         * Update only stats and likes without re-binding the entire view
         */
        void updateStatsAndLikes(FeedPost post) {
            updateLikeAndCommentCounts(post);
            updateLikeButtonState(post);
        }

        /**
         * Helper method to handle image click and show appropriate dialog
         * @param post The post containing the image
         * @param mediaItem The specific media item clicked
         * @param imagePosition The position of the clicked image in the post's media list
         */
        private void handleImageClick(FeedPost post, FeedPostMediaItem mediaItem, int imagePosition) {
            // If this post has multiple images, show gallery mode
            if (post.getMedia() != null && post.getMedia().size() > 1) {
                new FullImageDialog(context, post.getMedia(), imagePosition).show();
            } else {
                // Single image mode - get best quality image URL
                String fullImageUrl = getBestQualityImageUrl(mediaItem);
                if (fullImageUrl != null) {
                    new FullImageDialog(context, fullImageUrl).show();
                }
            }

            // Also notify the listener if set
            if (listener != null) {
                listener.onMediaClick(mediaItem);
            }
        }

        /**
         * Format timestamp based on SDK configuration
         * Uses the same logic as CommentViewHolder.updateDateDisplay()
         */
        private String formatTimestamp(OffsetDateTime date) {
            if (date == null) {
                return "";
            }

            if (useAbsoluteDates) {
                // Use system's locale-aware date formatting
                Locale currentLocale = context.getResources().getConfiguration().getLocales().get(0);
                DateTimeFormatter formatter = DateTimeFormatter
                        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                        .withLocale(currentLocale);

                return date.format(formatter);
            } else {
                // Format as relative date: 2 minutes ago, 1 hour ago, etc.
                CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                        date.toInstant().toEpochMilli(),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                );
                return relativeTime.toString();
            }
        }
    }
}