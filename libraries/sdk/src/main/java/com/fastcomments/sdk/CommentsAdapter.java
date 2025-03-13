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

import com.fastcomments.client.R;
import com.fastcomments.model.APICommentPublicComment;
import com.fastcomments.model.CommentAuthor;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentViewHolder> {

    private List<APICommentPublicComment> comments = new ArrayList<>();
    private Set<String> expandedComments = new HashSet<>();
    private OnCommentReplyListener replyListener;

    public interface OnCommentReplyListener {
        void onReply(APICommentPublicComment parentComment);
    }

    public void setOnCommentReplyListener(OnCommentReplyListener listener) {
        this.replyListener = listener;
    }

    public void setComments(List<APICommentPublicComment> newComments) {
        comments.clear();
        comments.addAll(newComments);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        int count = 0;
        for (APICommentPublicComment comment : comments) {
            count++; // Count parent comment
            if (expandedComments.contains(comment.getId()) && comment.getReplies() != null) {
                count += comment.getReplies().size(); // Count visible child replies
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
        Pair<APICommentPublicComment, Boolean> commentPair = findCommentForPosition(position);
        final APICommentPublicComment comment = commentPair.first;
        boolean isChild = commentPair.second;
        boolean isExpanded = expandedComments.contains(comment.getParentId());

        holder.bind(comment, isChild, isExpanded, new OnToggleRepliesListener() {
            @Override
            public void onToggle(APICommentPublicComment comment) {
                if (expandedComments.contains(comment.getId())) {
                    expandedComments.remove(comment.getId());
                } else {
                    expandedComments.add(comment.getId());
                }
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
    private Pair<APICommentPublicComment, Boolean> findCommentForPosition(int position) {
        int pos = position;
        for (APICommentPublicComment comment : comments) {
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
        void onToggle(APICommentPublicComment comment);
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

        public void bind(final APICommentPublicComment comment, boolean isChild, boolean isExpanded, 
                        final OnToggleRepliesListener listener) {
            // Display author information
            CommentAuthor author = comment.getAuthor();
            if (author != null) {
                nameTextView.setText(author.getName());
                // You would use an image loading library like Glide or Picasso here
                // For now, just use a placeholder
                avatarImageView.setImageResource(R.drawable.default_avatar);
            } else {
                nameTextView.setText(R.string.anonymous);
                avatarImageView.setImageResource(R.drawable.default_avatar);
            }
            
            // Format and display the date
            Date date = comment.getDate();
            if (date != null) {
                CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                    date.getTime(), 
                    System.currentTimeMillis(), 
                    DateUtils.MINUTE_IN_MILLIS
                );
                dateTextView.setText(relativeTime);
            } else {
                dateTextView.setText("");
            }
            
            // Display the comment content
            contentTextView.setText(comment.getHtml());

            // Indent child comments to reflect hierarchy
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            if (params != null) {
                params.leftMargin = isChild ? 50 : 0;
                itemView.setLayoutParams(params);
            }

            // Show the toggle replies button only if there are replies
            List<APICommentPublicComment> replies = comment.getReplies();
            if (replies != null && !replies.isEmpty()) {
                toggleRepliesButton.setVisibility(View.VISIBLE);
                toggleRepliesButton.setText(isExpanded ? "Hide Replies" : "Show Replies (" + replies.size() + ")");
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