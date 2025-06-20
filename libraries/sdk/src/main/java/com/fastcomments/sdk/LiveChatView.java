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

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.core.VoteStyle;
import com.fastcomments.model.APIEmptyResponse;
import com.fastcomments.model.BlockSuccess;
import com.fastcomments.model.PickFCommentApprovedOrCommentHTML;
import com.fastcomments.model.PublicComment;
import com.fastcomments.model.SortDirections;
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

import java.util.ArrayList;
import java.util.Objects;

/**
 * A specialized view for live chat-style commenting, where:
 * - Comments show oldest to newest
 * - Scrolling is pinned to the bottom by default
 * - Focused on real-time interaction
 */
public class LiveChatView extends FrameLayout {

    private RecyclerView recyclerView;
    private CommentsAdapter adapter;
    private CommentFormView commentForm;
    private BottomCommentInputView bottomCommentInput;
    private ProgressBar progressBar;
    private TextView emptyStateView;
    private FastCommentsSDK sdk;
    private FrameLayout commentFormContainer;
    private View paginationControls;
    private Button btnNextComments;
    private Button btnLoadAll;
    private ProgressBar paginationProgressBar;
    private OnBackPressedCallback backPressedCallback;
    private Handler dateUpdateHandler;
    private Runnable dateUpdateRunnable;
    private static final long DATE_UPDATE_INTERVAL = 60000; // Update every minute
    private boolean autoScrollToBottom = true;
    private LinearLayoutManager layoutManager;

    /**
     * Interface for comment action callbacks
     */
    public interface CommentActionListener {
        /**
         * Called when a comment is posted
         * @param comment The posted comment
         */
        void onCommentPosted(com.fastcomments.model.PublicComment comment);
        
        /**
         * Called when a comment is deleted
         * @param commentId The ID of the deleted comment
         */
        void onCommentDeleted(String commentId);
    }
    
    private CommentActionListener commentActionListener;
    private OnUserClickListener userClickListener;
    
    /**
     * Set a listener to be notified of comment actions
     * @param listener The listener to set
     */
    public void setCommentActionListener(CommentActionListener listener) {
        this.commentActionListener = listener;
    }
    
    /**
     * Set a listener to be notified when a user (name or avatar) is clicked
     * @param listener The listener to set
     */
    public void setOnUserClickListener(OnUserClickListener listener) {
        this.userClickListener = listener;
        // Update existing adapter if it's already created
        if (adapter != null) {
            adapter.setUserClickListener(listener);
        }
    }

    // Standard View constructors for inflation from XML
    public LiveChatView(Context context) {
        super(context);
        init(context, null, null);
    }

