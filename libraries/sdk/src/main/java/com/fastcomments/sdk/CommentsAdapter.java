package com.fastcomments.sdk;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentViewHolder> {

    private final List<RenderableComment> comments = new ArrayList<>();
    private Callback<RenderableComment> replyListener;

    public void setOnCommentReplyListener(Callback<RenderableComment> listener) {
        this.replyListener = listener;
    }

    public void setComments(List<RenderableComment> newComments) {
        comments.clear();
        comments.addAll(newComments);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        int count = 0;
        for (RenderableComment comment : comments) {
            count++; // Count parent comment
            if (comment.isExpanded()) {
                count += comment.getChildCount();
            }
        }
        return count;
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Pair<RenderableComment, Boolean> commentPair = findCommentForPosition(position);
        final RenderableComment comment = commentPair.first;
        boolean isChild = commentPair.second;

        holder.bind(comment, isChild, new OnToggleRepliesListener() {
            @Override
            public void onToggle(RenderableComment comment) {
                comment.setExpanded(!comment.isExpanded());
                notifyDataSetChanged();
            }
        });
        
        // Set up reply click listener
        holder.replyButton.setOnClickListener(v -> {
            if (replyListener != null) {
                replyListener.onReply(comment);
            }
        });
    }

    // Helper method to determine which comment corresponds to a given adapter position
    private Pair<RenderableComment, Boolean> findCommentForPosition(int position) {
        int pos = position;
        for (RenderableComment comment : comments) {
            if (pos == 0) {
                return new Pair<>(comment, false);
            }
            pos--;
            if (expandedComments.contains(comment.getId()) && comment.getReplies() != null) {
                int replyCount = comment.getReplies().size();
                if (pos < replyCount) {
                    return new Pair<>(comment.getReplies().get(pos), true);
                } else {
                    pos -= replyCount;
                }
            }
        }
        throw new IndexOutOfBoundsException("Invalid position");
    }

    public interface OnToggleRepliesListener {
        void onToggle(RenderableComment comment);
    }

    public static class CommentViewHolder extends RecyclerView.ViewHolder {
        private final ImageView avatarImageView;
        private final TextView nameTextView;
        private final TextView dateTextView;
        private final TextView contentTextView;
        private final Button toggleRepliesButton;
        private final Button replyButton;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImageView = itemView.findViewById(R.id.commentAvatar);
            nameTextView = itemView.findViewById(R.id.commentName);
            dateTextView = itemView.findViewById(R.id.commentDate);
            contentTextView = itemView.findViewById(R.id.commentContent);
            toggleRepliesButton = itemView.findViewById(R.id.toggleReplies);
            replyButton = itemView.findViewById(R.id.replyButton);
        }

        public void bind(final RenderableComment comment, boolean isChild, final OnToggleRepliesListener listener) {
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
            contentTextView.setText(comment.getComment().getCommentHTML());

            // Indent child comments to reflect hierarchy
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            if (params != null) {
                params.leftMargin = isChild ? 50 : 0;
                itemView.setLayoutParams(params);
            }

            // Show the toggle replies button only if there are replies
            final Integer childCount = comment.getComment().getChildCount();
            if (childCount != null && childCount > 0) {
                toggleRepliesButton.setVisibility(View.VISIBLE);
                toggleRepliesButton.setText(comment.isExpanded() ? "Hide Replies" : "Show Replies (" + childCount + ")");
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

    // Simple Pair implementation
    public static class Pair<F, S> {
        public final F first;
        public final S second;
        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }
    }
}