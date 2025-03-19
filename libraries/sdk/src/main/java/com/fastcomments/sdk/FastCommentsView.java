package com.fastcomments.sdk;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;

import com.fastcomments.core.VoteStyle;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fastcomments.model.APIError;
import com.fastcomments.model.GetCommentsResponseWithPresencePublicComment;
import com.fastcomments.model.VoteResponse;
import com.fastcomments.model.VoteDeleteResponse;
import com.fastcomments.model.ImportedAPIStatusFAILED;

import java.util.Objects;

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
    private Handler dateUpdateHandler;
    private Runnable dateUpdateRunnable;
    private static final long DATE_UPDATE_INTERVAL = 60000; // Update every minute

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
        
        // Initialize the date update handler and runnable
        dateUpdateHandler = new Handler(Looper.getMainLooper());
        dateUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateDates();
                // Schedule the next update
                dateUpdateHandler.postDelayed(this, DATE_UPDATE_INTERVAL);
            }
        };

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

        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new CommentsAdapter(context, sdk);
        recyclerView.setAdapter(adapter);
        
        // Set up infinite scrolling if enabled in config
        boolean isInfiniteScrollingEnabled = sdk.getConfig().enableInfiniteScrolling != null && 
                                           sdk.getConfig().enableInfiniteScrolling;
        
        if (isInfiniteScrollingEnabled) {
            recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    
                    if (dy > 0) { // Scrolling down
                        int visibleItemCount = layoutManager.getChildCount();
                        int totalItemCount = layoutManager.getItemCount();
                        int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                        
                        // Load more when user is near the end (last 5 items)
                        if ((visibleItemCount + firstVisibleItemPosition + 5) >= totalItemCount
                                && sdk.hasMore) {
                            loadMoreComments();
                        }
                    }
                }
            });
        }

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
            
            // Check vote style for appropriate message
            final boolean isHeartStyle = Objects.equals(sdk.getConfig().voteStyle, VoteStyle.Heart);
            
            // Toggle the vote state
            boolean needToDelete = false;
            if (originalIsVotedUp != null && originalIsVotedUp) {
                // User already upvoted, so remove the vote
                commentToVote.getComment().setIsVotedUp(false);
                // Update vote count
                Integer votesUp = commentToVote.getComment().getVotesUp();
                if (votesUp != null && votesUp > 0) {
                    commentToVote.getComment().setVotesUp(votesUp - 1);
                }
                if (isHeartStyle) {
                    toastMessage = getContext().getString(R.string.you_removed_like, commenterName);
                } else {
                    toastMessage = getContext().getString(R.string.you_removed_upvote, commenterName);
                }
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
                if (isHeartStyle) {
                    toastMessage = getContext().getString(R.string.you_liked, commenterName);
                } else {
                    toastMessage = getContext().getString(R.string.you_upvoted, commenterName);
                }
            }
            
            // Notify adapter to update UI immediately for better UX
            adapter.notifyDataSetChanged();
            
            // Store toast message for display after successful API call
            final String finalToastMessage = toastMessage;
            
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

            // Check if user is logged in or if anonymous votes are allowed
            boolean userIsLoggedIn = sdk.getCurrentUser() != null &&
                    sdk.getCurrentUser().getAuthorized() != null &&
                    sdk.getCurrentUser().getAuthorized();
            boolean allowAnonVotes = sdk.getConfig() != null &&
                    Boolean.TRUE.equals(sdk.getConfig().allowAnonVotes);

            // Special handling if the user is not logged in
            if (!userIsLoggedIn) {
                if (!allowAnonVotes) {
                    final boolean needToDeleteFinal = needToDelete;
                    // Show dialog to collect user info for votes that need verification
                    UserLoginDialog.show(getContext(), sdk.getConfig(), "vote", new UserLoginDialog.OnUserCredentialsListener() {
                        @Override
                        public void onUserCredentialsEntered(String username, String email) {
                            // Proceed with vote using provided credentials
                            processUpvote(commentToVote, needToDeleteFinal, originalIsVotedUp, originalVoteId,
                                    upvoteErrorHandler, username, email, finalToastMessage);
                        }

                        @Override
                        public void onCancel() {
                            // User canceled, revert UI state
                            commentToVote.getComment().setIsVotedUp(originalIsVotedUp);
                            commentToVote.getComment().setIsVotedDown(originalIsVotedDown);
                            commentToVote.getComment().setVotesUp(originalVotesUp);
                            commentToVote.getComment().setVotesDown(originalVotesDown);
                            adapter.notifyDataSetChanged();
                        }
                    });
                } else {
                    // Anonymous votes allowed, proceed directly without dialog
                    final boolean needToDeleteFinal = needToDelete;
                    processUpvote(commentToVote, needToDeleteFinal, originalIsVotedUp, originalVoteId,
                            upvoteErrorHandler, null, null, finalToastMessage);
                }
            } else {
                // User is logged in, proceed with vote
                processUpvote(commentToVote, needToDelete, originalIsVotedUp, originalVoteId,
                        upvoteErrorHandler, null, null, finalToastMessage);
            }
        });
        
        // Handle downvote requests
        adapter.setDownVoteListener((commentToVote) -> {
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
            
            // Store toast message for display after successful API call
            final String finalToastMessage = toastMessage;
            
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
            
            // Check if user is logged in or if anonymous votes are allowed
            boolean userIsLoggedIn = sdk.getCurrentUser() != null &&
                    sdk.getCurrentUser().getAuthorized() != null &&
                    sdk.getCurrentUser().getAuthorized();
            boolean allowAnonVotes = sdk.getConfig() != null &&
                    Boolean.TRUE.equals(sdk.getConfig().allowAnonVotes);
                                    
            // Special handling if the user is not logged in
            if (!userIsLoggedIn) {
                if (!allowAnonVotes) {
                    final boolean needToDeleteFinal = needToDelete;
                    // Show dialog to collect user info for votes that need verification
                    UserLoginDialog.show(getContext(), sdk.getConfig(), "vote", new UserLoginDialog.OnUserCredentialsListener() {
                        @Override
                        public void onUserCredentialsEntered(String username, String email) {
                            // Proceed with vote using provided credentials
                            processDownvote(commentToVote, needToDeleteFinal, originalIsVotedDown, originalVoteId,
                                    downvoteErrorHandler, username, email, finalToastMessage);
                        }
                        
                        @Override
                        public void onCancel() {
                            // User canceled, revert UI state
                            commentToVote.getComment().setIsVotedUp(originalIsVotedUp);
                            commentToVote.getComment().setIsVotedDown(originalIsVotedDown);
                            commentToVote.getComment().setVotesUp(originalVotesUp);
                            commentToVote.getComment().setVotesDown(originalVotesDown);
                            adapter.notifyDataSetChanged();
                        }
                    });
                } else {
                    // Anonymous votes allowed, proceed directly without dialog
                    final boolean needToDeleteFinal = needToDelete;
                    processDownvote(commentToVote, needToDeleteFinal, originalIsVotedDown, originalVoteId,
                            downvoteErrorHandler, null, null, finalToastMessage);
                }
            } else {
                // User is logged in, proceed with vote
                processDownvote(commentToVote, needToDelete, originalIsVotedDown, originalVoteId,
                        downvoteErrorHandler, null, null, finalToastMessage);
            }
        });

        adapter.setGetChildrenProducer((request, sendResults) -> {
            String parentId = request.getParentId();
            Button toggleButton = request.getToggleButton();
            Integer skip = request.getSkip();
            Integer limit = request.getLimit();
            boolean isLoadMore = request.isLoadMore();
            
            // Get the parent comment
            RenderableComment parentComment = sdk.commentsTree.commentsById.get(parentId);
            
            // If this is a pagination request, use the comment's pagination state
            if (parentComment != null) {
                if (skip == null) {
                    skip = isLoadMore ? parentComment.childSkip : 0;
                }
                
                if (limit == null) {
                    limit = parentComment.childPageSize;
                }
                
                // Update the skip value for subsequent requests
                if (isLoadMore) {
                    parentComment.childPage++;
                    parentComment.childSkip += parentComment.childPageSize;
                }
                
                // Mark as loading to update UI
                parentComment.isLoadingChildren = true;
            }

            // Set the button text to "Loading..." when starting to load
            if (toggleButton != null && !isLoadMore) {
                getHandler().post(() -> toggleButton.setText(R.string.loading_replies));
            }

            sdk.getCommentsForParent(skip, limit, 0, parentId, new FCCallback<GetCommentsResponseWithPresencePublicComment>() {
                @Override
                public boolean onFailure(APIError error) {
                    getHandler().post(() -> {
                        // If we have a parent comment, update its loading state
                        if (parentComment != null) {
                            parentComment.isLoadingChildren = false;
                            
                            // Revert pagination state on failure
                            if (isLoadMore) {
                                parentComment.childPage--;
                                parentComment.childSkip -= parentComment.childPageSize;
                            }
                        }
                        
                        // Show toast with error message
                        android.widget.Toast.makeText(
                                getContext(),
                                R.string.error_loading_replies,
                                android.widget.Toast.LENGTH_SHORT
                        ).show();

                        // Reset button text if the toggle button is available
                        if (toggleButton != null && !isLoadMore) {
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
                    getHandler().post(() -> {
                        // If we have a parent comment, update its state
                        if (parentComment != null) {
                            parentComment.isLoadingChildren = false;
                            parentComment.hasMoreChildren = response.getHasMore() != null ? response.getHasMore() : false;
                        }
                        
                        sendResults.call(response.getComments());
                    });
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
                        
                        // Start the date update timer
                        startDateUpdateTimer();
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
        
        // Hide pagination controls while commenting
        if (paginationControls.getVisibility() == View.VISIBLE) {
            paginationControls.startAnimation(fadeOutAnimation);
            paginationControls.setVisibility(View.GONE);
        }

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
        
        // Restore pagination controls if needed
        if (sdk.hasMore && adapter.getItemCount() > 0) {
            updatePaginationControls();
            if (paginationControls.getVisibility() == View.GONE && 
                    (sdk.getCountRemainingToShow() > 0 || sdk.shouldShowLoadAll())) {
                paginationControls.startAnimation(fadeInAnimation);
                paginationControls.setVisibility(View.VISIBLE);
            }
        }

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
        // Check if infinite scrolling is enabled
        boolean isInfiniteScrollingEnabled = sdk.getConfig().enableInfiniteScrolling != null && 
                                           sdk.getConfig().enableInfiniteScrolling;
        
        if (isInfiniteScrollingEnabled) {
            // Hide pagination controls when infinite scrolling is enabled
            paginationControls.setVisibility(View.GONE);
            return;
        }
        
        // Standard pagination controls logic for when infinite scrolling is disabled
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
        // Check if we're already loading
        if (paginationProgressBar.getVisibility() == View.VISIBLE) {
            return;
        }
        
        // Check if infinite scrolling is enabled
        boolean isInfiniteScrollingEnabled = sdk.getConfig().enableInfiniteScrolling != null && 
                                           sdk.getConfig().enableInfiniteScrolling;
        
        // Show loading indicator
        if (isInfiniteScrollingEnabled) {
            // In infinite scrolling mode, the pagination controls are hidden
            // but we still need to show a loading indicator - use the paginationProgressBar
            // but make it visible at the bottom of the comment list
            paginationControls.setVisibility(View.VISIBLE);
            btnNextComments.setVisibility(View.GONE);
            btnLoadAll.setVisibility(View.GONE);
            paginationProgressBar.setVisibility(View.VISIBLE);
        } else {
            // Standard pagination loading UI
            btnNextComments.setVisibility(View.GONE);
            btnLoadAll.setVisibility(View.GONE);
            paginationProgressBar.setVisibility(View.VISIBLE);
        }

        sdk.loadMore(new FCCallback<GetCommentsResponseWithPresencePublicComment>() {
            @Override
            public boolean onFailure(APIError error) {
                getHandler().post(() -> {
                    // Hide loading indicator
                    paginationProgressBar.setVisibility(View.GONE);
                    
                    // Check if infinite scrolling is enabled
                    boolean isInfiniteScrollingEnabled = sdk.getConfig().enableInfiniteScrolling != null && 
                                                       sdk.getConfig().enableInfiniteScrolling;
                    
                    if (isInfiniteScrollingEnabled) {
                        // For infinite scrolling, hide the pagination controls on error
                        paginationControls.setVisibility(View.GONE);
                    } else {
                        // For standard pagination, restore buttons
                        btnNextComments.setVisibility(View.VISIBLE);
                        if (sdk.shouldShowLoadAll()) {
                            btnLoadAll.setVisibility(View.VISIBLE);
                        }
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
                    
                    // Check if infinite scrolling is enabled
                    boolean isInfiniteScrollingEnabled = sdk.getConfig().enableInfiniteScrolling != null && 
                                                       sdk.getConfig().enableInfiniteScrolling;
                    
                    if (isInfiniteScrollingEnabled) {
                        // For infinite scrolling, hide the pagination controls again
                        paginationControls.setVisibility(View.GONE);
                    } else {
                        // For standard pagination, update pagination controls
                        updatePaginationControls();
                    }
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
                    // Hide loading indicator
                    paginationProgressBar.setVisibility(View.GONE);
                    paginationControls.setVisibility(View.GONE);
                });
                return CONSUME;
            }
        });
    }
    
    /**
     * Helper method for processing upvotes with or without anonymous credentials
     * 
     * @param commentToVote The comment being voted on
     * @param needToDelete Whether an existing vote needs to be deleted first
     * @param originalIsVotedUp The original upvote state before optimistic update
     * @param originalVoteId The ID of the existing vote, if any
     * @param errorHandler Callback to handle errors
     * @param username Username for anonymous voting (null if authenticated)
     * @param email Email for anonymous voting (null if authenticated)
     * @param toastMessage Message to show on success
     */
    private void processUpvote(RenderableComment commentToVote, boolean needToDelete, 
                              Boolean originalIsVotedUp, String originalVoteId,
                              FCCallback<APIError> errorHandler, String username, String email, String toastMessage) {
        if (needToDelete && originalVoteId != null) {
            // Delete the existing vote first
            sdk.deleteCommentVote(commentToVote.getComment().getId(), originalVoteId, username, email, 
                    new FCCallback<VoteDeleteResponse>() {
                @Override
                public boolean onFailure(APIError error) {
                    return errorHandler.onSuccess(error);
                }
                
                @Override
                public boolean onSuccess(VoteDeleteResponse response) {
                    // Clear vote ID since it was deleted
                    commentToVote.getComment().setMyVoteId(null);
                    
                    // If removing an upvote, we're done - show success message
                    if (originalIsVotedUp != null && originalIsVotedUp) {
                        // Show success toast message for removing vote
                        getHandler().post(() -> {
                            android.widget.Toast.makeText(
                                    getContext(),
                                    toastMessage,
                                    android.widget.Toast.LENGTH_SHORT
                            ).show();
                        });
                        return CONSUME;
                    }
                    
                    // Otherwise, we need to add a new upvote
                    sdk.voteComment(commentToVote.getComment().getId(), true, username, email, 
                            new FCCallback<VoteResponse>() {
                        @Override
                        public boolean onFailure(APIError error) {
                            return errorHandler.onSuccess(error);
                        }
                        
                        @Override
                        public boolean onSuccess(VoteResponse response) {
                            // Store new vote ID
                            commentToVote.getComment().setMyVoteId(response.getVoteId());
                            
                            // Show success toast message
                            getHandler().post(() -> {
                                android.widget.Toast.makeText(
                                        getContext(),
                                        toastMessage,
                                        android.widget.Toast.LENGTH_SHORT
                                ).show();
                            });
                            
                            return CONSUME;
                        }
                    });
                    
                    return CONSUME;
                }
            });
        } else if (originalIsVotedUp == null || !originalIsVotedUp) {
            // No existing vote to delete or it's a downvote being converted to upvote
            // Just add a new upvote
            sdk.voteComment(commentToVote.getComment().getId(), true, username, email, 
                    new FCCallback<VoteResponse>() {
                @Override
                public boolean onFailure(APIError error) {
                    return errorHandler.onSuccess(error);
                }
                
                @Override
                public boolean onSuccess(VoteResponse response) {
                    // Store new vote ID
                    commentToVote.getComment().setMyVoteId(response.getVoteId());
                    
                    // Show success toast message
                    getHandler().post(() -> {
                        android.widget.Toast.makeText(
                                getContext(),
                                toastMessage,
                                android.widget.Toast.LENGTH_SHORT
                        ).show();
                    });
                    
                    return CONSUME;
                }
            });
        }
    }
    
    /**
     * Helper method for processing downvotes with or without anonymous credentials
     * 
     * @param commentToVote The comment being voted on
     * @param needToDelete Whether an existing vote needs to be deleted first
     * @param originalIsVotedDown The original downvote state before optimistic update
     * @param originalVoteId The ID of the existing vote, if any
     * @param errorHandler Callback to handle errors
     * @param username Username for anonymous voting (null if authenticated)
     * @param email Email for anonymous voting (null if authenticated)
     * @param toastMessage Message to show on success
     */
    private void processDownvote(RenderableComment commentToVote, boolean needToDelete, 
                                Boolean originalIsVotedDown, String originalVoteId,
                                FCCallback<APIError> errorHandler, String username, String email, String toastMessage) {
        if (needToDelete && originalVoteId != null) {
            // Delete the existing vote first
            sdk.deleteCommentVote(commentToVote.getComment().getId(), originalVoteId, username, email, 
                    new FCCallback<VoteDeleteResponse>() {
                @Override
                public boolean onFailure(APIError error) {
                    return errorHandler.onSuccess(error);
                }
                
                @Override
                public boolean onSuccess(VoteDeleteResponse response) {
                    // Clear vote ID since it was deleted
                    commentToVote.getComment().setMyVoteId(null);
                    
                    // If removing a downvote, we're done - show success message
                    if (originalIsVotedDown != null && originalIsVotedDown) {
                        // Show success toast message for removing vote
                        getHandler().post(() -> {
                            android.widget.Toast.makeText(
                                    getContext(),
                                    toastMessage,
                                    android.widget.Toast.LENGTH_SHORT
                            ).show();
                        });
                        return CONSUME;
                    }
                    
                    // Otherwise, we need to add a new downvote
                    sdk.voteComment(commentToVote.getComment().getId(), false, username, email, 
                            new FCCallback<VoteResponse>() {
                        @Override
                        public boolean onFailure(APIError error) {
                            return errorHandler.onSuccess(error);
                        }
                        
                        @Override
                        public boolean onSuccess(VoteResponse response) {
                            // Store new vote ID
                            commentToVote.getComment().setMyVoteId(response.getVoteId());
                            
                            // Show success toast message
                            getHandler().post(() -> {
                                android.widget.Toast.makeText(
                                        getContext(),
                                        toastMessage,
                                        android.widget.Toast.LENGTH_SHORT
                                ).show();
                            });
                            
                            return CONSUME;
                        }
                    });
                    
                    return CONSUME;
                }
            });
        } else if (originalIsVotedDown == null || !originalIsVotedDown) {
            // No existing vote to delete or it's an upvote being converted to downvote
            // Just add a new downvote
            sdk.voteComment(commentToVote.getComment().getId(), false, username, email, 
                    new FCCallback<VoteResponse>() {
                @Override
                public boolean onFailure(APIError error) {
                    return errorHandler.onSuccess(error);
                }
                
                @Override
                public boolean onSuccess(VoteResponse response) {
                    // Store new vote ID
                    commentToVote.getComment().setMyVoteId(response.getVoteId());
                    
                    // Show success toast message
                    getHandler().post(() -> {
                        android.widget.Toast.makeText(
                                getContext(),
                                toastMessage,
                                android.widget.Toast.LENGTH_SHORT
                        ).show();
                    });
                    
                    return CONSUME;
                }
            });
        }
    }
    
    /**
     * Starts the timer for updating relative dates
     */
    private void startDateUpdateTimer() {
        // Remove any existing callbacks to avoid duplicates
        stopDateUpdateTimer();
        
        // Only start the timer if we have comments and absolute dates are not enabled
        if (adapter.getItemCount() > 0 && (sdk.getConfig().absoluteDates == null || !sdk.getConfig().absoluteDates)) {
            dateUpdateHandler.postDelayed(dateUpdateRunnable, DATE_UPDATE_INTERVAL);
        }
    }
    
    /**
     * Stops the timer for updating relative dates
     */
    private void stopDateUpdateTimer() {
        dateUpdateHandler.removeCallbacks(dateUpdateRunnable);
    }
    
    /**
     * Updates the dates for all visible comments
     */
    private void updateDates() {
        // Skip if absolute dates are enabled
        if (sdk.getConfig().absoluteDates != null && sdk.getConfig().absoluteDates) {
            return;
        }
        
        // Get visible position range
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager != null) {
            int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
            int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
            
            // Update only visible items to avoid unnecessary work
            for (int i = firstVisiblePosition; i <= lastVisiblePosition; i++) {
                RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(i);
                if (holder instanceof CommentViewHolder) {
                    ((CommentViewHolder) holder).updateDateDisplay();
                }
            }
        }
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Stop the timer when the view is detached to prevent memory leaks
        stopDateUpdateTimer();
        // Clean up WebSocket connections when the view is detached
        if (sdk != null) {
            sdk.cleanup();
        }
    }
}