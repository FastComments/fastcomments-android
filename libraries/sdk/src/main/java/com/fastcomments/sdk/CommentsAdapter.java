package com.fastcomments.sdk;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

import com.fastcomments.model.APICommentPublicComment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentViewHolder> {

    private List<APICommentPublicComment> comments = new ArrayList<>();
    private Set<String> expandedComments = new HashSet<>();

    public void setComments(List<APICommentPublicComment> newComments) {
        comments.clear();
        comments.addAll(newComments);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        int count = 0;
        for (APICommentPublicComment comment : comments) {
            count++; // Count parent comment.
            if (expandedComments.contains(comment.getId())) {
                count += comment.getReplies().size(); // Count visible child replies.
            }
        }
        return count;
    }

    @Override
    public CommentViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(CommentViewHolder holder, int position) {
        Pair<APICommentPublicComment, Boolean> commentPair = findCommentForPosition(position);
        final APICommentPublicComment comment = commentPair.first;
        boolean isChild = commentPair.second;
        boolean isExpanded = expandedComments.contains(comment.getId());

        holder.bind(comment, isChild, isExpanded, new OnToggleRepliesListener() {
            @Override
            public void onToggle() {
                if (expandedComments.contains(comment.getId())) {
                    expandedComments.remove(comment.getId());
                } else {
                    expandedComments.add(comment.getId());
                }
                notifyDataSetChanged();
            }
        });
    }

    // Helper method to determine which comment corresponds to a given adapter position.
    private Pair<APICommentPublicComment, Boolean> findCommentForPosition(int position) {
        int pos = position;
        for (APICommentPublicComment comment : comments) {
            if (pos == 0) {
                return new Pair<>(comment, false);
            }
            pos--;
            if (expandedComments.contains(comment.getId())) {
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

    public static class CommentViewHolder extends RecyclerView.ViewHolder {
        private ImageView avatarImageView;
        private TextView nameTextView;
        private TextView contentTextView;
        private Button toggleRepliesButton;

        public CommentViewHolder(View itemView) {
            super(itemView);
            avatarImageView = itemView.findViewById(R.id.commentAvatar);
            nameTextView = itemView.findViewById(R.id.commentName);
            contentTextView = itemView.findViewById(R.id.commentContent);
            toggleRepliesButton = itemView.findViewById(R.id.toggleReplies);
        }

        public void bind(final APICommentPublicComment comment, boolean isChild, boolean isExpanded, final OnToggleRepliesListener listener) {
            // Load the avatar image using an image loader such as Glide or Picasso.
            nameTextView.setText(comment.getAuthor().getName());
            contentTextView.setText(comment.getContent());

            // Indent child comments to reflect hierarchy.
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            params.leftMargin = isChild ? 50 : 0;
            itemView.setLayoutParams(params);

            // Show the toggle button only if there are replies.
            if (comment.getReplies() != null && !comment.getReplies().isEmpty()) {
                toggleRepliesButton.setVisibility(View.VISIBLE);
                toggleRepliesButton.setText(isExpanded ? "Collapse" : "Expand");
                toggleRepliesButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onToggle();
                    }
                });
            } else {
                toggleRepliesButton.setVisibility(View.GONE);
            }
        }
    }

    public interface OnToggleRepliesListener {
        void onToggle();
    }

    // Simple Pair implementation.
    public static class Pair<F, S> {
        public final F first;
        public final S second;
        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }
    }
}
