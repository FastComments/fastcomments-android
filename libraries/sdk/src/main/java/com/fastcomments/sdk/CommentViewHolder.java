package com.fastcomments.sdk;

import android.text.Html;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.time.OffsetDateTime;

public class CommentViewHolder extends RecyclerView.ViewHolder {

    private final ImageView avatarImageView;
    private final TextView nameTextView;
    private final TextView dateTextView;
    private final TextView contentTextView;
    private final Button toggleRepliesButton;
    private final Button replyButton;
    private final CommentsTree commentsTree;

    public CommentViewHolder(CommentsTree commentsTree, @NonNull View itemView) {
        super(itemView);
        avatarImageView = itemView.findViewById(R.id.commentAvatar);
        nameTextView = itemView.findViewById(R.id.commentName);
        dateTextView = itemView.findViewById(R.id.commentDate);
        contentTextView = itemView.findViewById(R.id.commentContent);
        toggleRepliesButton = itemView.findViewById(R.id.toggleReplies);
        replyButton = itemView.findViewById(R.id.replyButton);
        this.commentsTree = commentsTree;
    }

    public void setComment(final RenderableComment comment, final CommentsAdapter.OnToggleRepliesListener listener) {
        //noinspection ConstantValue
        if (comment.getComment().getCommenterName() != null) {
            nameTextView.setText(comment.getComment().getCommenterName());
            // You would use an image loading library like Glide or Picasso here
            // For now, just use a placeholder
            avatarImageView.setImageResource(R.drawable.default_avatar);
        } else {
            nameTextView.setText(R.string.anonymous);
            avatarImageView.setImageResource(R.drawable.default_avatar);
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

        // Display the comment content
        contentTextView.setText(Html.fromHtml(comment.getComment().getCommentHTML(), Html.FROM_HTML_MODE_LEGACY));

        // Indent child comments to reflect hierarchy
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
        if (params != null) {
            params.leftMargin = comment.determineNestingLevel(commentsTree.commentsById);
            itemView.setLayoutParams(params);
        }

        // Show the toggle replies button only if there are replies
        final Integer childCount = comment.getComment().getChildCount();
        if (childCount != null && childCount > 0) {
            toggleRepliesButton.setVisibility(View.VISIBLE);
            toggleRepliesButton.setText(comment.isRepliesShown() ? "Hide Replies" : "Show Replies (" + childCount + ")");
            toggleRepliesButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onToggle(comment);
                }
            });
        } else {
            toggleRepliesButton.setVisibility(View.GONE);
        }
    }
}
