package com.fastcomments.sdk;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.Locale;

public class CommentViewHolder extends RecyclerView.ViewHolder {

    private final Context context;
    private final ImageView avatarImageView;
    private final TextView nameTextView;
    private final TextView dateTextView;
    private final TextView contentTextView;
    private final TextView unverifiedLabel;
    private final Button toggleRepliesButton;
    private final Button replyButton;
    private final ImageButton upVoteButton;
    private final ImageButton downVoteButton;
    private final TextView upVoteCountTextView;
    private final TextView downVoteCountTextView;
    private final CommentsTree commentsTree;
    
    // Child comments pagination
    private View childPaginationControls;
    private Button btnLoadMoreReplies;
    private ProgressBar childPaginationProgressBar;
    private RenderableComment currentComment;

    public CommentViewHolder(Context context, CommentsTree commentsTree, @NonNull View itemView) {
        super(itemView);
        this.commentsTree = commentsTree;
        this.context = context;
        
        // Basic comment view elements
        avatarImageView = itemView.findViewById(R.id.commentAvatar);
        nameTextView = itemView.findViewById(R.id.commentName);
        dateTextView = itemView.findViewById(R.id.commentDate);
        contentTextView = itemView.findViewById(R.id.commentContent);
        unverifiedLabel = itemView.findViewById(R.id.unverifiedLabel);
        toggleRepliesButton = itemView.findViewById(R.id.toggleReplies);
        replyButton = itemView.findViewById(R.id.replyButton);
        upVoteButton = itemView.findViewById(R.id.upVoteButton);
        downVoteButton = itemView.findViewById(R.id.downVoteButton);
        upVoteCountTextView = itemView.findViewById(R.id.upVoteCount);
        downVoteCountTextView = itemView.findViewById(R.id.downVoteCount);
        
        // Child pagination controls
        childPaginationControls = itemView.findViewById(R.id.childPaginationControls);
        btnLoadMoreReplies = itemView.findViewById(R.id.btnLoadMoreReplies);
        childPaginationProgressBar = itemView.findViewById(R.id.childPaginationProgressBar);
    }

