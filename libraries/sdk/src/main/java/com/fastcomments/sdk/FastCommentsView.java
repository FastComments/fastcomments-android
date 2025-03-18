package com.fastcomments.sdk;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fastcomments.model.APIError;
import com.fastcomments.model.GetCommentsResponseWithPresencePublicComment;

public class FastCommentsView extends FrameLayout {

    private RecyclerView recyclerView;
    private CommentsAdapter adapter;
    private CommentFormView commentForm;
    private ProgressBar progressBar;
    private TextView emptyStateView;
    private FastCommentsSDK sdk;
    private FrameLayout commentFormContainer;
    private Button newCommentButton;
    private OnBackPressedCallback backPressedCallback;
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
        this.newCommentButton = findViewById(R.id.newCommentButton);

        // Find the comment form container and initialize the form
        this.commentFormContainer = findViewById(R.id.commentFormContainer);
        commentForm = new CommentFormView(context);
        this.commentFormContainer.addView(commentForm);

        // Hide the form initially
        this.commentFormContainer.setVisibility(View.GONE);

        // Set up back button handling if in an AppCompatActivity
        if (context instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) context;
            backPressedCallback = new OnBackPressedCallback(false) {
                @Override
                public void handleOnBackPressed() {
                    // Hide the form with animation
                    hideCommentForm();
                    // Reset the form
                    commentForm.resetReplyState();
                }
            };
            activity.getOnBackPressedDispatcher().addCallback(backPressedCallback);
        } else if (context instanceof Activity) {
            // Log a warning that back press won't be handled
            Log.w("FastCommentsView", "Context is an Activity but not AppCompatActivity. Back button handling not supported.");
        }

        // Setup new comment button
        newCommentButton.setOnClickListener(v -> {
            // Show form for a new top-level comment
            commentForm.resetReplyState();
            showCommentForm();
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new CommentsAdapter(context, sdk);
        recyclerView.setAdapter(adapter);

        // Setup form listeners
        commentForm.setOnCommentSubmitListener((commentText, parentId) -> {
            postComment(commentText, parentId);
            // Hide form after submitting with animation
            hideCommentForm();
        });

        commentForm.setOnCancelReplyListener(() -> {
            // Hide the form when canceling a reply with animation
            hideCommentForm();
        });

        // Handle reply requests from comments
        adapter.setRequestingReplyListener((commentToReplyTo) -> {
            // Show form in reply mode
            commentForm.setReplyingTo(commentToReplyTo);
            // Make form visible with animation
            showCommentForm();
            // Scroll to show both the comment and the form
            recyclerView.smoothScrollToPosition(adapter.getPositionForComment(commentToReplyTo));
        });

        adapter.setGetChildrenProducer((request, sendResults) -> {
            String parentId = request.getParentId();
            Button toggleButton = request.getToggleButton();
            
            // Set the button text to "Loading..." when starting to load
            if (toggleButton != null) {
                getHandler().post(() -> toggleButton.setText(R.string.loading_replies));
            }
            
            sdk.getCommentsForParent(null, null, 0, parentId, new FCCallback<GetCommentsResponseWithPresencePublicComment>() {
                @Override
                public boolean onFailure(APIError error) {
                    getHandler().post(() -> {
                        // Show toast with error message
                        android.widget.Toast.makeText(
                            getContext(), 
                            R.string.error_loading_replies, 
                            android.widget.Toast.LENGTH_SHORT
                        ).show();
                        
                        // Reset button text if the toggle button is available
                        if (toggleButton != null) {
                            // Get the comment to retrieve the child count
                            RenderableComment comment = sdk.commentsTree.commentsById.get(parentId);
                            if (comment != null && comment.getComment().getChildCount() != null) {
                                toggleButton.setText(getContext().getString(
                                    R.string.show_replies, 
                                    comment.getComment().getChildCount())
                                );
                            }
                        }
                    });
                    return CONSUME;
                }

                @Override
                public boolean onSuccess(GetCommentsResponseWithPresencePublicComment response) {
                    getHandler().post(() -> sendResults.call(response.getComments()));
                    return CONSUME;
                }
            });
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
     * Shows the comment form with animation
     */
    private void showCommentForm() {
        commentForm.setCurrentUser(sdk.getCurrentUser());
        // Load and start the animation
        Animation slideUpAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.slide_up);
        commentFormContainer.startAnimation(slideUpAnimation);
        commentFormContainer.setVisibility(View.VISIBLE);

        // Hide the new comment button
        Animation fadeOutAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.fade_out);
        newCommentButton.startAnimation(fadeOutAnimation);
        newCommentButton.setVisibility(View.GONE);

        // Enable back button handling
        if (backPressedCallback != null) {
            backPressedCallback.setEnabled(true);
        }
    }

    /**
     * Hides the comment form with animation
     */
    private void hideCommentForm() {
        // Load and start the animation
        Animation slideDownAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.slide_down);
        slideDownAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                commentFormContainer.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        commentFormContainer.startAnimation(slideDownAnimation);

        // Show the new comment button
        Animation fadeInAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.fade_in);
        newCommentButton.startAnimation(fadeInAnimation);
        newCommentButton.setVisibility(View.VISIBLE);

        // Disable back button handling
        if (backPressedCallback != null) {
            backPressedCallback.setEnabled(false);
        }
    }

    /**
     * Public method to manually reload comments
     */
    public void refresh() {
        load();
    }
}