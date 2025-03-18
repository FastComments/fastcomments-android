package com.fastcomments.sdk;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fastcomments.model.PublicComment;

import java.util.List;

public class CommentsAdapter extends RecyclerView.Adapter<CommentViewHolder> {

    private final Context context;
    private final CommentsTree commentsTree;
    private final FastCommentsSDK sdk;
    private Callback<RenderableComment> replyListener;
    private Callback<RenderableComment> upVoteListener;
    private Callback<RenderableComment> downVoteListener;
    private Producer<GetChildrenRequest, List<PublicComment>> getChildren;

    public CommentsAdapter(Context context, FastCommentsSDK sdk) {
        this.context = context;
        this.commentsTree = sdk.commentsTree;
        this.sdk = sdk;
        commentsTree.setAdapter(this);
    }

    public void setRequestingReplyListener(Callback<RenderableComment> listener) {
        this.replyListener = listener;
    }
    
    public void setUpVoteListener(Callback<RenderableComment> listener) {
        this.upVoteListener = listener;
    }
    
    public void setDownVoteListener(Callback<RenderableComment> listener) {
        this.downVoteListener = listener;
    }

    public void setGetChildrenProducer(Producer<GetChildrenRequest, List<PublicComment>> getChildren) {
        this.getChildren = getChildren;
    }

    @Override
    public int getItemCount() {
        return commentsTree.visibleSize();
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
        holder.setComment(comment, (updatedComment, toggleButton) -> {
            commentsTree.setRepliesVisible(updatedComment, !updatedComment.isRepliesShown(), (request, producer) -> {
                // Create a new request with the button
                GetChildrenRequest requestWithButton = new GetChildrenRequest(request.getParentId(), toggleButton);
                getChildren.get(requestWithButton, producer);
            });
        });

        // Set up reply button click listener
        holder.setReplyClickListener(v -> {
            if (replyListener != null) {
                replyListener.call(comment);
            }
        });
        
        // Set up vote button click listeners
        holder.setUpVoteClickListener(v -> {
            if (upVoteListener != null) {
                upVoteListener.call(comment);
            }
        });
        
        holder.setDownVoteClickListener(v -> {
            if (downVoteListener != null) {
                downVoteListener.call(comment);
            }
        });
    }

    // Helper method to determine which comment corresponds to a given adapter position
    private RenderableComment findCommentForPosition(int position) {
        if (position < 0 || position >= commentsTree.visibleComments.size()) {
            return null;
        }
        return commentsTree.visibleComments.get(position);
    }

    /**
     * Get the position for a specific comment in the adapter
     *
     * @param comment The comment to find
     * @return The position or 0 if not found
     */
    public int getPositionForComment(RenderableComment comment) {
        if (comment == null || commentsTree.visibleComments.isEmpty()) {
            return 0;
        }

        String commentId = comment.getComment().getId();
        for (int i = 0; i < commentsTree.visibleComments.size(); i++) {
            RenderableComment renderableComment = commentsTree.visibleComments.get(i);
            if (renderableComment.getComment().getId().equals(commentId)) {
                return i;
            }
        }

        return 0;
    }

    public interface OnToggleRepliesListener {
        void onToggle(RenderableComment comment, Button toggleButton);
    }
}