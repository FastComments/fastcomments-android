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
    private final CommentsTree commentsTree;

    public CommentViewHolder(Context context, CommentsTree commentsTree, @NonNull View itemView) {
        super(itemView);
        avatarImageView = itemView.findViewById(R.id.commentAvatar);
        nameTextView = itemView.findViewById(R.id.commentName);
        dateTextView = itemView.findViewById(R.id.commentDate);
        contentTextView = itemView.findViewById(R.id.commentContent);
        toggleRepliesButton = itemView.findViewById(R.id.toggleReplies);
        replyButton = itemView.findViewById(R.id.replyButton);
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
            Glide.with(context).load(comment.getComment().getAvatarSrc())
                    .apply(RequestOptions.circleCropTransform())
                    .into(avatarImageView);
        } else {
            Glide.with(context).load(R.drawable.default_avatar)
                    .apply(RequestOptions.circleCropTransform())
                    .into(avatarImageView);
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

        // Display the comment content
        contentTextView.setText(Html.fromHtml(comment.getComment().getCommentHTML(), Html.FROM_HTML_MODE_LEGACY, new Html.ImageGetter() {
            @Override
            public Drawable getDrawable(String source) {
//                System.out.println("Getting image " + source);
                final URLDrawable urlDrawable = new URLDrawable();
                urlDrawable.setBounds(0, 0, contentTextView.getWidth(), 100);

                // Use Glide to asynchronously load the image from the URL.
                Glide.with(context)
                        .asBitmap()
                        .load(source)
                        .into(new CustomTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                                // Create a BitmapDrawable from the loaded Bitmap
                                BitmapDrawable drawable = new BitmapDrawable(context.getResources(), resource);
                                drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                                ViewGroup.LayoutParams textViewLayout = contentTextView.getLayoutParams();
                                textViewLayout.height = drawable.getIntrinsicHeight();
                                contentTextView.setLayoutParams(textViewLayout);
                                urlDrawable.setDrawable(drawable);

                                // Refresh the TextView so that the new image is displayed
                                contentTextView.setText(contentTextView.getText());
                                // these do not seem to be required, so we can omit them for performance!
//                                contentTextView.requestLayout();
//                                contentTextView.invalidate();
                            }

                            @Override
                            public void onLoadCleared(@Nullable Drawable placeholder) {
                                // Handle cleanup if needed
                            }
                        });

                // Return the URLDrawable (which will be updated asynchronously)
                return urlDrawable;
            }
        }, null));

        // Indent child comments to reflect hierarchy
        ViewGroup.MarginLayoutParams itemViewLayoutParams = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
        if (itemViewLayoutParams != null) {
            final int nestingLevel = comment.determineNestingLevel(commentsTree.commentsById);
            itemViewLayoutParams.leftMargin = nestingLevel > 0 ? nestingLevel * 30 : 0;
            itemView.setLayoutParams(itemViewLayoutParams);
        }

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
}
