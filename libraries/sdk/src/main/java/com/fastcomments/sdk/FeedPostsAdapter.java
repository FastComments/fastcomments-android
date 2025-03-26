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
        private ImageView primaryImageView;
        private TextView imageCounterTextView;
        private ImageButton prevImageButton;
        private ImageButton nextImageButton;
        private int currentImageIndex = 0;
        private List<FeedPostMediaItem> mediaItems;
        
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
                    primaryImageView = itemView.findViewById(R.id.primaryImageView);
                    imageCounterTextView = itemView.findViewById(R.id.imageCounterTextView);
                    prevImageButton = itemView.findViewById(R.id.prevImageButton);
                    nextImageButton = itemView.findViewById(R.id.nextImageButton);
                    
                    // Set up navigation buttons
                    prevImageButton.setOnClickListener(v -> showPreviousImage());
                    nextImageButton.setOnClickListener(v -> showNextImage());
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
            // Set common elements
            String userName = post.getFromUserId() != null ? post.getFromUserId() : context.getString(R.string.anonymous);
            userNameTextView.setText(userName);
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
                
                if (mediaItem.getSizes() != null && mediaItem.getSizes().getSrc() != null) {
                    mediaContainer.setVisibility(View.VISIBLE);
                    
                    Glide.with(context)
                            .load(mediaItem.getSizes().getSrc())
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
        }
        
        private void bindMultiImagePost(FeedPost post) {
            if (post.getMedia() != null && !post.getMedia().isEmpty()) {
                mediaItems = post.getMedia();
                currentImageIndex = 0;
                
                // Show first image
                showImageAtIndex(0);
                
                // Update counter
                updateImageCounter();
                
                // Set navigation button states
                updateNavigationButtons();
                
                // Add click listener to the main image
                primaryImageView.setOnClickListener(v -> {
                    if (listener != null && currentImageIndex < mediaItems.size()) {
                        listener.onMediaClick(mediaItems.get(currentImageIndex));
                    }
                });
            }
        }
        
        /**
         * Show the image at the specified index in the multi-image carousel
         *
         * @param index The index of the image to display
         */
        private void showImageAtIndex(int index) {
            if (mediaItems == null || mediaItems.isEmpty() || index < 0 || index >= mediaItems.size()) {
                return;
            }
            
            FeedPostMediaItem mediaItem = mediaItems.get(index);
            
            // Determine direction for animation (left or right)
            boolean slideFromRight = index > currentImageIndex;
            currentImageIndex = index;
            
            if (mediaItem.getSizes() != null && mediaItem.getSizes().getSrc() != null) {
                // Apply fade animation
                primaryImageView.animate()
                        .alpha(0f)
                        .setDuration(150)
                        .withEndAction(() -> {
                            // Load the new image with error handling
                            Glide.with(context)
                                    .load(mediaItem.getSizes().getSrc())
                                    .transition(DrawableTransitionOptions.withCrossFade())
                                    .error(R.drawable.image_placeholder)
                                    .into(primaryImageView);
                            
                            // Animate the image back in
                            primaryImageView.animate()
                                    .alpha(1f)
                                    .setDuration(150)
                                    .start();
                        })
                        .start();
            }
            
            // Update counter and navigation buttons after changing the image
            updateImageCounter();
            updateNavigationButtons();
        }
        
        /**
         * Show the previous image in the carousel
         */
        private void showPreviousImage() {
            if (mediaItems == null || mediaItems.isEmpty() || currentImageIndex <= 0) {
                return;
            }
            
            showImageAtIndex(currentImageIndex - 1);
        }
        
        /**
         * Show the next image in the carousel
         */
        private void showNextImage() {
            if (mediaItems == null || mediaItems.isEmpty() || currentImageIndex >= mediaItems.size() - 1) {
                return;
            }
            
            showImageAtIndex(currentImageIndex + 1);
        }
        
        /**
         * Update the image counter text (e.g., "1/5")
         */
        private void updateImageCounter() {
            if (mediaItems == null || mediaItems.isEmpty() || imageCounterTextView == null) {
                return;
            }
            
            String counterText = String.format("%d/%d", currentImageIndex + 1, mediaItems.size());
            imageCounterTextView.setText(counterText);
        }
        
        /**
         * Update the visibility and enabled state of navigation buttons
         */
        private void updateNavigationButtons() {
            if (mediaItems == null || mediaItems.isEmpty()) {
                if (prevImageButton != null) prevImageButton.setVisibility(View.GONE);
                if (nextImageButton != null) nextImageButton.setVisibility(View.GONE);
                return;
            }
            
            // Only show navigation buttons if there are multiple images
            boolean hasMultipleImages = mediaItems.size() > 1;
            if (prevImageButton != null) {
                prevImageButton.setVisibility(hasMultipleImages ? View.VISIBLE : View.GONE);
                prevImageButton.setEnabled(currentImageIndex > 0);
                prevImageButton.setAlpha(currentImageIndex > 0 ? 1.0f : 0.5f);
            }
            
            if (nextImageButton != null) {
                nextImageButton.setVisibility(hasMultipleImages ? View.VISIBLE : View.GONE);
                nextImageButton.setEnabled(currentImageIndex < mediaItems.size() - 1);
                nextImageButton.setAlpha(currentImageIndex < mediaItems.size() - 1 ? 1.0f : 0.5f);
            }
        }
        
        private void bindTaskPost(FeedPost post) {
            // Handle optional media
            if (post.getMedia() != null && !post.getMedia().isEmpty()) {
                FeedPostMediaItem mediaItem = post.getMedia().get(0);
                if (mediaItem.getSizes() != null && mediaItem.getSizes().getSrc() != null) {
                    mediaContainer.setVisibility(View.VISIBLE);
                    Glide.with(context)
                            .load(mediaItem.getSizes().getSrc())
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .error(R.drawable.image_placeholder)
                            .into(mediaImageView);
                } else {
                    mediaContainer.setVisibility(View.GONE);
                }
            } else {
                mediaContainer.setVisibility(View.GONE);
            }
            
            // Create action buttons from links
            taskButtonsContainer.removeAllViews();
            
            if (post.getLinks() != null && !post.getLinks().isEmpty()) {
                for (FeedPostLink link : post.getLinks()) {
                    Button actionButton = new Button(context);
                    actionButton.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
                    
                    // Style as a filled button
                    actionButton.setBackgroundResource(android.R.color.holo_blue_light);
                    actionButton.setTextColor(context.getResources().getColor(android.R.color.white, null));
                    
                    // Set button text
                    String buttonText = link.getTitle();
                    if (buttonText == null || buttonText.isEmpty()) {
                        buttonText = link.getLink() != null ? 
                                context.getString(R.string.view_details) : 
                                context.getString(R.string.learn_more);
                    }
                    actionButton.setText(buttonText);
                    
                    // Set margin between buttons
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) actionButton.getLayoutParams();
                    params.setMargins(0, 0, 0, 16); // Add bottom margin
                    actionButton.setLayoutParams(params);
                    
                    // Set click listener
                    final String url = link.getLink();
                    actionButton.setOnClickListener(v -> {
                        if (listener != null && url != null) {
                            listener.onLinkClick(url);
                        }
                    });
                    
                    taskButtonsContainer.addView(actionButton);
                }
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
    }
}