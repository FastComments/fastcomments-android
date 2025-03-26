package com.fastcomments.sdk;

import android.content.Context;
import android.text.Html;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
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

    @NonNull
    @Override
    public FeedPostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.feed_post_item, parent, false);
        return new FeedPostViewHolder(view);
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
        private final TextView userNameTextView;
        private final TextView postTimeTextView;
        private final TextView contentTextView;
        private final ImageView avatarImageView;
        private final FrameLayout mediaContainer;
        private final ImageView mediaImageView;
        private final ImageView playButton;
        private final LinearLayout linksContainer;
        private final ChipGroup tagsChipGroup;
        private final Button commentButton;
        private final Button likeButton;
        private final Button shareButton;

        FeedPostViewHolder(@NonNull View itemView) {
            super(itemView);
            userNameTextView = itemView.findViewById(R.id.userNameTextView);
            postTimeTextView = itemView.findViewById(R.id.postTimeTextView);
            contentTextView = itemView.findViewById(R.id.contentTextView);
            avatarImageView = itemView.findViewById(R.id.avatarImageView);
            mediaContainer = itemView.findViewById(R.id.mediaContainer);
            mediaImageView = itemView.findViewById(R.id.mediaImageView);
            playButton = itemView.findViewById(R.id.playButton);
            linksContainer = itemView.findViewById(R.id.linksContainer);
            tagsChipGroup = itemView.findViewById(R.id.tagsChipGroup);
            commentButton = itemView.findViewById(R.id.commentButton);
            likeButton = itemView.findViewById(R.id.likeButton);
            shareButton = itemView.findViewById(R.id.shareButton);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPostClick(feedPosts.get(position));
                }
            });
        }

        void bind(FeedPost post, int position) {
            // Set user info
            String userName = post.getFromUserId() != null ? post.getFromUserId() : context.getString(R.string.anonymous);
            userNameTextView.setText(userName);

            // Set post time
            postTimeTextView.setText(formatTimestamp(post.getCreatedAt()));

            // Set content
            if (post.getContentHTML() != null) {
                contentTextView.setText(Html.fromHtml(post.getContentHTML(), Html.FROM_HTML_MODE_COMPACT));
                contentTextView.setVisibility(View.VISIBLE);
            } else {
                contentTextView.setVisibility(View.GONE);
            }

            // Set media content if available
            setupMediaContent(post);

            // Set links if available
            setupLinks(post);

            // Set tags if available
            setupTags(post);

            // Set button actions
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
        }

        private void setupMediaContent(FeedPost post) {
            if (post.getMedia() != null && !post.getMedia().isEmpty()) {
                FeedPostMediaItem mediaItem = post.getMedia().get(0); // Display the first media item
                mediaContainer.setVisibility(View.VISIBLE);

                if (mediaItem.getSizes() != null && mediaItem.getSizes().getSrc() != null) {
                    Glide.with(context)
                            .load(mediaItem.getSizes().getSrc())
                            .transition(DrawableTransitionOptions.withCrossFade())
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

        private void setupLinks(FeedPost post) {
            linksContainer.removeAllViews();
            
            if (post.getLinks() != null && !post.getLinks().isEmpty()) {
                linksContainer.setVisibility(View.VISIBLE);
                
                for (FeedPostLink link : post.getLinks()) {
                    View linkItemView = LayoutInflater.from(context).inflate(
                            R.layout.feed_post_link_item, linksContainer, false);
                    
                    TextView linkTitleView = linkItemView.findViewById(R.id.linkTitleTextView);
                    linkTitleView.setText(link.getTitle() != null ? link.getTitle() : link.getLink());
                    
                    final String url = link.getLink();
                    linkItemView.setOnClickListener(v -> {
                        if (listener != null && url != null) {
                            listener.onLinkClick(url);
                        }
                    });
                    
                    linksContainer.addView(linkItemView);
                }
            } else {
                linksContainer.setVisibility(View.GONE);
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