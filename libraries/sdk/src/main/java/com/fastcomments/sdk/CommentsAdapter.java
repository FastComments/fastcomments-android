package com.fastcomments.sdk;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class CommentsAdapter extends RecyclerView.Adapter<CommentViewHolder> {

    private CommentsTree commentsTree;
    private Callback<RenderableComment> replyListener;

    public CommentsAdapter(CommentsTree commentsTree) {
        this.commentsTree = commentsTree;
        commentsTree.setAdapter(this);
    }

    public void setOnCommentReplyListener(Callback<RenderableComment> listener) {
        this.replyListener = listener;
    }

    @Override
    public int getItemCount() {
        return commentsTree.visibleSize();
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(commentsTree, view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        final RenderableComment comment = findCommentForPosition(position);

        holder.setComment(comment, comment1 -> {
            comment1.setRepliesShown(!comment1.isRepliesShown());
            notifyDataSetChanged();
        });

//        holder.replyButton.setOnClickListener(v -> {
//            if (replyListener != null) {
//                replyListener.call(comment);
//            }
//        });
    }

    // Helper method to determine which comment corresponds to a given adapter position
    private RenderableComment findCommentForPosition(int position) {
        int pos = position;
        for (RenderableComment comment : commentsTree.comments) {
            if (pos == 0) {
                return comment;
            }
            pos--;
        }
        throw new IndexOutOfBoundsException("Invalid position");
    }

    public interface OnToggleRepliesListener {
        void onToggle(RenderableComment comment);
    }
}