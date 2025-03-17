package com.fastcomments.sdk;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fastcomments.model.APIError;
import com.fastcomments.model.GetCommentsResponseWithPresencePublicComment;
import com.fastcomments.model.PublicComment;

public class FastCommentsView extends FrameLayout {

    private RecyclerView recyclerView;
    private CommentsAdapter adapter;
    private CommentFormView commentForm;
    private ProgressBar progressBar;
    private TextView emptyStateView;
    private FastCommentsSDK sdk;
    // TODO maintain relative comment dates

    public FastCommentsView(Context context, FastCommentsSDK sdk) {
        super(context);
        init(context, null, sdk);
    }

    public FastCommentsView(Context context, AttributeSet attrs, FastCommentsSDK sdk) {
        super(context, attrs);
        init(context, attrs, sdk);
    }

    public FastCommentsView(Context context, AttributeSet attrs, int defStyleAttr, FastCommentsSDK sdk) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, sdk);
    }

    private void init(Context context, AttributeSet attrs, FastCommentsSDK sdk) {
        LayoutInflater.from(context).inflate(R.layout.fast_comments_view, this, true);

        recyclerView = findViewById(R.id.recyclerViewComments);
        progressBar = findViewById(R.id.commentsProgressBar);
        emptyStateView = findViewById(R.id.emptyStateView);
        Button newCommentButton = findViewById(R.id.newCommentButton);
        
        // Find the comment form container and initialize the form
        FrameLayout commentFormContainer = findViewById(R.id.commentFormContainer);
        commentForm = new CommentFormView(context);
        commentFormContainer.addView(commentForm);
        
        // Hide the form initially
        commentFormContainer.setVisibility(View.GONE);

        // Setup new comment button
        newCommentButton.setOnClickListener(v -> {
            // Show form for a new top-level comment
            commentForm.resetReplyState();
            commentFormContainer.setVisibility(View.VISIBLE);
            newCommentButton.setVisibility(View.GONE);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new CommentsAdapter(context, sdk.commentsTree);
        recyclerView.setAdapter(adapter);

        // Make button accessible from other methods
        final Button finalNewCommentButton = newCommentButton;
        
        // Setup form listeners
        commentForm.setOnCommentSubmitListener((commentText, parentId) -> {
            postComment(commentText, parentId);
            // Hide form after submitting
            commentFormContainer.setVisibility(View.GONE);
            // Show the new comment button again
            finalNewCommentButton.setVisibility(View.VISIBLE);
        });
        
        commentForm.setOnCancelReplyListener(() -> {
            // Hide the form when canceling a reply
            commentFormContainer.setVisibility(View.GONE);
            // Show the new comment button again
            finalNewCommentButton.setVisibility(View.VISIBLE);
        });
        
        if (sdk.getCurrentUser() != null) {
            commentForm.setCurrentUser(sdk.getCurrentUser());
        }

        // Handle reply requests from comments
        adapter.setRequestingReplyListener((commentToReplyTo) -> {
            // Show form in reply mode
            commentForm.setReplyingTo(commentToReplyTo);
            // Make form visible
            commentFormContainer.setVisibility(View.VISIBLE);
            // Hide the new comment button while replying
            finalNewCommentButton.setVisibility(View.GONE);
            // Scroll to show both the comment and the form
            recyclerView.smoothScrollToPosition(adapter.getPositionForComment(commentToReplyTo));
        });

        this.sdk = sdk;
    }

    public void load() {
        showLoading(true);

        sdk.load(new FCCallback<GetCommentsResponseWithPresencePublicComment>() {
            @Override
            public boolean onFailure(APIError error) {
                Log.e("FastCommentsView", error.toString());
                getHandler().post(() -> {
                    showLoading(false);
                    setIsEmpty(true);
                    emptyStateView.setText(R.string.error_loading_comments);
                });
                return CONSUME;
            }

            @Override
            public boolean onSuccess(GetCommentsResponseWithPresencePublicComment response) {
                getHandler().post(() -> {
                    showLoading(false);

                    if (response.getComments().isEmpty()) {
                        setIsEmpty(true);
                    } else {
                        setIsEmpty(false);
                        adapter.notifyDataSetChanged();
                    }
                });
                return CONSUME;
            }
        });
    }

    /**
     * Post a new comment
     *
     * @param commentText Text of the comment
     * @param parentId    Parent comment ID for replies (null for top-level comments)
     */
    public void postComment(String commentText, String parentId) {
        commentForm.setSubmitting(true);

//        sdk.postComment(commentText, parentId, new FCCallback<PublicComment>() {
//            @Override
//            public boolean onFailure(APIError error) {
//                getHandler().post(() -> {
//                    commentForm.setSubmitting(false);
//                    commentForm.showError(error.getMessage());
//                });
//                return CONSUME;
//            }
//
//            @Override
//            public boolean onSuccess(PublicComment comment) {
//                getHandler().post(() -> {
//                    commentForm.setSubmitting(false);
//                    commentForm.clearText();
//                    commentForm.resetReplyState();
//
//                    // Refresh comments to include the new one
//                    refresh();
//                });
//                return CONSUME;
//            }
//        });
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        if (isLoading) {
            recyclerView.setVisibility(View.GONE);
        }
    }

    private void setIsEmpty(boolean isEmpty) {
        emptyStateView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    /**
     * Public method to manually reload comments
     */
    public void refresh() {
        load();
    }
}