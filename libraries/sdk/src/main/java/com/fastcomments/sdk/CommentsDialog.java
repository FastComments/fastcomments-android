package com.fastcomments.sdk;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import com.fastcomments.model.APIError;
import com.fastcomments.model.FeedPost;
import com.fastcomments.model.GetFeedPostsStats200Response;
import com.fastcomments.model.PublicComment;

/**
 * Dialog for displaying comments for a post
 */
public class CommentsDialog extends Dialog {

    private final FeedPost post;
    private final FastCommentsFeedSDK feedSDK;
    private FastCommentsView commentsView;
    private OnCommentAddedListener commentAddedListener;
    private OnUserClickListener userClickListener;
    
    /**
     * Interface for notifying when a comment is added
     */
    public interface OnCommentAddedListener {
        void onCommentAdded(String postId);
    }

    public CommentsDialog(@NonNull Context context, FeedPost post, FastCommentsFeedSDK feedSDK) {
        super(context);
        this.post = post;
        this.feedSDK = feedSDK;
    }
    
    /**
     * Set a listener to be notified when a comment is added
     */
    public void setOnCommentAddedListener(OnCommentAddedListener listener) {
        this.commentAddedListener = listener;
    }
    
    /**
     * Get the current comment added listener
     * @return The current listener
     */
    public OnCommentAddedListener getOnCommentAddedListener() {
        return commentAddedListener;
    }
    
    /**
     * Set a listener to be notified when a user (name or avatar) is clicked
     * @param listener The listener to set
     */
    public void setOnUserClickListener(OnUserClickListener listener) {
        this.userClickListener = listener;
        // If commentsView is already created, pass the listener to it
        if (commentsView != null) {
            commentsView.setOnUserClickListener(listener);
        }
    }
    
    /**
     * Get the current user click listener
     * @return The current listener
     */
    public OnUserClickListener getOnUserClickListener() {
        return userClickListener;
    }
    
    /**
     * Get the post ID for this dialog
     * @return The post ID
     */
    public String getPostId() {
        return post != null ? post.getId() : null;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Remove default dialog title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_comments);
        
        // Make dialog fill most of the screen
        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 
                    ViewGroup.LayoutParams.MATCH_PARENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        // Set up close button
        ImageButton closeButton = findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> {
            // Use the same logic as onBackPressed
            onBackPressed();
        });
        
        // Set title if available
        TextView titleTextView = findViewById(R.id.titleTextView);
        if (post.getTitle() != null && !post.getTitle().isEmpty()) {
            titleTextView.setText(post.getTitle());
        }
        
        // Create the comments SDK and view
        FastCommentsSDK commentsSDK = feedSDK.createCommentsSDKForPost(post);
        commentsView = new FastCommentsView(getContext(), commentsSDK);
        
        // Set user click listener if one was provided
        if (userClickListener != null) {
            commentsView.setOnUserClickListener(userClickListener);
        }
        
        // Set up comment form listener to know when a comment is posted
        commentsView.setCommentPostListener((comment) -> {
            // Update post stats to refresh comment count
            if (post.getId() != null) {
                List<String> postIds = new ArrayList<>(1);
                postIds.add(post.getId());
                feedSDK.getFeedPostsStats(postIds, new FCCallback<GetFeedPostsStats200Response>() {
                    @Override
                    public boolean onFailure(APIError error) {
                        // Silently fail - not critical
                        return CONSUME;
                    }
                    
                    @Override
                    public boolean onSuccess(GetFeedPostsStats200Response response) {
                        // Stats updated in the SDK's cache
                        
                        // Notify the listener that a comment was added
                        if (commentAddedListener != null) {
                            commentAddedListener.onCommentAdded(post.getId());
                        }
                        
                        return CONSUME;
                    }
                });
            } else if (commentAddedListener != null && post.getId() != null) {
                // Fallback if we can't get stats
                commentAddedListener.onCommentAdded(post.getId());
            }
        });
        
        // Add comments view to container
        FrameLayout container = findViewById(R.id.commentsContainer);
        container.addView(commentsView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        
        // Load comments
        commentsView.load();
    }
    
    /**
     * Check if the back press should be intercepted to handle comment form state
     * 
     * @return true if the back press should be intercepted, false otherwise
     */
    private boolean shouldInterceptBackPress() {
        // If commentsView is null, no need to intercept
        if (commentsView == null) {
            return false;
        }
        
        // Get the bottom comment input from CommentsView
        BottomCommentInputView bottomInput = commentsView.getBottomCommentInput();
        if (bottomInput == null) {
            return false;
        }
        
        // Check if user has typed any text (either in reply or new comment)
        boolean hasText = !bottomInput.isTextEmpty();
        
        return hasText;
    }
    
    @Override
    public void onBackPressed() {
        if (shouldInterceptBackPress()) {
            // Get the bottom input and check if it has a parent comment (reply) or text
            BottomCommentInputView bottomInput = commentsView.getBottomCommentInput();
            RenderableComment parentComment = bottomInput.getParentComment();
            
            // Show confirmation dialog
            String title, message;
            if (parentComment != null) {
                title = getContext().getString(R.string.cancel_reply_title);
                message = getContext().getString(R.string.cancel_reply_confirm);
            } else {
                title = getContext().getString(R.string.cancel_comment_title);
                message = getContext().getString(R.string.cancel_comment_confirm);
            }
            
            new android.app.AlertDialog.Builder(getContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    // Proceed with cancellation
                    bottomInput.clearReplyState();
                    bottomInput.clearText();
                    
                    // Don't dismiss the dialog yet - let the user continue with comments
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
        } else {
            // No need to intercept, just dismiss the dialog
            super.onBackPressed();
        }
    }
    
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Clean up resources
        if (commentsView != null) {
            // CommentsView will clean up the SDK
            commentsView = null;
        }
    }
}