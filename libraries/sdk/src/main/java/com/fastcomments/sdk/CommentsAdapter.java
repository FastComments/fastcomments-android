package com.fastcomments.sdk;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class CommentsAdapter extends RecyclerView.Adapter<CommentViewHolder> {

    private final Context context;
    private final CommentsTree commentsTree;
    private Callback<RenderableComment> replyListener;

    public CommentsAdapter(Context context, CommentsTree commentsTree) {
        this.context = context;
        this.commentsTree = commentsTree;
        commentsTree.setAdapter(this);
    }

    public void setOnCommentReplyListener(Callback<RenderableComment> listener) {
        this.replyListener = listener;
    }

    @Override
    public int getItemCount() {
        return commentsTree.totalSize();
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(context, commentsTree, view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        final RenderableComment comment = findCommentForPosition(position);

        if (comment == null) {
            return;
        }
        holder.setComment(comment, updatedComment -> {
            commentsTree.setRepliesVisible(updatedComment, !updatedComment.isRepliesShown());
        });

//        holder.replyButton.setOnClickListener(v -> {
//            if (replyListener != null) {
//                replyListener.call(comment);
//            }
//        });
    }

    // Helper method to determine which comment corresponds to a given adapter position
    private RenderableComment findCommentForPosition(int position) {
        if (position < 0 || position >= commentsTree.visibleComments.size()) {
            return null;
        }
        return commentsTree.visibleComments.get(position);
    }

    public interface OnToggleRepliesListener {
        void onToggle(RenderableComment comment);
    }
}