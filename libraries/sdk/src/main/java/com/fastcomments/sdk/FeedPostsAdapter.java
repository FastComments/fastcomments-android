package com.fastcomments.sdk;

import android.content.Context;
import android.text.Html;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
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
        // Check if this is a task post with action links
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

    class FeedPostViewHolder extends RecyclerView.ViewHolder {
        private final FeedPostType postType;
        
        // Common elements in all layouts
        private final TextView userNameTextView;
        private final TextView postTimeTextView;
        private final TextView contentTextView;
        private final ImageView avatarImageView;
        private final ChipGroup tagsChipGroup;
        private final Button commentButton;
        private final Button likeButton;
        private final Button shareButton;
        
        // Single image layout elements
        private FrameLayout mediaContainer;
        private ImageView mediaImageView;
        private ImageView playButton;
        
        // Multi-image layout elements
        private FrameLayout mediaGalleryContainer;
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
                    imageViewPager = itemView.findViewById(R.id.imageViewPager);
                    imageCounterTextView = itemView.findViewById(R.id.imageCounterTextView);
                    break;
                    
                case TASK:
                    taskButtonsContainer = itemView.findViewById(R.id.taskButtonsContainer);
                    mediaContainer = itemView.findViewById(R.id.mediaContainer);
                    mediaImageView = itemView.findViewById(R.id.mediaImageView);
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
            if (post.getFromUserId() != null) {
                String userName = post.getFromUserId();
                userNameTextView.setText(userName);
                userNameTextView.setVisibility(View.VISIBLE);
                
                // If there's an avatar URL, load it - otherwise show a default avatar
                if (avatarImageView != null) {
                    if (post.getFromUserAvatar() != null && !post.getFromUserAvatar().isEmpty()) {
                        avatarImageView.setVisibility(View.VISIBLE);
                        // Use properly rounded avatar with border
                        Glide.with(context)
                                .load(post.getFromUserAvatar())
                                .circleCrop()
                                .error(R.drawable.default_avatar)
                                .into(avatarImageView);
                    } else {
                        // Show default avatar image
                        avatarImageView.setVisibility(View.VISIBLE);
                        avatarImageView.setImageResource(R.drawable.default_avatar);
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
                        boolean isVideo = mediaItem.getLink() != null && 
                                         (mediaItem.getLink().contains("youtube") || 
                                          mediaItem.getLink().contains("vimeo") ||
                                          mediaItem.getLink().contains(".mp4"));
                        
                        playButton.setVisibility(isVideo ? View.VISIBLE : View.GONE);
                        
                        // Set click listener for media
                        mediaContainer.setOnClickListener(v -> {
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
        
        private void bindMultiImagePost(FeedPost post) {
            if (post.getMedia() != null && !post.getMedia().isEmpty()) {
                mediaItems = post.getMedia();
                
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
                    } else {
                        mediaContainer.setVisibility(View.GONE);
                    }
                } else {
                    mediaContainer.setVisibility(View.GONE);
                }
            } else {
                mediaContainer.setVisibility(View.GONE);
            }
            
            // Create action buttons from links
            taskButtonsContainer.removeAllViews();
            
            if (post.getLinks() != null && !post.getLinks().isEmpty()) {
                // Create a horizontal layout for the buttons
                LinearLayout buttonRow = new LinearLayout(context);
                buttonRow.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                buttonRow.setOrientation(LinearLayout.HORIZONTAL);
                
                // Set equal distribution of buttons
                int buttonWeight = 1;
                int buttonCount = post.getLinks().size();
                
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
                    
                    // Style as a filled button
                    actionButton.setBackgroundResource(android.R.color.holo_blue_light);
                    actionButton.setTextColor(context.getResources().getColor(android.R.color.white, null));
                    
                    // Set button text (keeping text short for horizontal layout)
                    String buttonText = link.getTitle();
                    if (buttonText == null || buttonText.isEmpty()) {
                        buttonText = link.getLink() != null ? 
                                context.getString(R.string.view_details) : 
                                context.getString(R.string.learn_more);
                    }
                    actionButton.setText(buttonText);
                    actionButton.setTextSize(12); // Smaller text size to fit better
                    actionButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    actionButton.setSingleLine(true);
                    
                    // Set click listener
                    final String url = link.getLink();
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