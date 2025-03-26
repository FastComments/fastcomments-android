package com.fastcomments;

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

import com.fastcomments.model.FeedPost;
import com.fastcomments.sdk.FastCommentsFeedSDK;
import com.fastcomments.sdk.FastCommentsSDK;
import com.fastcomments.sdk.FastCommentsView;

/**
 * Dialog for displaying comments for a post
 */
public class CommentsDialog extends Dialog {

    private final FeedPost post;
    private final FastCommentsFeedSDK feedSDK;
    private FastCommentsView commentsView;

    public CommentsDialog(@NonNull Context context, FeedPost post, FastCommentsFeedSDK feedSDK) {
        super(context);
        this.post = post;
        this.feedSDK = feedSDK;
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
        closeButton.setOnClickListener(v -> dismiss());
        
        // Set title if available
        TextView titleTextView = findViewById(R.id.titleTextView);
        if (post.getTitle() != null && !post.getTitle().isEmpty()) {
            titleTextView.setText(post.getTitle());
        }
        
        // Create the comments SDK and view
        FastCommentsSDK commentsSDK = feedSDK.createCommentsSDKForPost(post);
        commentsView = new FastCommentsView(getContext(), commentsSDK);
        
        // Add comments view to container
        FrameLayout container = findViewById(R.id.commentsContainer);
        container.addView(commentsView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        
        // Load comments
        commentsView.load();
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