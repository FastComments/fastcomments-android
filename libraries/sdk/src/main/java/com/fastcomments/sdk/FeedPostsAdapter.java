package com.fastcomments.sdk;

import android.content.Context;
import android.text.Html;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
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
import java.util.List;
import java.util.Locale;

public class FeedPostsAdapter extends RecyclerView.Adapter<FeedPostsAdapter.FeedPostViewHolder> {

    private final Context context;
    private final List<FeedPost> feedPosts;
    private final OnFeedPostInteractionListener listener;
    private final FastCommentsFeedSDK sdk;
    private final boolean useAbsoluteDates;

    public interface OnFeedPostInteractionListener {
        void onCommentClick(FeedPost post);

        void onLikeClick(FeedPost post, int position);

        void onShareClick(FeedPost post);

        void onPostClick(FeedPost post);

        void onLinkClick(String url);

        void onMediaClick(FeedPostMediaItem mediaItem);
    }

    public FeedPostsAdapter(Context context, List<FeedPost> feedPosts, FastCommentsFeedSDK sdk, OnFeedPostInteractionListener listener) {
        this.context = context;
        this.feedPosts = feedPosts;
        this.listener = listener;
        this.sdk = sdk;
        // Set date format based on SDK configuration
        this.useAbsoluteDates = Boolean.TRUE.equals(sdk.getConfig().absoluteDates);
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

    public void updatePosts(List<FeedPost> newPosts) {
        this.feedPosts.clear();
        this.feedPosts.addAll(newPosts);
        notifyDataSetChanged();
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

    enum FeedPostType {
        TEXT_ONLY,
        SINGLE_IMAGE,
        MULTI_IMAGE,
        TASK
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

            // Set post click listener
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPostClick(feedPosts.get(position));
                }
            });
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

            // Handle like count display
            int likeCount = sdk.getPostLikeCount(post.getId());
            if (likeCount > 0) {
                likeCountTextView.setVisibility(View.VISIBLE);
                String likesText = likeCount == 1 ?
                        context.getString(R.string.like_count_singular, likeCount) :
                        context.getString(R.string.like_count_plural, likeCount);
                likeCountTextView.setText(likesText);
            } else {
                likeCountTextView.setVisibility(View.GONE);
            }

