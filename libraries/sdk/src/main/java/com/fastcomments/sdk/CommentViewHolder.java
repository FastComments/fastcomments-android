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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.time.OffsetDateTime;

public class CommentViewHolder extends RecyclerView.ViewHolder {

    private final Context context;
    private final ImageView avatarImageView;
    private final TextView nameTextView;
    private final TextView dateTextView;
    private final TextView contentTextView;
    private final Button toggleRepliesButton;
    private final Button replyButton;
    private final ImageButton upVoteButton;
    private final ImageButton downVoteButton;
    private final TextView upVoteCountTextView;
    private final TextView downVoteCountTextView;
    private final CommentsTree commentsTree;

    public CommentViewHolder(Context context, CommentsTree commentsTree, @NonNull View itemView) {
        super(itemView);
        avatarImageView = itemView.findViewById(R.id.commentAvatar);
        nameTextView = itemView.findViewById(R.id.commentName);
        dateTextView = itemView.findViewById(R.id.commentDate);
        contentTextView = itemView.findViewById(R.id.commentContent);
        toggleRepliesButton = itemView.findViewById(R.id.toggleReplies);
        replyButton = itemView.findViewById(R.id.replyButton);
        upVoteButton = itemView.findViewById(R.id.upVoteButton);
        downVoteButton = itemView.findViewById(R.id.downVoteButton);
        upVoteCountTextView = itemView.findViewById(R.id.upVoteCount);
        downVoteCountTextView = itemView.findViewById(R.id.downVoteCount);
        this.commentsTree = commentsTree;
        this.context = context;
    }

    public void setComment(final RenderableComment comment, final CommentsAdapter.OnToggleRepliesListener listener) {
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

        // Format and display the date
        OffsetDateTime date = comment.getComment().getDate();
        if (date != null) {
            CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                    date.toInstant().toEpochMilli(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
            );
            dateTextView.setText(relativeTime);
        } else {
            dateTextView.setText("");
        }

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
            if (comment.isRepliesShown()) {
                toggleRepliesButton.setText(R.string.hide_replies);
            } else {
                toggleRepliesButton.setText(context.getString(R.string.show_replies, childCount));
            }
            toggleRepliesButton.setOnClickListener(v -> {
                if (listener != null) {
                    // Pass both the comment and the toggle button for UI updates
                    listener.onToggle(comment, toggleRepliesButton);
                }
            });
        } else {
            toggleRepliesButton.setVisibility(View.GONE);
        }
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
}
