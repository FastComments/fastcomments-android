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
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.fastcomments.core.VoteStyle;
import com.fastcomments.model.CommentUserBadgeInfo;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class CommentViewHolder extends RecyclerView.ViewHolder {

    private final Context context;
    private final ImageView avatarImageView;
    private final ImageView onlineIndicator; // Online status indicator
    private final TextView nameTextView;
    private final TextView dateTextView;
    private final TextView contentTextView;
    private final TextView unverifiedLabel;
    private final TextView displayLabelTextView; // Display label above username
    private final com.google.android.flexbox.FlexboxLayout badgesContainer; // Badges container
    private final Button toggleRepliesButton;
    private final Button replyButton;
    private final ImageButton upVoteButton;
    private final ImageButton downVoteButton;
    private final TextView upVoteCountTextView;
    private final TextView downVoteCountTextView;
    private final ImageView pinIcon;
    private final ImageButton heartButton; // Heart style vote button
    private final View standardVoteContainer; // Container for standard up/down vote buttons
    private final View heartVoteContainer; // Container for heart vote button
    private final TextView heartVoteCountTextView; // Count for heart votes
    private final ImageButton commentMenuButton; // Three-dot menu button
    private final FastCommentsSDK sdk;

    // Child comments pagination
    private View childPaginationControls;
    private Button btnLoadMoreReplies;
    private ProgressBar childPaginationProgressBar;
    private RenderableComment currentComment;

    public CommentViewHolder(Context context, FastCommentsSDK sdk, @NonNull View itemView) {
        super(itemView);
        this.sdk = sdk;
        this.context = context;

        // Basic comment view elements
        avatarImageView = itemView.findViewById(R.id.commentAvatar);
        onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
        nameTextView = itemView.findViewById(R.id.commentName);
        dateTextView = itemView.findViewById(R.id.commentDate);
        contentTextView = itemView.findViewById(R.id.commentContent);
        unverifiedLabel = itemView.findViewById(R.id.unverifiedLabel);
        displayLabelTextView = itemView.findViewById(R.id.displayLabel);
        badgesContainer = itemView.findViewById(R.id.badgesContainer);
        toggleRepliesButton = itemView.findViewById(R.id.toggleReplies);
        replyButton = itemView.findViewById(R.id.replyButton);
        upVoteButton = itemView.findViewById(R.id.upVoteButton);
        downVoteButton = itemView.findViewById(R.id.downVoteButton);
        upVoteCountTextView = itemView.findViewById(R.id.upVoteCount);
        downVoteCountTextView = itemView.findViewById(R.id.downVoteCount);
        pinIcon = itemView.findViewById(R.id.pinIcon);
        heartButton = itemView.findViewById(R.id.heartButton);
        commentMenuButton = itemView.findViewById(R.id.commentMenuButton);

        // Get references to vote containers
        standardVoteContainer = itemView.findViewById(R.id.standardVoteContainer);
        heartVoteContainer = itemView.findViewById(R.id.heartVoteContainer);
        heartVoteCountTextView = itemView.findViewById(R.id.heartVoteCount);

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

        // Handle display label if present
        String displayLabel = comment.getComment().getDisplayLabel();
        if (displayLabel != null && !displayLabel.isEmpty()) {
            displayLabelTextView.setText(displayLabel);
            displayLabelTextView.setVisibility(View.VISIBLE);
        } else {
            displayLabelTextView.setVisibility(View.GONE);
        }

        // Handle unverified label
        Boolean isVerified = comment.getComment().getVerified();
        if (!disableUnverifiedLabel && (isVerified == null || !isVerified)) {
            unverifiedLabel.setVisibility(View.VISIBLE);
        } else {
            unverifiedLabel.setVisibility(View.GONE);
        }

        // Handle pinned comment icon
        Boolean isPinned = comment.getComment().getIsPinned();
        pinIcon.setVisibility(isPinned != null && isPinned ? View.VISIBLE : View.GONE);
        
        // Handle online status indicator
        onlineIndicator.setVisibility(comment.isOnline ? View.VISIBLE : View.GONE);
        
        // Handle badges if present
        updateBadges(comment.getComment().getBadges());

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
            final int nestingLevel = comment.determineNestingLevel(sdk.commentsTree.commentsById);
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

        // Heart button state is based on upvote state (heart vote is equivalent to upvote)
        heartButton.setSelected(isVotedUp != null && isVotedUp);

        // Set heart vote count (same as upvote count) with abbreviation
        if (upVotes != null && upVotes > 0) {
            heartVoteCountTextView.setText(formatAbbreviatedCount(upVotes));
            heartVoteCountTextView.setTypeface(null, android.graphics.Typeface.BOLD);
            heartVoteCountTextView.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.fastcomments_vote_count_color));
        } else {
            heartVoteCountTextView.setText(R.string.vote_count_zero);
            heartVoteCountTextView.setTypeface(null, android.graphics.Typeface.NORMAL);
            heartVoteCountTextView.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.fastcomments_vote_count_zero_color));
        }

        // Show the appropriate vote style based on configuration
        boolean useHeartStyle = Objects.equals(sdk.getConfig().voteStyle, VoteStyle.Heart);

        standardVoteContainer.setVisibility(useHeartStyle ? View.GONE : View.VISIBLE);
        heartVoteContainer.setVisibility(useHeartStyle ? View.VISIBLE : View.GONE);

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
     *
     * @param clickListener The click listener to set
     */
    public void setReplyClickListener(View.OnClickListener clickListener) {
        replyButton.setOnClickListener(clickListener);
    }

    /**
     * Set the click listener for the up vote button
     *
     * @param clickListener The click listener to set
     */
    public void setUpVoteClickListener(View.OnClickListener clickListener) {
        upVoteButton.setOnClickListener(clickListener);
    }

    /**
     * Set the click listener for the down vote button
     *
     * @param clickListener The click listener to set
     */
    public void setDownVoteClickListener(View.OnClickListener clickListener) {
        downVoteButton.setOnClickListener(clickListener);
    }

    /**
     * Set the click listener for the heart vote button
     *
     * @param clickListener The click listener to set
     */
    public void setHeartClickListener(View.OnClickListener clickListener) {
        heartButton.setOnClickListener(clickListener);
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
        final boolean useAbsoluteDates = Boolean.TRUE.equals(sdk.getConfig().absoluteDates);

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

        // Also update vote buttons and counts if necessary
        // This is needed for the heart button to stay in sync when vote counts update
        Boolean isVotedUp = currentComment.getComment().getIsVotedUp();
        heartButton.setSelected(isVotedUp != null && isVotedUp);
        
        // Update heart count with abbreviated format
        Integer upVotes = currentComment.getComment().getVotesUp();
        if (upVotes != null && upVotes > 0) {
            heartVoteCountTextView.setText(formatAbbreviatedCount(upVotes));
        }

        // Show the appropriate vote style based on configuration
        boolean useHeartStyle = Objects.equals(sdk.getConfig().voteStyle, VoteStyle.Heart);

        standardVoteContainer.setVisibility(useHeartStyle ? View.GONE : View.VISIBLE);
        heartVoteContainer.setVisibility(useHeartStyle ? View.VISIBLE : View.GONE);
    }
    
    /**
     * Update badges display for a comment
     *
     * @param badges List of badge information
     */
    public void updateBadges(List<CommentUserBadgeInfo> badges) {
        badgesContainer.removeAllViews();
        
        if (badges == null || badges.isEmpty()) {
            badgesContainer.setVisibility(View.GONE);
            return;
        }
        
        badgesContainer.setVisibility(View.VISIBLE);
        
        for (com.fastcomments.model.CommentUserBadgeInfo badge : badges) {
            View badgeView = BadgeView.createBadgeView(context, badge);
            badgesContainer.addView(badgeView);
        }
    }
    
    /**
     * Format a count number to an abbreviated format
     * Examples:
     * - 0-999: Shows as is (123)
     * - 1,000-999,999: Shows as #K (1.2K, 45K, 999K)
     * - 1,000,000+: Shows as #M (1.2M, 45M)
     * 
     * @param count The count to format
     * @return Formatted string
     */
    private String formatAbbreviatedCount(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        } else if (count < 10000) {
            // For 1,000-9,999, show with one decimal (1.2K)
            float thousands = count / 1000f;
            return String.format(Locale.getDefault(), "%.1fK", thousands);
        } else if (count < 1000000) {
            // For 10,000-999,999, show without decimal (45K)
            int thousands = Math.round(count / 1000f);
            return thousands + "K";
        } else {
            // For 1,000,000+, show in millions
            float millions = count / 1000000f;
            if (millions < 10) {
                // For 1M-9.9M, show with one decimal
                return String.format(Locale.getDefault(), "%.1fM", millions);
            } else {
                // For 10M+, show without decimal
                int roundedMillions = Math.round(millions);
                return roundedMillions + "M";
            }
        }
    }
    
    /**
     * Set the click listener for the comment menu button
     * 
     * @param commentMenuListener The listener to handle menu items
     */
    public void setCommentMenuClickListener(final CommentsAdapter.OnCommentMenuItemListener commentMenuListener) {
        commentMenuButton.setOnClickListener(v -> showCommentMenu(commentMenuListener));
    }
    
    /**
     * Show the comment menu
     * 
     * @param commentMenuListener The listener to handle menu items
     */
    private void showCommentMenu(final CommentsAdapter.OnCommentMenuItemListener commentMenuListener) {
        // Create popup menu
        PopupMenu popupMenu = new PopupMenu(context, commentMenuButton);
        popupMenu.inflate(R.menu.comment_menu);
        
        // Get menu for adjustments
        android.view.Menu menu = popupMenu.getMenu();
        
        // Only show edit option for user's own comments
        boolean isCurrentUserComment = false;
        
        if (currentComment != null && sdk.getCurrentUser() != null) {
            String commentUserId = currentComment.getComment().getUserId();
            String currentUserId = sdk.getCurrentUser().getId();
            
            if (commentUserId != null && currentUserId != null) {
                isCurrentUserComment = commentUserId.equals(currentUserId);
            }
        }
        
        // Show/hide edit option based on ownership
        menu.findItem(R.id.menu_edit_comment).setVisible(isCurrentUserComment);
        
        // Set item click listener
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            
            if (itemId == R.id.menu_edit_comment) {
                // Handle edit comment
                if (currentComment != null && commentMenuListener != null) {
                    String commentId = currentComment.getComment().getId();
                    String commentText = currentComment.getComment().getCommentHTML();
                    commentMenuListener.onEdit(commentId, commentText);
                    return true;
                }
            } else if (itemId == R.id.menu_flag_comment) {
                // Handle flag comment
                if (currentComment != null && commentMenuListener != null) {
                    String commentId = currentComment.getComment().getId();
                    commentMenuListener.onFlag(commentId);
                    return true;
                }
            } else if (itemId == R.id.menu_block_user) {
                // Handle block user
                if (currentComment != null && commentMenuListener != null) {
                    String commentId = currentComment.getComment().getId();
                    String userName = currentComment.getComment().getCommenterName();
                    commentMenuListener.onBlock(commentId, userName);
                    return true;
                }
            }
            
            return false;
        });
        
        // Show the popup menu
        popupMenu.show();
    }
}
