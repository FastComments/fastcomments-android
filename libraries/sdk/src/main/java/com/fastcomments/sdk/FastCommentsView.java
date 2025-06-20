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
import com.fastcomments.model.APIEmptyResponse;
import com.fastcomments.model.BlockSuccess;
import com.fastcomments.model.PickFCommentApprovedOrCommentHTML;
import com.fastcomments.model.PublicComment;
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
    private BottomCommentInputView bottomCommentInput;
    private ProgressBar progressBar;
    private TextView emptyStateView;
    private FastCommentsSDK sdk;
    private View paginationControls;
    private Button btnNextComments;
    private Button btnLoadAll;
    private ProgressBar paginationProgressBar;
    private OnBackPressedCallback backPressedCallback;
    private Handler dateUpdateHandler;
    private Runnable dateUpdateRunnable;
    private static final long DATE_UPDATE_INTERVAL = 60000; // Update every minute
    private CommentPostListener commentPostListener;
    private OnReplyClickListener replyClickListener;

    /**
     * Interface for comment post callbacks
     */
    public interface CommentPostListener {
        void onCommentPosted(com.fastcomments.model.PublicComment comment);
    }

    /**
     * Interface for reply button click callbacks
     */
    public interface OnReplyClickListener {
        void onReplyClick(RenderableComment comment);
    }

    /**
     * Set a listener to be notified when a comment is posted
     *
     * @param listener The listener to set
     */
    public void setCommentPostListener(CommentPostListener listener) {
        this.commentPostListener = listener;
    }

    /**
     * Set a listener to be notified when a reply button is clicked
     *
     * @param listener The listener to set
     */
    public void setOnReplyClickListener(OnReplyClickListener listener) {
        this.replyClickListener = listener;
    }

    // Standard View constructors for inflation from XML
    public FastCommentsView(Context context) {
        super(context);
        init(context, null, null);
    }

    public FastCommentsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, null);
    }

    public FastCommentsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, null);
    }

    // Custom constructors with SDK
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
        bottomCommentInput = findViewById(R.id.bottomCommentInput);

        // Initialize pagination controls
        this.paginationControls = findViewById(R.id.paginationControls);
        this.btnNextComments = paginationControls.findViewById(R.id.btnNextComments);
        this.btnLoadAll = paginationControls.findViewById(R.id.btnLoadAll);
        this.paginationProgressBar = paginationControls.findViewById(R.id.paginationProgressBar);


        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);

        // Disable item animations to prevent flicker when clicking items
        recyclerView.setItemAnimator(null);

        // Set up back button handling if in an AppCompatActivity
        if (context instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) context;
            backPressedCallback = new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    handleBackPress();
                }
            };
            activity.getOnBackPressedDispatcher().addCallback(backPressedCallback);
        } else if (context instanceof Activity) {
            // Log a warning that back press won't be handled
            Log.w("FastCommentsView", "Context is an Activity but not AppCompatActivity. Back button handling not supported.");
        }

        // Store the SDK reference
        this.sdk = sdk;

        // If SDK is not provided, postpone adapter initialization
        // It will be initialized when setSDK is called
        if (sdk != null) {
            initializeWithSDK();
        }
    }

    /**
     * Set the SDK instance to use with this view (for use when inflating from XML)
     *
     * @param sdk The FastCommentsSDK instance
     */
    public void setSDK(FastCommentsSDK sdk) {
        // If we already have this SDK instance set, don't reinitialize
        if (this.sdk == sdk) {
            return;
        }

        this.sdk = sdk;
        if (sdk != null) {
            initializeWithSDK();
        }
    }

    /**
     * Initialize the adapter and other SDK-dependent functionality
     */
    private void initializeWithSDK() {
        // Initialize adapter
        adapter = new CommentsAdapter(getContext(), sdk);
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
                        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                        if (layoutManager != null) {
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
                }
            });
        }

        // Set up bottom comment input
        bottomCommentInput.setCurrentUser(sdk.getCurrentUser());
        bottomCommentInput.setSDK(sdk);

        // Setup comment submission listener
        bottomCommentInput.setOnCommentSubmitListener((commentText, parentId) -> {
            postCommentWithMentions(commentText, parentId);
        });

        // Handle reply state changes
        bottomCommentInput.setOnReplyStateChangeListener((isReplying, parentComment) -> {
            // Update any UI state as needed
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
            // Set the bottom input to reply mode
            bottomCommentInput.setReplyingTo(commentToReplyTo);
            bottomCommentInput.requestInputFocus();

            // Notify the reply click listener if set
            if (replyClickListener != null) {
                replyClickListener.onReplyClick(commentToReplyTo);
            }
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

            // Notify adapter to update UI immediately for better UX - use targeted update for performance
            int position = adapter.getPositionForComment(commentToVote);
            if (position != -1) {
                adapter.notifyItemChanged(position);
            } else {
                Log.w("FastCommentsView", "Could not find comment position for vote update, UI may not refresh");
            }

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

                        // Update the UI - use targeted update for performance
                        int position = adapter.getPositionForComment(commentToVote);
                        if (position != -1) {
                            adapter.notifyItemChanged(position);
                        } else {
                            Log.w("FastCommentsView", "Could not find comment position for vote rollback, UI may not refresh");
                        }
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

            // Notify adapter to update UI immediately for better UX - use targeted update for performance
            int position = adapter.getPositionForComment(commentToVote);
            if (position != -1) {
                adapter.notifyItemChanged(position);
            } else {
                Log.w("FastCommentsView", "Could not find comment position for vote update, UI may not refresh");
            }

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

        // Set up listener for new child comments button clicks
        adapter.setNewChildCommentsListener(parentId -> {
            // This method is called when a "Show New Replies" button is clicked
            // scrolling is handled by the CommentsTree
        });

        // Set up comment menu listener for edit, delete, flag, and block actions
        adapter.setCommentMenuListener(new CommentsAdapter.OnCommentMenuItemListener() {
            @Override
            public void onEdit(String commentId, String commentText) {
                // Show edit dialog
                CommentEditDialog dialog = new CommentEditDialog(getContext(), sdk);
                dialog.setOnSaveCallback(newText -> {
                    // Call API to edit the comment
                    sdk.editComment(commentId, newText, new FCCallback<PickFCommentApprovedOrCommentHTML>() {
                        @Override
                        public boolean onFailure(APIError error) {
                            // Show error message
                            getHandler().post(() -> {
                                String errorMessage;
                                if (error.getTranslatedError() != null && !error.getTranslatedError().isEmpty()) {
                                    errorMessage = error.getTranslatedError();
                                } else if (error.getReason() != null && !error.getReason().isEmpty()) {
                                    errorMessage = error.getReason();
                                } else {
                                    errorMessage = getContext().getString(R.string.error_editing_comment);
                                }

                                android.widget.Toast.makeText(
                                        getContext(),
                                        errorMessage,
                                        android.widget.Toast.LENGTH_SHORT
                                ).show();
                            });
                            return CONSUME;
                        }

                        @Override
                        public boolean onSuccess(PickFCommentApprovedOrCommentHTML updatedComment) {
                            // Show success message
                            getHandler().post(() -> {
                                android.widget.Toast.makeText(
                                        getContext(),
                                        R.string.comment_edited_successfully,
                                        android.widget.Toast.LENGTH_SHORT
                                ).show();

                                // Update the comment HTML in the existing comment object
                                RenderableComment renderableComment = sdk.commentsTree.commentsById.get(commentId);
                                if (renderableComment != null) {
                                    renderableComment.getComment().setCommentHTML(updatedComment.getCommentHTML());
                                    adapter.notifyDataSetChanged();
                                }
                            });
                            return CONSUME;
                        }
                    });
                }).show(commentText);
            }

            @Override
            public void onDelete(String commentId) {
                // Confirm before deleting
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
                builder.setTitle(R.string.delete_comment_title)
                        .setMessage(R.string.delete_comment_confirm)
                        .setPositiveButton(R.string.delete, (dialog, which) -> {
                            // Call API to delete the comment
                            sdk.deleteComment(commentId, new FCCallback<APIEmptyResponse>() {
                                @Override
                                public boolean onFailure(APIError error) {
                                    // Show error message
                                    getHandler().post(() -> {
                                        String errorMessage;
                                        if (error.getTranslatedError() != null && !error.getTranslatedError().isEmpty()) {
                                            errorMessage = error.getTranslatedError();
                                        } else if (error.getReason() != null && !error.getReason().isEmpty()) {
                                            errorMessage = error.getReason();
                                        } else {
                                            errorMessage = getContext().getString(R.string.error_deleting_comment);
                                        }

                                        android.widget.Toast.makeText(
                                                getContext(),
                                                errorMessage,
                                                android.widget.Toast.LENGTH_SHORT
                                        ).show();
                                    });
                                    return CONSUME;
                                }

                                @Override
                                public boolean onSuccess(APIEmptyResponse success) {
                                    // Show success message
                                    getHandler().post(() -> {
                                        android.widget.Toast.makeText(
                                                getContext(),
                                                R.string.comment_deleted_successfully,
                                                android.widget.Toast.LENGTH_SHORT
                                        ).show();

                                        // UI is updated from the SDK on success
                                        adapter.notifyDataSetChanged();

                                        // Notify listener that a comment was deleted (to update post stats)
                                        if (commentPostListener != null) {
                                            // We use the same listener as when adding a comment
                                            // since the action needed is the same: update post stats
                                            commentPostListener.onCommentPosted(null);
                                        }
                                    });
                                    return CONSUME;
                                }
                            });
                        })
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                            dialog.dismiss();
                        })
                        .show();
            }

            @Override
            public void onFlag(String commentId) {
                // Call API to flag the comment directly without dialog
                sdk.flagComment(commentId, new FCCallback<APIEmptyResponse>() {
                    @Override
                    public boolean onFailure(APIError error) {
                        // Show error message
                        getHandler().post(() -> {
                            String errorMessage;
                            if (error.getTranslatedError() != null && !error.getTranslatedError().isEmpty()) {
                                errorMessage = error.getTranslatedError();
                            } else if (error.getReason() != null && !error.getReason().isEmpty()) {
                                errorMessage = error.getReason();
                            } else {
                                errorMessage = getContext().getString(R.string.error_flagging_comment);
                            }

                            android.widget.Toast.makeText(
                                    getContext(),
                                    errorMessage,
                                    android.widget.Toast.LENGTH_SHORT
                            ).show();
                        });
                        return CONSUME;
                    }

                    @Override
                    public boolean onSuccess(APIEmptyResponse success) {
                        // Show success message
                        getHandler().post(() -> {
                            android.widget.Toast.makeText(
                                    getContext(),
                                    R.string.comment_flagged_successfully,
                                    android.widget.Toast.LENGTH_SHORT
                            ).show();
                        });
                        return CONSUME;
                    }
                });
            }

            @Override
            public void onBlock(String commentId, String userName) {
                // Confirm before blocking
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
                builder.setTitle(R.string.block_user_title)
                        .setMessage(getContext().getString(R.string.block_user_confirm, userName))
                        .setPositiveButton(R.string.block, (dialog, which) -> {
                            // Call API to block the user
                            sdk.blockUserFromComment(commentId, new FCCallback<BlockSuccess>() {
                                @Override
                                public boolean onFailure(APIError error) {
                                    // Show error message
                                    getHandler().post(() -> {
                                        String errorMessage;
                                        if (error.getTranslatedError() != null && !error.getTranslatedError().isEmpty()) {
                                            errorMessage = error.getTranslatedError();
                                        } else if (error.getReason() != null && !error.getReason().isEmpty()) {
                                            errorMessage = error.getReason();
                                        } else {
                                            errorMessage = getContext().getString(R.string.error_blocking_user);
                                        }

                                        android.widget.Toast.makeText(
                                                getContext(),
                                                errorMessage,
                                                android.widget.Toast.LENGTH_SHORT
                                        ).show();
                                    });
                                    return CONSUME;
                                }

                                @Override
                                public boolean onSuccess(BlockSuccess success) {
                                    // Show success message
                                    getHandler().post(() -> {
                                        android.widget.Toast.makeText(
                                                getContext(),
                                                R.string.user_blocked_successfully,
                                                android.widget.Toast.LENGTH_SHORT
                                        ).show();

                                        // Refresh to remove blocked user's comments
                                        refresh();
                                    });
                                    return CONSUME;
                                }
                            });
                        })
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                            dialog.dismiss();
                        })
                        .show();
            }
        });

        this.sdk = sdk;
    }

    public void load() {
        if (sdk == null) {
            Log.e("FastCommentsView", "Cannot load comments: SDK not set. Call setSDK() first.");
            return;
        }

        showLoading(true);

        sdk.load(new FCCallback<GetCommentsResponseWithPresencePublicComment>() {
            @Override
            public boolean onFailure(APIError error) {
                Log.e("FastCommentsView", error.toString());
                getHandler().post(() -> {
                    showLoading(false);

                    // Use blockingErrorMessage if available, otherwise fallback to a generic message
                    if (sdk.blockingErrorMessage != null && !sdk.blockingErrorMessage.isEmpty()) {
                        setBlockingError(sdk.blockingErrorMessage);
                    } else {
                        // Show standard error as blocking error
                        setBlockingError(getContext().getString(R.string.generic_loading_error));
                    }
                });
                return CONSUME;
            }

            @Override
            public boolean onSuccess(GetCommentsResponseWithPresencePublicComment response) {
                getHandler().post(() -> {
                    showLoading(false);

                    // Clear any blocking error
                    setBlockingError(null);

                    if (bottomCommentInput != null) {
                        bottomCommentInput.setCurrentUser(sdk.getCurrentUser());
                    }

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
     * Post a new comment with mentions support
     *
     * @param commentText Text of the comment
     * @param parentId    Parent comment ID for replies (null for top-level comments)
     */
    public void postCommentWithMentions(String commentText, String parentId) {
        bottomCommentInput.setSubmitting(true);

        // Store reference to the parent comment for resetting UI later
        RenderableComment parentComment = bottomCommentInput.getParentComment();

        // Get any selected mentions from the bottom comment input
        java.util.List<UserMention> mentions = bottomCommentInput.getSelectedMentions();

        sdk.postComment(commentText, parentId, mentions, new FCCallback<PublicComment>() {
            @Override
            public boolean onFailure(APIError error) {
                getHandler().post(() -> {
                    bottomCommentInput.setSubmitting(false);
                    // Check for translated error message
                    String errorMessage;
                    if (error.getTranslatedError() != null && !error.getTranslatedError().isEmpty()) {
                        errorMessage = error.getTranslatedError();
                    } else if (error.getReason() != null && !error.getReason().isEmpty()) {
                        errorMessage = error.getReason();
                    } else {
                        errorMessage = getContext().getString(R.string.error_posting_comment);
                    }

                    bottomCommentInput.showError(errorMessage);
                });
                return CONSUME;
            }

            @Override
            public boolean onSuccess(PublicComment comment) {
                getHandler().post(() -> {
                    bottomCommentInput.setSubmitting(false);
                    bottomCommentInput.clearText();
                    bottomCommentInput.clearReplyState();

                    // Show a toast message to confirm successful posting
                    android.widget.Toast.makeText(
                            getContext(),
                            R.string.comment_posted_successfully,
                            android.widget.Toast.LENGTH_SHORT
                    ).show();

                    // Add the comment to the tree, display immediately
                    sdk.addComment(comment, true);

                    // If this was the first comment, update empty state to show the comments
                    if (sdk.commentsTree.visibleSize() == 1) {
                        setIsEmpty(false);
                    }

                    // Notify listener that a comment has been posted
                    if (commentPostListener != null) {
                        commentPostListener.onCommentPosted(comment);
                    }

                    // Get the position of the newly added comment and scroll to it
                    final RenderableComment renderableComment = sdk.commentsTree.commentsById.get(comment.getId());
                    if (renderableComment != null) {
                        int position = sdk.commentsTree.visibleNodes.indexOf(renderableComment);
                        if (position >= 0) {
                            recyclerView.smoothScrollToPosition(position);
                        }
                    }
                });
                return CONSUME;
            }
        });
    }

    /**
     * Post a new comment (legacy method without mentions support)
     *
     * @param commentText Text of the comment
     * @param parentId    Parent comment ID for replies (null for top-level comments)
     * @deprecated Use postCommentWithMentions instead
     */
    @Deprecated
    public void postComment(String commentText, String parentId) {
        postCommentWithMentions(commentText, parentId);
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
     * Set the view to display a blocking error message
     *
     * @param errorMessage The error message to display, or null to hide the error
     */
    private void setBlockingError(String errorMessage) {
        if (errorMessage != null && !errorMessage.isEmpty()) {
            // Show the error message
            emptyStateView.setText(errorMessage);
            emptyStateView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            paginationControls.setVisibility(View.GONE);
        } else {
            // No error, restore normal visibility based on comments count
            boolean isEmpty = adapter.getItemCount() == 0;
            setIsEmpty(isEmpty);

            // Show or hide pagination controls based on hasMore
            if (sdk.hasMore && !isEmpty) {
                updatePaginationControls();
            } else {
                paginationControls.setVisibility(View.GONE);
            }
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
     * @param commentToVote     The comment being voted on
     * @param needToDelete      Whether an existing vote needs to be deleted first
     * @param originalIsVotedUp The original upvote state before optimistic update
     * @param originalVoteId    The ID of the existing vote, if any
     * @param errorHandler      Callback to handle errors
     * @param username          Username for anonymous voting (null if authenticated)
     * @param email             Email for anonymous voting (null if authenticated)
     * @param toastMessage      Message to show on success
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
     * @param commentToVote       The comment being voted on
     * @param needToDelete        Whether an existing vote needs to be deleted first
     * @param originalIsVotedDown The original downvote state before optimistic update
     * @param originalVoteId      The ID of the existing vote, if any
     * @param errorHandler        Callback to handle errors
     * @param username            Username for anonymous voting (null if authenticated)
     * @param email               Email for anonymous voting (null if authenticated)
     * @param toastMessage        Message to show on success
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
     * Get the UI handler for posting to the main thread
     *
     * @return Handler for UI updates
     */
    public Handler getHandler() {
        if (dateUpdateHandler == null) {
            dateUpdateHandler = new Handler(Looper.getMainLooper());
        }
        return dateUpdateHandler;
    }

    /**
     * Updates the dates for all visible comments
     */
    private void updateDates() {
        // Skip if SDK is not set or absolute dates are enabled
        if (sdk == null || (sdk.getConfig().absoluteDates != null && sdk.getConfig().absoluteDates)) {
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

    /**
     * Proceed with canceling reply after confirmation or if no confirmation needed
     *
     * @param commentId The ID of the comment being replied to
     */
    private void proceedWithCancelReply(String commentId) {
        // Cancel the reply
        bottomCommentInput.clearReplyState();
    }

    /**
     * Updates the reply button text for a specific comment
     *
     * @param commentId  The ID of the comment to update
     * @param isReplying True if the user is replying to this comment, false otherwise
     */
    private void updateReplyButtonText(String commentId, boolean isReplying) {
        if (commentId == null || adapter == null) {
            return;
        }

        // Get the RenderableComment from the comments tree
        RenderableComment comment = sdk.commentsTree.commentsById.get(commentId);
        if (comment == null) {
            return;
        }

        // Get the position of the comment in the adapter
        int position = adapter.getPositionForComment(comment);
        if (position < 0) {
            return;
        }

        // Find the ViewHolder for this position
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (holder instanceof CommentViewHolder) {
            CommentViewHolder commentHolder = (CommentViewHolder) holder;
            // Set the appropriate text based on the replying state
            if (isReplying) {
                commentHolder.replyButton.setText(R.string.cancel_replying);
            } else {
                commentHolder.replyButton.setText(R.string.reply);
            }
        }
    }
    
    /**
     * Handle back button press to warn user before losing comment text
     */
    private void handleBackPress() {
        // Check if we have text in the bottom comment input
        if (bottomCommentInput != null && !bottomCommentInput.isTextEmpty()) {
            // Show confirmation dialog - different message for reply vs new comment
            String title, message;
            RenderableComment parentComment = bottomCommentInput.getParentComment();
            
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
                    // Proceed with back navigation
                    bottomCommentInput.clearText();
                    bottomCommentInput.clearReplyState();
                    // Allow the back press to proceed
                    if (backPressedCallback != null) {
                        backPressedCallback.setEnabled(false);
                        if (getContext() instanceof Activity) {
                            ((Activity) getContext()).onBackPressed();
                        }
                        backPressedCallback.setEnabled(true);
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
        } else {
            // No text, allow normal back navigation
            if (backPressedCallback != null) {
                backPressedCallback.setEnabled(false);
                if (getContext() instanceof Activity) {
                    ((Activity) getContext()).onBackPressed();
                }
                backPressedCallback.setEnabled(true);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cleanup();
    }

    /**
     * Clean up all resources to prevent memory leaks.
     * Call this method when the view will no longer be used.
     */
    public void cleanup() {
        // Stop the timer when the view is detached to prevent memory leaks
        stopDateUpdateTimer();

        // Clear SDK and WebSocket connections
        if (sdk != null) {
            sdk.cleanup();
            sdk = null;
        }

        // Clear listeners
        commentPostListener = null;

        // Clear back pressed callback
        if (backPressedCallback != null) {
            backPressedCallback.setEnabled(false);
            backPressedCallback = null;
        }

        // Clear adapter data through SDK
        if (sdk != null && sdk.commentsTree != null) {
            sdk.commentsTree.clear();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }


        // Clear handler callbacks
        if (dateUpdateHandler != null) {
            dateUpdateHandler.removeCallbacksAndMessages(null);
        }

        // Clear runnables
        dateUpdateRunnable = null;
    }

    /**
     * Returns the RecyclerView adapter used by this view.
     *
     * @return The CommentsAdapter instance
     */
    public CommentsAdapter getAdapter() {
        return adapter;
    }

    /**
     * Clears all comments from the adapter.
     * Use this method when switching fragments to avoid memory leaks.
     */
    public void clearAdapter() {
        if (adapter != null && sdk != null && sdk.commentsTree != null) {
            sdk.commentsTree.clear();
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * Gets the bottom comment input view
     *
     * @return The bottom comment input view
     */
    public BottomCommentInputView getBottomCommentInput() {
        return bottomCommentInput;
    }

    /**
     * Checks if the bottom comment input is currently in reply mode
     *
     * @return true if replying to a comment, false otherwise
     */
    public boolean isReplyingToComment() {
        return bottomCommentInput != null &&
                bottomCommentInput.getParentComment() != null;
    }
}