    public void setComment(final RenderableComment comment, boolean disableUnverifiedLabel, final CommentsAdapter.OnToggleRepliesListener listener) {
        //noinspection ConstantValue
        if (comment.getComment().getCommenterName() != null) {
            nameTextView.setText(comment.getComment().getCommenterName());
        } else {
            nameTextView.setText(R.string.anonymous);
        }

        if (comment.getComment().getAvatarSrc() != null) {
            AvatarFetcher.fetchTransformInto(context, comment.getComment().getAvatarSrc(), avatarImageView);
        } else {
            AvatarFetcher.fetchTransformInto(context, R.drawable.default_avatar, avatarImageView);
        }
        
        // Handle unverified label
        Boolean isVerified = comment.getComment().getVerified();
        if (!disableUnverifiedLabel && (isVerified == null || !isVerified)) {
            unverifiedLabel.setVisibility(View.VISIBLE);
        } else {
            unverifiedLabel.setVisibility(View.GONE);
        }

        // Store current comment reference first, so updateDateDisplay has the correct reference
        this.currentComment = comment;
        
        // Format and display the date
        updateDateDisplay();

        ViewGroup.LayoutParams textViewLayout = contentTextView.getLayoutParams();
        textViewLayout.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        contentTextView.setLayoutParams(textViewLayout);
        
        // Make links clickable
        contentTextView.setClickable(true);
        contentTextView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        contentTextView.setLinksClickable(true);
        
        // Display the comment content with clickable links
        String htmlContent = comment.getComment().getCommentHTML();
        contentTextView.setText(HtmlLinkHandler.parseHtml(context, htmlContent, contentTextView));

        // Indent child comments to reflect hierarchy
        ViewGroup.MarginLayoutParams itemViewLayoutParams = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
        if (itemViewLayoutParams != null) {
            final int nestingLevel = comment.determineNestingLevel(commentsTree.commentsById);
            itemViewLayoutParams.leftMargin = nestingLevel > 0 ? nestingLevel * 30 : 0;
            itemView.setLayoutParams(itemViewLayoutParams);
        }
        
        // Display vote counts and set button states
        Integer upVotes = comment.getComment().getVotesUp();
        if (upVotes != null && upVotes > 0) {
            upVoteCountTextView.setText(String.valueOf(upVotes));
            upVoteCountTextView.setTypeface(null, android.graphics.Typeface.BOLD);
            upVoteCountTextView.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.fastcomments_vote_count_color));
        } else {
            upVoteCountTextView.setText(R.string.vote_count_zero);
            upVoteCountTextView.setTypeface(null, android.graphics.Typeface.NORMAL);
            upVoteCountTextView.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.fastcomments_vote_count_zero_color));
        }
        
        Integer downVotes = comment.getComment().getVotesDown();
        if (downVotes != null && downVotes > 0) {
            downVoteCountTextView.setText(String.valueOf(downVotes));
            downVoteCountTextView.setTypeface(null, android.graphics.Typeface.BOLD);
            downVoteCountTextView.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.fastcomments_vote_count_color));
        } else {
            downVoteCountTextView.setText(R.string.vote_count_zero);
            downVoteCountTextView.setTypeface(null, android.graphics.Typeface.NORMAL);
            downVoteCountTextView.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.fastcomments_vote_count_zero_color));
        }
        
        // Set button selected states based on user's votes
        Boolean isVotedUp = comment.getComment().getIsVotedUp();
        upVoteButton.setSelected(isVotedUp != null && isVotedUp);
        
        Boolean isVotedDown = comment.getComment().getIsVotedDown();
        downVoteButton.setSelected(isVotedDown != null && isVotedDown);

        // Show the toggle replies button only if there are replies
        final Integer childCount = comment.getComment().getChildCount();
        if (childCount != null && childCount > 0) {
            toggleRepliesButton.setVisibility(View.VISIBLE);
            if (comment.isRepliesShown) {
                toggleRepliesButton.setText(R.string.hide_replies);
                // Update pagination controls
                updateChildPaginationControls(comment);
            } else {
                toggleRepliesButton.setText(context.getString(R.string.show_replies, childCount));
                childPaginationControls.setVisibility(View.GONE);
            }
            toggleRepliesButton.setOnClickListener(v -> {
                if (listener != null) {
                    // Pass both the comment and the toggle button for UI updates
                    listener.onToggle(comment, toggleRepliesButton);
                }
            });
        } else {
            toggleRepliesButton.setVisibility(View.GONE);
            childPaginationControls.setVisibility(View.GONE);
        }
        
        // Store current comment reference
        this.currentComment = comment;
    }
    
    /**
     * Set the click listener for the reply button
     * @param clickListener The click listener to set
     */
    public void setReplyClickListener(View.OnClickListener clickListener) {
        replyButton.setOnClickListener(clickListener);
    }
    
    /**
     * Set the click listener for the up vote button
     * @param clickListener The click listener to set
     */
    public void setUpVoteClickListener(View.OnClickListener clickListener) {
        upVoteButton.setOnClickListener(clickListener);
    }
    
    /**
     * Set the click listener for the down vote button
     * @param clickListener The click listener to set
     */
    public void setDownVoteClickListener(View.OnClickListener clickListener) {
        downVoteButton.setOnClickListener(clickListener);
    }
    
    /**
     * Updates the visibility and text of the child pagination controls
     */
    private void updateChildPaginationControls(RenderableComment comment) {
        if (!comment.isRepliesShown) {
            childPaginationControls.setVisibility(View.GONE);
            return;
        }
        
        // Check if we need to show pagination controls
        if (comment.hasMoreChildren) {
            childPaginationControls.setVisibility(View.VISIBLE);
            int remainingCount = comment.getRemainingChildCount();
            
            if (comment.isLoadingChildren) {
                btnLoadMoreReplies.setVisibility(View.GONE);
                childPaginationProgressBar.setVisibility(View.VISIBLE);
            } else {
                btnLoadMoreReplies.setVisibility(View.VISIBLE);
                childPaginationProgressBar.setVisibility(View.GONE);
                btnLoadMoreReplies.setText(context.getString(R.string.next_comments, remainingCount));
            }
        } else {
            childPaginationControls.setVisibility(View.GONE);
        }
    }
    
    /**
     * Set click listener for the "Load More" button for child comments pagination
     */
    public void setLoadMoreChildrenClickListener(View.OnClickListener clickListener) {
        btnLoadMoreReplies.setOnClickListener(clickListener);
    }
    
    /**
     * Updates the date display based on the current comment and configuration
     */
    public void updateDateDisplay() {
        if (currentComment == null || currentComment.getComment() == null) {
            dateTextView.setText("");
            return;
        }
        
        OffsetDateTime date = currentComment.getComment().getDate();
        if (date == null) {
            dateTextView.setText("");
            return;
        }
        
        // Check if we should use absolute dates based on config
        boolean useAbsoluteDates = false;
        
        // Get config from the FastCommentsSDK instance in the CommentsTree
        if (commentsTree != null && commentsTree.getSdk() != null) {
            useAbsoluteDates = commentsTree.getSdk().getConfig().absoluteDates != null && 
                               commentsTree.getSdk().getConfig().absoluteDates;
        }
        
        if (useAbsoluteDates) {
            // Use system's locale-aware date formatting
            Locale currentLocale = context.getResources().getConfiguration().getLocales().get(0);
            DateTimeFormatter formatter = DateTimeFormatter
                    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .withLocale(currentLocale);
            
            dateTextView.setText(date.format(formatter));
        } else {
            // Format as relative date: 2 minutes ago, 1 hour ago, etc.
            CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                    date.toInstant().toEpochMilli(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
            );
            dateTextView.setText(relativeTime);
        }
    }
}