            // Handle like button state based on user's reactions from SDK
            boolean userHasLiked = sdk.hasUserReactedToPost(post.getId(), "l");
            if (userHasLiked) {
                likeButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.heart_filled, 0, 0, 0);
                likeButton.setTextColor(context.getResources().getColor(android.R.color.holo_red_light, null));
                likeButton.setText(R.string.liked);
            } else {
                likeButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.like_icon, 0, 0, 0);
                likeButton.setTextColor(likeButton.getContext().getResources().getColor(android.R.color.darker_gray, null));
                likeButton.setText(R.string.like);
            }

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

                        Glide.with(context)
                                .load(bestSizeAsset.getSrc())
                                .transition(DrawableTransitionOptions.withCrossFade())
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
                        mediaImageView.setOnClickListener(v -> {
                            // Get best quality image URL
                            String fullImageUrl = getBestQualityImageUrl(mediaItem);

                            if (fullImageUrl != null) {
                                // Show the full image dialog
                                new FullImageDialog(context, fullImageUrl).show();
                            }

                            // Also notify the listener if set
                            if (listener != null) {
                                listener.onMediaClick(mediaItem);
                            }
                        });

                        // Set click listener for media container as a backup
                        mediaContainer.setOnClickListener(v -> {
                            // Get best quality image URL
                            String fullImageUrl = getBestQualityImageUrl(mediaItem);

                            if (fullImageUrl != null) {
                                // Show the full image dialog
                                new FullImageDialog(context, fullImageUrl).show();
                            }

                            // Also notify the listener if set
                            if (listener != null) {
                                listener.onMediaClick(mediaItem);
                            }
                        });
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
                    setupThreeImagesLayout(mediaItems);

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
                    setupImageGrid(mediaItems);

                    // Make sure GridLayout is actually visible
                    imageGridLayout.invalidate();
                    mediaGalleryContainer.setVisibility(View.VISIBLE);
                } else {
                    // For 4+ images, use the ViewPager
                    imageGridLayout.setVisibility(View.GONE);
                    threeImagesLayout.setVisibility(View.GONE);
                    imageViewPager.setVisibility(View.VISIBLE);
                    imageCounterTextView.setVisibility(View.VISIBLE);

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
         * @param mediaItems The list of media items
         */
        private void setupImageGrid(List<FeedPostMediaItem> mediaItems) {
            int count = mediaItems.size();

            if (count == 1) {
                // Single image takes full size
                ImageView imageView = createImageView(mediaItems.get(0));
                GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                        GridLayout.spec(0, 2, 1f),
                        GridLayout.spec(0, 2, 1f));
                params.width = GridLayout.LayoutParams.MATCH_PARENT;
                params.height = GridLayout.LayoutParams.MATCH_PARENT;
                imageGridLayout.addView(imageView, params);
            } else if (count == 2) {
                // Two images side by side (only handles 1-2 images now)
                for (int i = 0; i < count; i++) {
                    ImageView imageView = createImageView(mediaItems.get(i));
                    GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                            GridLayout.spec(0, 2, 1f),
                            GridLayout.spec(i, 1, 1f));
                    params.width = 0;
                    params.height = GridLayout.LayoutParams.MATCH_PARENT;
                    params.setMargins(i > 0 ? 2 : 0, 0, i > 0 ? 0 : 2, 0);
                    imageGridLayout.addView(imageView, params);
                }
            }
        }

        /**
         * Set up the three images layout with the given media items
         * This is a dedicated method for handling exactly 3 images
         */
        private void setupThreeImagesLayout(List<FeedPostMediaItem> mediaItems) {
            // Load top image (first image)
            FeedPostMediaItem firstItem = mediaItems.get(0);
            loadImageIntoView(firstItem, topImageView);

            // Set click listener for full screen viewing
            topImageView.setOnClickListener(v -> {
                String fullImageUrl = getBestQualityImageUrl(firstItem);
                if (fullImageUrl != null) {
                    new FullImageDialog(context, fullImageUrl).show();
                }
                if (listener != null) {
                    listener.onMediaClick(firstItem);
                }
            });

            // Load bottom left image (second image)
            FeedPostMediaItem secondItem = mediaItems.get(1);
            loadImageIntoView(secondItem, bottomLeftImageView);

            // Set click listener for full screen viewing
            bottomLeftImageView.setOnClickListener(v -> {
                String fullImageUrl = getBestQualityImageUrl(secondItem);
                if (fullImageUrl != null) {
                    new FullImageDialog(context, fullImageUrl).show();
                }
                if (listener != null) {
                    listener.onMediaClick(secondItem);
                }
            });

            // Load bottom right image (third image)
            FeedPostMediaItem thirdItem = mediaItems.get(2);
            loadImageIntoView(thirdItem, bottomRightImageView);

            // Set click listener for full screen viewing
            bottomRightImageView.setOnClickListener(v -> {
                String fullImageUrl = getBestQualityImageUrl(thirdItem);
                if (fullImageUrl != null) {
                    new FullImageDialog(context, fullImageUrl).show();
                }
                if (listener != null) {
                    listener.onMediaClick(thirdItem);
                }
            });
        }

        /**
         * Helper method to load an image into an ImageView
         */
        private void loadImageIntoView(FeedPostMediaItem mediaItem, ImageView imageView) {
            if (mediaItem.getSizes() != null && !mediaItem.getSizes().isEmpty()) {
                FeedPostMediaItemAsset bestAsset = selectBestImageSize(mediaItem.getSizes());
                if (bestAsset != null && bestAsset.getSrc() != null) {
                    Glide.with(context)
                            .load(bestAsset.getSrc())
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .error(R.drawable.image_placeholder)
                            .into(imageView);
                } else {
                    imageView.setImageResource(R.drawable.image_placeholder);
                }
            } else {
                imageView.setImageResource(R.drawable.image_placeholder);
            }
        }

        /**
         * Create an ImageView for a media item
         *
         * @param mediaItem The media item to display
         * @return A configured ImageView
         */
        private ImageView createImageView(FeedPostMediaItem mediaItem) {
            ImageView imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setBackgroundColor(context.getResources().getColor(android.R.color.darker_gray, null));

            // Load image using Glide if media item has sizes
            if (!mediaItem.getSizes().isEmpty()) {
                FeedPostMediaItemAsset bestSizeAsset = selectBestImageSize(mediaItem.getSizes());

                if (bestSizeAsset != null) {
                    Glide.with(context)
                            .load(bestSizeAsset.getSrc())
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .error(R.drawable.image_placeholder)
                            .into(imageView);
                }
            }

            // Set click listener
            imageView.setOnClickListener(v -> {
                // Get best quality image URL
                String fullImageUrl = getBestQualityImageUrl(mediaItem);

                if (fullImageUrl != null) {
                    // Show the full image dialog
                    new FullImageDialog(context, fullImageUrl).show();
                }

                // Also notify the listener if set
                if (listener != null) {
                    listener.onMediaClick(mediaItem);
                }
            });

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
                        Glide.with(context)
                                .load(bestSizeAsset.getSrc())
                                .transition(DrawableTransitionOptions.withCrossFade())
                                .error(R.drawable.image_placeholder)
                                .into(mediaImageView);

                        // Make sure image and container are clickable
                        mediaContainer.setClickable(true);
                        mediaContainer.setFocusable(true);
                        mediaImageView.setClickable(true);
                        mediaImageView.setFocusable(true);

                        // Set click listeners for both container and image
                        View.OnClickListener imageClickListener = v -> {
                            // Get best quality image URL
                            String fullImageUrl = getBestQualityImageUrl(mediaItem);

                            if (fullImageUrl != null) {
                                // Show the full image dialog
                                new FullImageDialog(context, fullImageUrl).show();
                            }

                            // Also notify the listener
                            if (listener != null) {
                                listener.onMediaClick(mediaItem);
                            }
                        };

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
                        actionButton.setTextColor(context.getResources().getColor(android.R.color.black, null));
                        actionButton.setPadding(16, 12, 16, 12); // Increased vertical padding for taller buttons

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
                            actionButton.setTextColor(context.getResources().getColor(android.R.color.black, null));
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
                            actionButton.setTextColor(context.getResources().getColor(android.R.color.black, null));
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
                // No links, hide the link preview container
                linkPreviewContainer.setVisibility(View.GONE);
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
    }
}