    public LiveChatView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, null);
    }

    public LiveChatView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, null);
    }

    // Custom constructors with SDK
    public LiveChatView(Context context, FastCommentsSDK sdk) {
        super(context);
        init(context, null, sdk);
    }

    public LiveChatView(Context context, AttributeSet attrs, FastCommentsSDK sdk) {
        super(context, attrs);
        init(context, attrs, sdk);
    }

    public LiveChatView(Context context, AttributeSet attrs, int defStyleAttr, FastCommentsSDK sdk) {
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
        // Note: LiveChatView should be updated to use BottomCommentInputView in a future update

        // Initialize pagination controls
        this.paginationControls = findViewById(R.id.paginationControls);
        this.btnNextComments = paginationControls.findViewById(R.id.btnNextComments);
        this.btnLoadAll = paginationControls.findViewById(R.id.btnLoadAll);
        this.paginationProgressBar = paginationControls.findViewById(R.id.paginationProgressBar);

        // Find the bottom comment input
        bottomCommentInput = findViewById(R.id.bottomCommentInput);
        if (bottomCommentInput != null) {
            // Use bottom comment input for live chat
            commentForm = null; // We'll use bottomCommentInput instead
        } else {
            // Fallback for layouts without bottom input
            commentForm = new CommentFormView(context);
        }

        // Bottom input is always visible for live chat

        // Set up back button handling if in an AppCompatActivity
        if (context instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) context;
            backPressedCallback = new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    // Check if we have text in either input method
                    boolean hasText = false;
                    RenderableComment parentComment = null;
                    
                    if (bottomCommentInput != null) {
                        hasText = !bottomCommentInput.isTextEmpty();
                        parentComment = bottomCommentInput.getParentComment();
                    } else if (commentForm != null) {
                        hasText = !commentForm.isTextEmpty();
                        parentComment = commentForm.getParentComment();
                    }
                    
                    if (hasText) {
                        // Show confirmation dialog - different message for reply vs new comment
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
                                if (bottomCommentInput != null) {
                                    bottomCommentInput.clearText();
                                    bottomCommentInput.clearReplyState();
                                } else if (commentForm != null) {
                                    commentForm.resetReplyState();
                                }
                            })
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                    } else {
                        // Text is empty, no need for confirmation
                        if (bottomCommentInput != null) {
                            bottomCommentInput.clearReplyState();
                        } else if (commentForm != null) {
                            commentForm.resetReplyState();
                        }
                    }
                }
            };
            activity.getOnBackPressedDispatcher().addCallback(backPressedCallback);
        } else if (context instanceof Activity) {
            // Log a warning that back press won't be handled
            Log.w("LiveChatView", "Context is an Activity but not AppCompatActivity. Back button handling not supported.");
        }

        // Create a LinearLayoutManager for bottom-up layout
        layoutManager = new LinearLayoutManager(context);
        layoutManager.setStackFromEnd(true); // Stack from end for chat-like behavior
        recyclerView.setLayoutManager(layoutManager);

        // Add scroll listener to detect when user manually scrolls
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    // User is manually scrolling, temporarily disable auto-scroll
                    autoScrollToBottom = false;
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // Check if we're at the bottom when scrolling stops
                    if (layoutManager.findLastVisibleItemPosition() == adapter.getItemCount() - 1) {
                        // If user scrolled to bottom, re-enable auto-scroll
                        autoScrollToBottom = true;
                    }
                }
            }
        });
        
        // Store the SDK reference
        this.sdk = sdk;
        
        // If SDK is not provided, postpone adapter initialization
        // It will be initialized when setSDK is called
        if (sdk != null) {
            initializeWithSDK();
        }
    }

    public static void setupLiveChatConfig(CommentWidgetConfig config) {
        config.showLiveRightAway = true;
        config.defaultSortDirection = SortDirections.OF;
        config.maxReplyDepth = 0;
        config.disableVoting = true;
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
            setupDemoBanner();
        }
    }
    
    /**
     * Initialize the adapter and other SDK-dependent functionality
     */
    private void initializeWithSDK() {
        // Initialize adapter
        adapter = new CommentsAdapter(getContext(), sdk);
        recyclerView.setAdapter(adapter);
        
        // Enable live chat style in the comment tree
        sdk.commentsTree.setLiveChatStyle(true);
        
        // Set up infinite scrolling (in reverse) for chat mode
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                // Check if we need to load more older messages when scrolling up
                if (dy < 0) { // Scrolling up
                    if (layoutManager.findFirstVisibleItemPosition() <= 5 && sdk.hasMore) {
                        loadMoreComments();
                    }
                }
            }
        });

        // Setup form listeners based on which input method is available
        if (bottomCommentInput != null) {
            // Setup bottom comment input for live chat
            bottomCommentInput.setCurrentUser(sdk.getCurrentUser());
            bottomCommentInput.setSDK(sdk);
            
            bottomCommentInput.setOnCommentSubmitListener((commentText, parentId) -> {
                postComment(commentText, parentId);
            });
            
            bottomCommentInput.setOnReplyStateChangeListener((isReplying, parentComment) -> {
                // Handle reply state changes if needed
            });
        } else if (commentForm != null) {
            // Setup traditional comment form as fallback
            commentForm.setOnCommentSubmitListener((commentText, parentId) -> {
                postComment(commentText, parentId);
            });

            commentForm.setOnCancelReplyListener(() -> {
                // Get the parent comment
                RenderableComment parentComment = commentForm.getParentComment();
                if (parentComment != null && !commentForm.isTextEmpty()) {
                    // Show confirmation dialog
                    new android.app.AlertDialog.Builder(getContext())
                        .setTitle(R.string.cancel_reply_title)
                        .setMessage(R.string.cancel_reply_confirm)
                        .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                            // Proceed with cancellation
                            commentForm.resetReplyState();
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .show();
                } else {
                    // Just reset the form if there's no text
                    commentForm.resetReplyState();
                }
            });
        }

        // For chat view, we primarily use infinite scrolling, but keep pagination
        // buttons just in case
        btnNextComments.setOnClickListener(v -> {
            loadMoreComments();
        });

        btnLoadAll.setOnClickListener(v -> {
            loadAllComments();
        });

        // Handle reply requests from comments
        adapter.setRequestingReplyListener((commentToReplyTo) -> {
            // Set reply mode on the appropriate input method
            if (bottomCommentInput != null) {
                bottomCommentInput.setReplyingTo(commentToReplyTo);
                bottomCommentInput.requestInputFocus();
            } else if (commentForm != null) {
                commentForm.setReplyingTo(commentToReplyTo);
            }
            
            // Scroll to the comment being replied to
            int pos = adapter.getPositionForComment(commentToReplyTo);
            if (pos >= 0) {
                recyclerView.smoothScrollToPosition(pos);
            }
        });

        // Set up all the vote handlers similar to FastCommentsView
        setupVoteHandlers();
        
        // Set up comment menu listener
        setupCommentMenuListener();
        
        // Handle user click events
        adapter.setUserClickListener(userClickListener);
        
        // The SDK supports loading child comments, but we don't need to show
        // them in the chat view as they appear as top-level messages
        adapter.setGetChildrenProducer((request, sendResults) -> {
            // Simply return an empty list since we don't show threaded replies
            sendResults.call(new ArrayList<>());
        });
        
        // Set SDK on the appropriate input method for mentions functionality
        if (commentForm != null) {
            commentForm.setSDK(sdk);
        }
        // Note: bottomCommentInput SDK is already set above
    }

    private void setupVoteHandlers() {
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

        // Set up downvote handler (similar to upvote handler)
        adapter.setDownVoteListener((commentToVote) -> {
            // The implementation is similar to upvote handler
            // For brevity, just calling the appropriate method
            handleDownVote(commentToVote);
        });
        
        // Set up heart click handler (using same logic as upvote)
//        adapter.setHeartClickListener = adapter.setUpVoteClickListener;
    }
    
    private void handleDownVote(RenderableComment commentToVote) {
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
    }

    private void setupCommentMenuListener() {
        // Set up comment menu listener for edit, flag, and block actions
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
                                        
                                        // Notify listener if set
                                        if (commentActionListener != null) {
                                            commentActionListener.onCommentDeleted(commentId);
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
    }

    public void load() {
        if (sdk == null) {
            Log.e("LiveChatView", "Cannot load comments: SDK not set. Call setSDK() first.");
            return;
        }
        
        showLoading(true);

        sdk.load(new FCCallback<GetCommentsResponseWithPresencePublicComment>() {
            @Override
            public boolean onFailure(APIError error) {
                Log.e("LiveChatView", error.toString());
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
                        
                        // For chat view, always scroll to bottom after loading
                        if (autoScrollToBottom) {
                            scrollToBottom();
                        }
                    }
                    
                    // Initialize the input with current user info
                    if (bottomCommentInput != null) {
                        bottomCommentInput.setCurrentUser(sdk.getCurrentUser());
                    } else if (commentForm != null) {
                        commentForm.setCurrentUser(sdk.getCurrentUser());
                    }
                });
                return CONSUME;
            }
        });
    }

    /**
     * Scroll to the bottom of the chat
     */
    public void scrollToBottom() {
        if (adapter != null && adapter.getItemCount() > 0) {
            recyclerView.post(() -> {
                recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
            });
        }
    }

    /**
     * Post a new comment
     *
     * @param commentText Text of the comment
     * @param parentId    Parent comment ID for replies (null for top-level comments)
     */
    public void postComment(String commentText, String parentId) {
        // Set submitting state on the appropriate input method
        if (bottomCommentInput != null) {
            bottomCommentInput.setSubmitting(true);
        } else if (commentForm != null) {
            commentForm.setSubmitting(true);
        }

        sdk.postComment(commentText, parentId, new FCCallback<PublicComment>() {
            @Override
            public boolean onFailure(APIError error) {
                getHandler().post(() -> {
                    // Reset submitting state
                    if (bottomCommentInput != null) {
                        bottomCommentInput.setSubmitting(false);
                    } else if (commentForm != null) {
                        commentForm.setSubmitting(false);
                    }
                    
                    // Check for translated error message
                    String errorMessage;
                    if (error.getTranslatedError() != null && !error.getTranslatedError().isEmpty()) {
                        errorMessage = error.getTranslatedError();
                    } else if (error.getReason() != null && !error.getReason().isEmpty()) {
                        errorMessage = error.getReason();
                    } else {
                        errorMessage = getContext().getString(R.string.error_posting_comment);
                    }

                    // Show error on the appropriate input method
                    if (bottomCommentInput != null) {
                        bottomCommentInput.showError(errorMessage);
                    } else if (commentForm != null) {
                        commentForm.showError(errorMessage);
                    }
                });
                return CONSUME;
            }

            @Override
            public boolean onSuccess(PublicComment comment) {
                getHandler().post(() -> {
                    // Reset state on the appropriate input method
                    if (bottomCommentInput != null) {
                        bottomCommentInput.setSubmitting(false);
                        bottomCommentInput.clearText();
                        bottomCommentInput.clearReplyState();
                    } else if (commentForm != null) {
                        commentForm.setSubmitting(false);
                        commentForm.clearText();
                        commentForm.resetReplyState();
                    }

                    // Show a toast message to confirm successful posting
                    android.widget.Toast.makeText(
                            getContext(),
                            R.string.comment_posted_successfully,
                            android.widget.Toast.LENGTH_SHORT
                    ).show();

                    // Add the comment to the tree, display immediately
                    // Note: For LiveChatView, comments will be added at the bottom because
                    // we've set the sort direction to OLDEST_FIRST in setSDK()
                    sdk.addComment(comment, true);
                    
                    // Re-enable auto-scroll and scroll to the bottom to show the new comment
                    autoScrollToBottom = true;
                    scrollToBottom();
                    
                    // Notify listener if set
                    if (commentActionListener != null) {
                        commentActionListener.onCommentPosted(comment);
                    }
                });
                return CONSUME;
            }
        });
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
        // For chat view, we want pagination to load older messages,
        // so we keep the controls visible if there are more to load
        if (sdk.hasMore) {
            paginationControls.setVisibility(View.VISIBLE);

            // Update "Next" button text for loading older messages
            int countToShow = sdk.getCountRemainingToShow();
            if (countToShow > 0) {
                btnNextComments.setText(getContext().getString(R.string.load_older_messages, countToShow));
                btnNextComments.setVisibility(View.VISIBLE);
            } else {
                btnNextComments.setVisibility(View.GONE);
            }

            // Show/hide "Load All" button based on total comment count
            if (sdk.shouldShowLoadAll()) {
                btnLoadAll.setVisibility(View.VISIBLE);
                btnLoadAll.setText(getContext().getString(R.string.load_all_messages, sdk.commentCountOnServer));
            } else {
                btnLoadAll.setVisibility(View.GONE);
            }
        } else {
            paginationControls.setVisibility(View.GONE);
        }
    }

    /**
     * Load more comments (older messages)
     */
    private void loadMoreComments() {
        // Check if we're already loading
        if (paginationProgressBar.getVisibility() == View.VISIBLE) {
            return;
        }

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
                    
                    // Restore buttons
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
                    // Hide loading indicator
                    paginationProgressBar.setVisibility(View.GONE);
                    paginationControls.setVisibility(View.GONE);
                    
                    // Re-enable auto-scroll and scroll to bottom
                    autoScrollToBottom = true;
                    scrollToBottom();
                });
                return CONSUME;
            }
        });
    }

    /**
     * Helper method for processing upvotes with or without anonymous credentials
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
    
    /**
     * Enable or disable auto-scrolling to bottom when new messages arrive
     * 
     * @param enable Whether to enable auto-scrolling
     */
    public void setAutoScrollToBottom(boolean enable) {
        this.autoScrollToBottom = enable;
    }
    
    /**
     * Check if auto-scrolling to bottom is enabled
     * 
     * @return true if auto-scrolling is enabled
     */
    public boolean isAutoScrollToBottom() {
        return autoScrollToBottom;
    }
    
    /**
     * Sets up the demo banner if tenant ID is "demo"
     */
    private void setupDemoBanner() {
        DemoBannerHelper.setupDemoBanner(this, sdk);
    }
}