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

public class CommentsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_COMMENT = 0;
    private static final int VIEW_TYPE_BUTTON = 1;

    private final Context context;
    private final CommentsTree commentsTree;
    private final FastCommentsSDK sdk;
    private Callback<RenderableComment> replyListener;
    private Callback<RenderableComment> upVoteListener;
    private Callback<RenderableComment> downVoteListener;
    private Producer<GetChildrenRequest, List<PublicComment>> getChildren;
    private Callback<String> newChildCommentsListener; // Triggered when clicking "Show new replies" button
    private OnCommentMenuItemListener commentMenuListener; // Listener for comment menu actions

    public CommentsAdapter(Context context, FastCommentsSDK sdk) {
        this.context = context;
        this.commentsTree = sdk.commentsTree;
        this.sdk = sdk;
        commentsTree.setAdapter(this);
    }
    
    public Context getContext() {
        return context;
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
    
    public void setNewChildCommentsListener(Callback<String> listener) {
        this.newChildCommentsListener = listener;
    }
    
    public void setCommentMenuListener(OnCommentMenuItemListener listener) {
        this.commentMenuListener = listener;
    }

    public void setGetChildrenProducer(Producer<GetChildrenRequest, List<PublicComment>> getChildren) {
        this.getChildren = getChildren;
    }

    @Override
    public int getItemCount() {
        return commentsTree.visibleSize();
    }
    
    @Override
    public int getItemViewType(int position) {
        RenderableNode node = commentsTree.visibleNodes.get(position);
        if (node instanceof RenderableComment) {
            return VIEW_TYPE_COMMENT;
        } else {
            return VIEW_TYPE_BUTTON;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_COMMENT) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment_new, parent, false);
            return new CommentViewHolder(context, sdk, view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_button, parent, false);
            return new ButtonViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof CommentViewHolder) {
            bindCommentViewHolder((CommentViewHolder) holder, position);
        } else if (holder instanceof ButtonViewHolder) {
            bindButtonViewHolder((ButtonViewHolder) holder, position);
        }
    }
    
    private void bindCommentViewHolder(CommentViewHolder holder, int position) {
        final RenderableComment comment = (RenderableComment) commentsTree.visibleNodes.get(position);
        
        // Pass config setting for unverified label
        boolean disableUnverifiedLabel = Boolean.TRUE.equals(sdk.getConfig().disableUnverifiedLabel);
                
        holder.setComment(comment, disableUnverifiedLabel, (updatedComment, toggleButton) -> {
            commentsTree.setRepliesVisible(updatedComment, !updatedComment.isRepliesShown, (request, producer) -> {
                // Create a new request with the button
                GetChildrenRequest requestWithButton = new GetChildrenRequest(request.getParentId(), toggleButton);
                getChildren.get(requestWithButton, producer);
            });
        });

        // Set up reply button click listener - check if maxReplyDepth is 0 (live chat mode)
        if (sdk.getConfig().maxReplyDepth != null && sdk.getConfig().maxReplyDepth == 0) {
            // Hide reply button in live chat mode
            holder.replyButton.setVisibility(View.GONE);
        } else {
            // Set up reply button click listener for regular comment mode
            holder.setReplyClickListener(v -> {
                if (replyListener != null) {
                    replyListener.call(comment);
                }
            });
        }
        
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
        
        // Set up heart button click listener (uses same upvote handler)
        holder.setHeartClickListener(v -> {
            if (upVoteListener != null) {
                upVoteListener.call(comment);
            }
        });
        
        // Set up comment menu click listener
        holder.setCommentMenuClickListener(commentMenuListener);
        
        // Set up load more children click listener
        holder.setLoadMoreChildrenClickListener(v -> {
            if (getChildren != null && comment.isRepliesShown && comment.hasMoreChildren) {
                // Mark as loading to update UI
                comment.isLoadingChildren = true;
                notifyItemChanged(position);
                
                // Create a request for the next page of child comments
                GetChildrenRequest paginationRequest = new GetChildrenRequest(
                        comment.getComment().getId(),
                        null,
                        comment.childSkip,
                        comment.childPageSize,
                        true
                );
                
                getChildren.get(paginationRequest, childComments -> {
                    // Update has more flag based on response
                    getHandler().post(() -> {
                        comment.isLoadingChildren = false;
                        notifyItemChanged(position);
                    });
                });
            }
        });
    }
    
    private void bindButtonViewHolder(ButtonViewHolder holder, int position) {
        final RenderableButton button = (RenderableButton) commentsTree.visibleNodes.get(position);
        
        if (button.getButtonType() == RenderableButton.TYPE_NEW_ROOT_COMMENTS) {
            // New root comments button
            holder.setButtonText(context.getString(R.string.show_new_comments, button.getCommentCount()));
            holder.setButtonClickListener(v -> {
                commentsTree.showNewRootComments();
            });
        } else if (button.getButtonType() == RenderableButton.TYPE_NEW_CHILD_COMMENTS) {
            // New child comments button for a specific parent
            holder.setButtonText(context.getString(R.string.show_new_replies, button.getCommentCount()));
            holder.setButtonClickListener(v -> {
                String parentId = button.getParentId();
                if (parentId != null) {
                    commentsTree.showNewChildComments(parentId);
                    if (newChildCommentsListener != null) {
                        newChildCommentsListener.call(parentId);
                    }
                }
            });
        }
    }
    
    private android.os.Handler getHandler() {
        return new android.os.Handler(android.os.Looper.getMainLooper());
    }

    /**
     * Get the position for a specific comment in the adapter
     *
     * @param comment The comment to find
     * @return The position or 0 if not found
     */
    public int getPositionForComment(RenderableComment comment) {
        if (comment == null || commentsTree.visibleNodes.isEmpty()) {
            return 0;
        }

        return commentsTree.visibleNodes.indexOf(comment);
    }

    public interface OnToggleRepliesListener {
        void onToggle(RenderableComment comment, Button toggleButton);
    }
    
    /**
     * Listener for comment menu items
     */
    public interface OnCommentMenuItemListener {
        void onEdit(String commentId, String commentText);
        void onFlag(String commentId);
        void onBlock(String commentId, String userName);
    }
    
    /**
     * ViewHolder for button items that prompt the user to load new comments
     */
    static class ButtonViewHolder extends RecyclerView.ViewHolder {
        private final Button button;
        
        public ButtonViewHolder(@NonNull View itemView) {
            super(itemView);
            button = itemView.findViewById(R.id.btnNewComments);
        }
        
        public void setButtonText(String text) {
            button.setText(text);
        }
        
        public void setButtonClickListener(View.OnClickListener listener) {
            button.setOnClickListener(listener);
        }
    }
}