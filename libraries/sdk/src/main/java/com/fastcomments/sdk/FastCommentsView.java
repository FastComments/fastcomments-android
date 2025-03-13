package com.fastcomments.sdk;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fastcomments.client.R;
import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.APICommentPublicComment;

import java.util.List;

public class FastCommentsView extends LinearLayout {

    private RecyclerView recyclerView;
    private CommentsAdapter adapter;
    private CommentFormView commentForm;
    private ProgressBar progressBar;
    private TextView emptyStateView;
    private FastCommentsSDK sdk;
    private CommentWidgetConfig config;
    private int currentPage = 1;

    public FastCommentsView(Context context) {
        super(context);
        init(context, null);
    }

    public FastCommentsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public FastCommentsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.fast_comments_view, this, true);
        
        recyclerView = findViewById(R.id.recyclerViewComments);
        progressBar = findViewById(R.id.commentsProgressBar);
        emptyStateView = findViewById(R.id.emptyStateView);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new CommentsAdapter();
        recyclerView.setAdapter(adapter);
        
        // Add comment form at the bottom
        commentForm = new CommentFormView(context);
        addView(commentForm);
        
        // Get SDK instance - this will throw an exception if not initialized
        sdk = FastCommentsSDK.getInstance();
    }
    
    /**
     * Configure the FastCommentsView with the specified configuration
     * @param config CommentWidgetConfig object
     */
    public void setConfig(CommentWidgetConfig config) {
        this.config = config;
        // Load comments when configuration is set
        loadComments();
        
        // Set up comment submission
        commentForm.setOnCommentSubmitListener(new CommentFormView.OnCommentSubmitListener() {
            @Override
            public void onCommentSubmit(String commentText) {
                postComment(commentText, null);
            }
        });
    }
    
    /**
     * Set a callback for when comments are loaded
     */
    public interface OnCommentsLoadedListener {
        void onCommentsLoaded(List<APICommentPublicComment> comments);
        void onError(Exception e);
    }
    
    private OnCommentsLoadedListener commentsLoadedListener;
    
    /**
     * Set a listener to be notified when comments are loaded
     * @param listener OnCommentsLoadedListener
     */
    public void setOnCommentsLoadedListener(OnCommentsLoadedListener listener) {
        this.commentsLoadedListener = listener;
    }
    
    /**
     * Set reply listener for comment adapter
     */
    public void setOnCommentReplyListener(CommentsAdapter.OnCommentReplyListener listener) {
        adapter.setOnCommentReplyListener(listener);
    }

    /**
     * Load comments from the API
     */
    public void loadComments() {
        if (config == null) {
            throw new IllegalStateException("Config not set. Call setConfig() first.");
        }
        
        showLoading(true);
        
        sdk.getComments(new FastCommentsSDK.CommentsCallback() {
            @Override
            public void onSuccess(List<APICommentPublicComment> comments) {
                showLoading(false);
                
                if (comments.isEmpty()) {
                    showEmptyState(true);
                } else {
                    showEmptyState(false);
                    adapter.setComments(comments);
                }
                
                if (commentsLoadedListener != null) {
                    commentsLoadedListener.onCommentsLoaded(comments);
                }
            }

            @Override
            public void onError(Exception e) {
                showLoading(false);
                showEmptyState(true);
                emptyStateView.setText(R.string.error_loading_comments);
                
                if (commentsLoadedListener != null) {
                    commentsLoadedListener.onError(e);
                }
            }
        });
    }
    
    /**
     * Post a new comment
     * @param commentText Text of the comment
     * @param parentId Parent comment ID for replies (null for top-level comments)
     */
    public void postComment(String commentText, String parentId) {
        if (config == null) {
            throw new IllegalStateException("Config not set. Call setConfig() first.");
        }
        
        commentForm.setSubmitting(true);
        
        sdk.postComment(commentText, parentId, new FastCommentsSDK.CommentPostCallback() {
            @Override
            public void onSuccess(APICommentPublicComment comment) {
                commentForm.setSubmitting(false);
                commentForm.clearText();
                
                // Refresh comments to include the new one
                loadComments();
            }

            @Override
            public void onError(Exception e) {
                commentForm.setSubmitting(false);
                commentForm.showError(e.getMessage());
            }
        });
    }
    
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }
    
    private void showEmptyState(boolean show) {
        emptyStateView.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }
    
    /**
     * Public method to manually reload comments
     */
    public void refresh() {
        loadComments();
    }
}