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
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fastcomments.model.APIError;
import com.fastcomments.model.GetCommentsResponseWithPresencePublicComment;
import com.fastcomments.model.VoteResponse;
import com.fastcomments.model.VoteDeleteResponse;
import com.fastcomments.model.ImportedAPIStatusFAILED;

public class FastCommentsView extends FrameLayout {

    private RecyclerView recyclerView;
    private CommentsAdapter adapter;
    private CommentFormView commentForm;
    private ProgressBar progressBar;
    private TextView emptyStateView;
    private FastCommentsSDK sdk;
    private FrameLayout commentFormContainer;
    private FloatingActionButton newCommentButton;
    private View paginationControls;
    private Button btnNextComments;
    private Button btnLoadAll;
    private ProgressBar paginationProgressBar;
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

        // Initialize pagination controls
        this.paginationControls = findViewById(R.id.paginationControls);
        this.btnNextComments = paginationControls.findViewById(R.id.btnNextComments);
        this.btnLoadAll = paginationControls.findViewById(R.id.btnLoadAll);
        this.paginationProgressBar = paginationControls.findViewById(R.id.paginationProgressBar);

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

        // Setup pagination button listeners
        btnNextComments.setOnClickListener(v -> {
            loadMoreComments();
        });

        btnLoadAll.setOnClickListener(v -> {
            loadAllComments();
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
        
        // Handle upvote requests
        adapter.setUpVoteListener((commentToVote) -> {
            if (sdk.getCurrentUser() == null) {
                // Show login required message
                android.widget.Toast.makeText(
                        getContext(),
                        R.string.login_to_vote,
                        android.widget.Toast.LENGTH_SHORT
                ).show();
                return;
            }
            
            // Get commenter name for the toast message
            String commenterName = commentToVote.getComment().getCommenterName();
            if (commenterName == null || commenterName.isEmpty()) {
                commenterName = getContext().getString(R.string.anonymous);
            }
            
            // Save original state for rollback in case of API failure
            final Boolean originalIsVotedUp = commentToVote.getComment().getIsVotedUp();
            final Boolean originalIsVotedDown = commentToVote.getComment().getIsVotedDown();
            final Integer originalVotesUp = commentToVote.getComment().getVotesUp();
            final Integer originalVotesDown = commentToVote.getComment().getVotesDown();
            final String originalVoteId = commentToVote.getComment().getMyVoteId();
            
            String toastMessage;
            boolean needToDelete = false;
            
            // Toggle the vote state
            if (originalIsVotedUp != null && originalIsVotedUp) {
                // User already upvoted, so remove the vote
                commentToVote.getComment().setIsVotedUp(false);
                // Update vote count
                Integer votesUp = commentToVote.getComment().getVotesUp();
                if (votesUp != null && votesUp > 0) {
                    commentToVote.getComment().setVotesUp(votesUp - 1);
                }
                toastMessage = getContext().getString(R.string.you_removed_upvote, commenterName);
                needToDelete = true;
            } else {
                // User hasn't upvoted, so add the vote
                commentToVote.getComment().setIsVotedUp(true);
                // Also remove downvote if exists
                if (commentToVote.getComment().getIsVotedDown() != null && 
                    commentToVote.getComment().getIsVotedDown()) {
                    commentToVote.getComment().setIsVotedDown(false);
                    // Update downvote count
                    Integer votesDown = commentToVote.getComment().getVotesDown();
                    if (votesDown != null && votesDown > 0) {
                        commentToVote.getComment().setVotesDown(votesDown - 1);
                    }
                    needToDelete = true;
                }
                // Update upvote count
                Integer votesUp = commentToVote.getComment().getVotesUp();
                if (votesUp != null) {
                    commentToVote.getComment().setVotesUp(votesUp + 1);
                } else {
                    commentToVote.getComment().setVotesUp(1);
                }
                toastMessage = getContext().getString(R.string.you_upvoted, commenterName);
            }
            
            // Notify adapter to update UI immediately for better UX
            adapter.notifyDataSetChanged();
            
            // Show toast message
            android.widget.Toast.makeText(
                    getContext(),
                    toastMessage,
                    android.widget.Toast.LENGTH_SHORT
            ).show();
            
            // Create error handler for voting operations
            final FCCallback<APIError> upvoteErrorHandler = new FCCallback<APIError>() {
                @Override
                public boolean onFailure(APIError error) {
                    // This shouldn't get called since we're already in a failure handler
                    return CONSUME;
                }
                
                @Override
                public boolean onSuccess(APIError error) {
                    // Revert to original state on failure
                    commentToVote.getComment().setIsVotedUp(originalIsVotedUp);
                    commentToVote.getComment().setIsVotedDown(originalIsVotedDown);
                    commentToVote.getComment().setVotesUp(originalVotesUp);
                    commentToVote.getComment().setVotesDown(originalVotesDown);
                    commentToVote.getComment().setMyVoteId(originalVoteId);
                    
                    // Show error message
                    getHandler().post(() -> {
                        android.widget.Toast.makeText(
                                getContext(),
                                R.string.error_voting,
                                android.widget.Toast.LENGTH_SHORT
                        ).show();
                        
                        // Update the UI
                        adapter.notifyDataSetChanged();
                    });
                    
                    return CONSUME;
                }
            };
            
            // The general vote flow: 
            // 1. If there's an existing vote, delete it first
            // 2. Then make a new vote if needed (not needed when removing an upvote)
            
            if (needToDelete && originalVoteId != null) {
                // Delete the existing vote first
                sdk.deleteCommentVote(commentToVote.getComment().getId(), originalVoteId, new FCCallback<VoteDeleteResponse>() {
                    @Override
                    public boolean onFailure(APIError error) {
                        return upvoteErrorHandler.onSuccess(error);
                    }
                    
                    @Override
                    public boolean onSuccess(VoteDeleteResponse response) {
                        // Clear vote ID since it was deleted
                        commentToVote.getComment().setMyVoteId(null);
                        
                        // If removing an upvote, we're done
                        if (originalIsVotedUp != null && originalIsVotedUp) {
                            return CONSUME;
                        }
                        
                        // Otherwise, we need to add a new upvote
                        sdk.voteComment(commentToVote.getComment().getId(), true, new FCCallback<VoteResponse>() {
                            @Override
                            public boolean onFailure(APIError error) {
                                return upvoteErrorHandler.onSuccess(error);
                            }
                            
                            @Override
                            public boolean onSuccess(VoteResponse response) {
                                // Store new vote ID
                                commentToVote.getComment().setMyVoteId(response.getVoteId());
                                return CONSUME;
                            }
                        });
                        
                        return CONSUME;
                    }
                });
            } else if (originalIsVotedUp == null || !originalIsVotedUp) {
                // No existing vote to delete or it's a downvote being converted to upvote
                // Just add a new upvote
                sdk.voteComment(commentToVote.getComment().getId(), true, new FCCallback<VoteResponse>() {
                    @Override
                    public boolean onFailure(APIError error) {
                        return upvoteErrorHandler.onSuccess(error);
                    }
                    
                    @Override
                    public boolean onSuccess(VoteResponse response) {
                        // Store new vote ID
                        commentToVote.getComment().setMyVoteId(response.getVoteId());
                        return CONSUME;
                    }
                });
            }
        });
        
        // Handle downvote requests
        adapter.setDownVoteListener((commentToVote) -> {
            if (sdk.getCurrentUser() == null) {
                // Show login required message
                android.widget.Toast.makeText(
                        getContext(),
                        R.string.login_to_vote,
                        android.widget.Toast.LENGTH_SHORT
                ).show();
                return;
            }
            
            // Get commenter name for the toast message
            String commenterName = commentToVote.getComment().getCommenterName();
            if (commenterName == null || commenterName.isEmpty()) {
                commenterName = getContext().getString(R.string.anonymous);
            }
            
            // Save original state for rollback in case of API failure
            final Boolean originalIsVotedUp = commentToVote.getComment().getIsVotedUp();
            final Boolean originalIsVotedDown = commentToVote.getComment().getIsVotedDown();
            final Integer originalVotesUp = commentToVote.getComment().getVotesUp();
            final Integer originalVotesDown = commentToVote.getComment().getVotesDown();
            final String originalVoteId = commentToVote.getComment().getMyVoteId();
            
            String toastMessage;
            boolean needToDelete = false;
            
            // Toggle the vote state
            if (originalIsVotedDown != null && originalIsVotedDown) {
                // User already downvoted, so remove the vote
                commentToVote.getComment().setIsVotedDown(false);
                // Update vote count
                Integer votesDown = commentToVote.getComment().getVotesDown();
                if (votesDown != null && votesDown > 0) {
                    commentToVote.getComment().setVotesDown(votesDown - 1);
                }
                toastMessage = getContext().getString(R.string.you_removed_downvote, commenterName);
                needToDelete = true;
            } else {
                // User hasn't downvoted, so add the vote
                commentToVote.getComment().setIsVotedDown(true);
                // Also remove upvote if exists
                if (commentToVote.getComment().getIsVotedUp() != null && 
                    commentToVote.getComment().getIsVotedUp()) {
                    commentToVote.getComment().setIsVotedUp(false);
                    // Update upvote count
                    Integer votesUp = commentToVote.getComment().getVotesUp();
                    if (votesUp != null && votesUp > 0) {
                        commentToVote.getComment().setVotesUp(votesUp - 1);
                    }
                    needToDelete = true;
                }
                // Update downvote count
                Integer votesDown = commentToVote.getComment().getVotesDown();
                if (votesDown != null) {
                    commentToVote.getComment().setVotesDown(votesDown + 1);
                } else {
                    commentToVote.getComment().setVotesDown(1);
                }
                toastMessage = getContext().getString(R.string.you_downvoted, commenterName);
            }
            
            // Notify adapter to update UI immediately for better UX
            adapter.notifyDataSetChanged();
            
            // Show toast message
            android.widget.Toast.makeText(
                    getContext(),
                    toastMessage,
                    android.widget.Toast.LENGTH_SHORT
            ).show();
            
            // Create error handler for voting operations
            final FCCallback<APIError> downvoteErrorHandler = new FCCallback<APIError>() {
                @Override
                public boolean onFailure(APIError error) {
                    // This shouldn't get called since we're already in a failure handler
                    return CONSUME;
                }
                
                @Override
                public boolean onSuccess(APIError error) {
                    // Revert to original state on failure
                    commentToVote.getComment().setIsVotedUp(originalIsVotedUp);
                    commentToVote.getComment().setIsVotedDown(originalIsVotedDown);
                    commentToVote.getComment().setVotesUp(originalVotesUp);
                    commentToVote.getComment().setVotesDown(originalVotesDown);
                    commentToVote.getComment().setMyVoteId(originalVoteId);
                    
                    // Show error message
                    getHandler().post(() -> {
                        android.widget.Toast.makeText(
                                getContext(),
                                R.string.error_voting,
                                android.widget.Toast.LENGTH_SHORT
                        ).show();
                        
                        // Update the UI
                        adapter.notifyDataSetChanged();
                    });
                    
                    return CONSUME;
                }
            };
            
            // The general vote flow: 
            // 1. If there's an existing vote, delete it first
            // 2. Then make a new vote if needed (not needed when removing a downvote)
            
            if (needToDelete && originalVoteId != null) {
                // Delete the existing vote first
                sdk.deleteCommentVote(commentToVote.getComment().getId(), originalVoteId, new FCCallback<VoteDeleteResponse>() {
                    @Override
                    public boolean onFailure(APIError error) {
                        return downvoteErrorHandler.onSuccess(error);
                    }
                    
                    @Override
                    public boolean onSuccess(VoteDeleteResponse response) {
                        // Clear vote ID since it was deleted
                        commentToVote.getComment().setMyVoteId(null);
                        
                        // If removing a downvote, we're done
                        if (originalIsVotedDown != null && originalIsVotedDown) {
                            return CONSUME;
                        }
                        
                        // Otherwise, we need to add a new downvote
                        sdk.voteComment(commentToVote.getComment().getId(), false, new FCCallback<VoteResponse>() {
                            @Override
                            public boolean onFailure(APIError error) {
                                return downvoteErrorHandler.onSuccess(error);
                            }
                            
                            @Override
                            public boolean onSuccess(VoteResponse response) {
                                // Store new vote ID
                                commentToVote.getComment().setMyVoteId(response.getVoteId());
                                return CONSUME;
                            }
                        });
                        
                        return CONSUME;
                    }
                });
            } else if (originalIsVotedDown == null || !originalIsVotedDown) {
                // No existing vote to delete or it's an upvote being converted to downvote
                // Just add a new downvote
                sdk.voteComment(commentToVote.getComment().getId(), false, new FCCallback<VoteResponse>() {
                    @Override
                    public boolean onFailure(APIError error) {
                        return downvoteErrorHandler.onSuccess(error);
                    }
                    
                    @Override
                    public boolean onSuccess(VoteResponse response) {
                        // Store new vote ID
                        commentToVote.getComment().setMyVoteId(response.getVoteId());
                        return CONSUME;
                    }
                });
            }
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
                        paginationControls.setVisibility(View.GONE);
                    } else {
                        setIsEmpty(false);
                        adapter.notifyDataSetChanged();

                        // Update pagination controls
                        updatePaginationControls();
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

    /**
     * Update the pagination controls based on current state
     */
    private void updatePaginationControls() {
        if (sdk.hasMore) {
            paginationControls.setVisibility(View.VISIBLE);

            // Update "Next" button text with count
            int countToShow = sdk.getCountRemainingToShow();
            if (countToShow > 0) {
                btnNextComments.setText(getContext().getString(R.string.next_comments, countToShow));
                btnNextComments.setVisibility(View.VISIBLE);
            } else {
                btnNextComments.setVisibility(View.GONE);
            }

            // Show/hide "Load All" button based on total comment count
            if (sdk.shouldShowLoadAll()) {
                btnLoadAll.setVisibility(View.VISIBLE);
                btnLoadAll.setText(getContext().getString(R.string.load_all, sdk.commentCountOnServer));
            } else {
                btnLoadAll.setVisibility(View.GONE);
            }
        } else {
            paginationControls.setVisibility(View.GONE);
        }
    }

    /**
     * Load more comments (next page)
     */
    private void loadMoreComments() {
        // Show loading indicator
        btnNextComments.setVisibility(View.GONE);
        btnLoadAll.setVisibility(View.GONE);
        paginationProgressBar.setVisibility(View.VISIBLE);

        sdk.loadMore(new FCCallback<GetCommentsResponseWithPresencePublicComment>() {
            @Override
            public boolean onFailure(APIError error) {
                getHandler().post(() -> {
                    // Hide loading indicator
                    paginationProgressBar.setVisibility(View.GONE);
                    btnNextComments.setVisibility(View.VISIBLE);

                    if (sdk.shouldShowLoadAll()) {
                        btnLoadAll.setVisibility(View.VISIBLE);
                    }

                    // Show error toast
                    android.widget.Toast.makeText(
                            getContext(),
                            R.string.error_loading_comments,
                            android.widget.Toast.LENGTH_SHORT
                    ).show();
                });
                return CONSUME;
            }

            @Override
            public boolean onSuccess(GetCommentsResponseWithPresencePublicComment response) {
                getHandler().post(() -> {
                    // Hide loading indicator
                    paginationProgressBar.setVisibility(View.GONE);

                    // Update pagination controls
                    updatePaginationControls();
                });
                return CONSUME;
            }
        });
    }

    /**
     * Load all comments at once
     */
    private void loadAllComments() {
        // Show loading indicator
        btnNextComments.setVisibility(View.GONE);
        btnLoadAll.setVisibility(View.GONE);
        paginationProgressBar.setVisibility(View.VISIBLE);

        sdk.loadAll(new FCCallback<GetCommentsResponseWithPresencePublicComment>() {
            @Override
            public boolean onFailure(APIError error) {
                getHandler().post(() -> {
                    // Hide loading indicator
                    paginationProgressBar.setVisibility(View.GONE);
                    btnNextComments.setVisibility(View.VISIBLE);

                    if (sdk.shouldShowLoadAll()) {
                        btnLoadAll.setVisibility(View.VISIBLE);
                    }

                    // Show error toast
                    android.widget.Toast.makeText(
                            getContext(),
                            R.string.error_loading_comments,
                            android.widget.Toast.LENGTH_SHORT
                    ).show();
                });
                return CONSUME;
            }

            @Override
            public boolean onSuccess(GetCommentsResponseWithPresencePublicComment response) {
                getHandler().post(() -> {
                    // Hide loading indicator and pagination controls
                    paginationProgressBar.setVisibility(View.GONE);
                    paginationControls.setVisibility(View.GONE);
                });
                return CONSUME;
            }
        });
    }
}