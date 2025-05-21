package com.fastcomments.sdk;

import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.fastcomments.model.APIError;
import com.fastcomments.model.PublicComment;
import com.fastcomments.model.UserSessionInfo;

import java.util.ArrayList;
import java.util.List;

public class CommentFormView extends LinearLayout {

    private ImageView avatarImageView;
    private TextView userNameTextView;
    private EditText commentEditText;
    private Button submitButton;
    private Button cancelButton;
    private ProgressBar progressBar;
    private TextView errorTextView;
    private TextView replyingToTextView;
    private ListView mentionSuggestionsList;
    private OnCommentSubmitListener submitListener;
    private OnCancelReplyListener cancelListener;
    private String parentId;
    private RenderableComment parentComment;
    
    // For @mentions functionality
    private FastCommentsSDK sdk;
    private MentionSuggestionsAdapter mentionsAdapter;
    private List<UserMention> mentionSuggestions = new ArrayList<>();
    private List<UserMention> selectedMentions = new ArrayList<>();
    private int mentionStartPosition = -1;
    private String currentMentionText = "";
    private boolean isSearchingUsers = false;

    public interface OnCommentSubmitListener {
        void onCommentSubmit(String commentText, String parentId);
    }

    public interface OnCancelReplyListener {
        void onCancelReply();
    }

    public CommentFormView(Context context) {
        super(context);
        init(context);
    }

    public CommentFormView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CommentFormView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.comment_form_view, this, true);

        avatarImageView = findViewById(R.id.formAvatar);
        userNameTextView = findViewById(R.id.formUserName);
        commentEditText = findViewById(R.id.formEditText);
        submitButton = findViewById(R.id.formSubmitButton);
        progressBar = findViewById(R.id.formProgressBar);
        errorTextView = findViewById(R.id.formErrorText);
        replyingToTextView = findViewById(R.id.replyingToText);
        cancelButton = findViewById(R.id.cancelReplyButton);
        mentionSuggestionsList = findViewById(R.id.mentionSuggestionsList);

        // Hide error text initially
        errorTextView.setVisibility(View.GONE);
        replyingToTextView.setVisibility(View.GONE);

        // Initialize mentions adapter
        mentionsAdapter = new MentionSuggestionsAdapter(getContext(), mentionSuggestions);
        mentionSuggestionsList.setAdapter(mentionsAdapter);
        mentionSuggestionsList.setVisibility(View.GONE);

        // Setup mention item click listener
        mentionSuggestionsList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < mentionSuggestions.size()) {
                selectUserMention(mentionSuggestions.get(position));
            }
        });

        // Setup text watcher for @mentions
        commentEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not needed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Check for @ character to trigger mention suggestions
                if (count == 1 && start < s.length() && s.charAt(start) == '@') {
                    // Start tracking a new mention
                    mentionStartPosition = start;
                    currentMentionText = "";
                    showMentionSuggestions();
                } else if (mentionStartPosition >= 0) {
                    // We're in the middle of typing a mention
                    if (start < mentionStartPosition) {
                        // Cursor moved before the @ symbol, cancel mention
                        cancelMention();
                    } else {
                        // Extract the text between @ and cursor
                        int mentionLength = start + count - mentionStartPosition;
                        if (mentionLength > 0 && start + count <= s.length()) {
                            String newMentionText = s.subSequence(mentionStartPosition + 1, start + count).toString();
                            
                            // Check if mention text changed
                            if (!newMentionText.equals(currentMentionText)) {
                                currentMentionText = newMentionText;
                                
                                // Cancel mention if space is added (end of mention)
                                if (currentMentionText.contains(" ")) {
                                    cancelMention();
                                } else {
                                    // Search for users with the updated text
                                    searchUsers(currentMentionText);
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Not needed
            }
        });

        // Set up the submit button
        submitButton.setOnClickListener(v -> {
            String commentText = commentEditText.getText().toString().trim();
            if (TextUtils.isEmpty(commentText)) {
                errorTextView.setText(R.string.empty_comment_error);
                errorTextView.setVisibility(View.VISIBLE);
                return;
            }

            errorTextView.setVisibility(View.GONE);
            if (submitListener != null) {
                submitListener.onCommentSubmit(commentText, parentId);
            }
        });

        // Set up cancel button
        cancelButton.setOnClickListener(v -> {
            // The cancel listener might want to show a confirmation dialog,
            // so we let it handle the cancellation first
            if (cancelListener != null) {
                cancelListener.onCancelReply();
            } else {
                // If no listener or the listener doesn't handle it, just reset
                resetReplyState();
            }
        });

        // Initially hide cancel button until replying
        cancelButton.setVisibility(View.GONE);
    }

    /**
     * Set the listener for comment submission
     *
     * @param listener OnCommentSubmitListener
     */
    public void setOnCommentSubmitListener(OnCommentSubmitListener listener) {
        this.submitListener = listener;
    }

    /**
     * Set the listener for canceling a reply
     *
     * @param listener OnCancelReplyListener
     */
    public void setOnCancelReplyListener(OnCancelReplyListener listener) {
        this.cancelListener = listener;
    }

    /**
     * Set the current user info in the form
     *
     * @param userInfo UserSessionInfo
     */
    public void setCurrentUser(@NonNull UserSessionInfo userInfo) {
        // Try displayName first, then fallback to username, then Anonymous
        String nameToShow = userInfo.getDisplayName();
        if (nameToShow == null || nameToShow.isEmpty()) {
            nameToShow = userInfo.getUsername();
            if (nameToShow == null || nameToShow.isEmpty()) {
                nameToShow = getContext().getString(R.string.anonymous);
            }
        }
        userNameTextView.setText(nameToShow);
        
        if (userInfo.getAvatarSrc() != null) {
            AvatarFetcher.fetchTransformInto(getContext(), userInfo.getAvatarSrc(), avatarImageView);
        } else {
            AvatarFetcher.fetchTransformInto(getContext(), R.drawable.default_avatar, avatarImageView);
        }
    }

    /**
     * Show loading state during comment submission
     *
     * @param submitting true to show loading, false to hide
     */
    public void setSubmitting(boolean submitting) {
        progressBar.setVisibility(submitting ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!submitting);
        commentEditText.setEnabled(!submitting);
        cancelButton.setEnabled(!submitting);
    }

    /**
     * Clear the comment text input and selected mentions
     */
    public void clearText() {
        commentEditText.setText("");
        // Clear selected mentions
        if (selectedMentions != null) {
            selectedMentions.clear();
        }
    }
    
    /**
     * Check if the comment text input is empty
     * 
     * @return true if the comment text is empty, false otherwise
     */
    public boolean isTextEmpty() {
        return commentEditText.getText().toString().trim().isEmpty();
    }

    /**
     * Show an error message
     *
     * @param errorMessage Error message to display
     */
    public void showError(String errorMessage) {
        errorTextView.setText(errorMessage);
        errorTextView.setVisibility(View.VISIBLE);
    }

    /**
     * Set up the form for replying to a comment.
     */
    public void setReplyingTo(RenderableComment renderableComment) {
        if (renderableComment != null) {
            final PublicComment comment = renderableComment.getComment();
            this.parentId = comment.getId();
            this.parentComment = renderableComment; // Store reference to parent comment
            String commenterName = comment.getCommenterName() != null
                    ? comment.getCommenterName()
                    : getContext().getString(R.string.anonymous);

            replyingToTextView.setText(getContext().getString(R.string.replying_to, commenterName));
            replyingToTextView.setVisibility(View.VISIBLE);
            cancelButton.setVisibility(View.VISIBLE);

            // Set hint to indicate replying
            commentEditText.setHint(R.string.reply_hint);

            // Focus the comment box
            commentEditText.requestFocus();
        }
    }

    /**
     * Reset to top-level comment state (not replying)
     */
    public void resetReplyState() {
        this.parentId = null;
        this.parentComment = null;
        replyingToTextView.setVisibility(View.GONE);
        cancelButton.setVisibility(View.GONE);
        commentEditText.setHint(R.string.comment_hint);
        clearText(); // This will also clear selected mentions
    }
    
    /**
     * Get the parent comment that's being replied to
     * 
     * @return The parent RenderableComment or null if not replying
     */
    public RenderableComment getParentComment() {
        return parentComment;
    }
    
    /**
     * Set the SDK instance for API access
     * 
     * @param sdk FastCommentsSDK instance
     */
    public void setSDK(FastCommentsSDK sdk) {
        this.sdk = sdk;
    }
    
    /**
     * Show the mention suggestions list
     */
    private void showMentionSuggestions() {
        if (mentionSuggestionsList != null) {
            mentionSuggestionsList.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * Hide the mention suggestions list
     */
    private void hideMentionSuggestions() {
        if (mentionSuggestionsList != null) {
            mentionSuggestionsList.setVisibility(View.GONE);
        }
    }
    
    /**
     * Cancel the current mention being typed
     */
    private void cancelMention() {
        mentionStartPosition = -1;
        currentMentionText = "";
        hideMentionSuggestions();
    }
    
    /**
     * Search for users by partial name
     * 
     * @param searchTerm The search term (text after @ symbol)
     */
    private void searchUsers(String searchTerm) {
        if (sdk == null) {
            return;
        }
        
        // Don't search if the term is empty
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            mentionSuggestions.clear();
            mentionsAdapter.notifyDataSetChanged();
            return;
        }
        
        // Don't search if already searching
        if (isSearchingUsers) {
            return;
        }
        
        isSearchingUsers = true;
        
        // Call the API to search for users
        sdk.searchUsers(searchTerm, new FCCallback<List<UserMention>>() {
            @Override
            public boolean onFailure(APIError error) {
                isSearchingUsers = false;
                // Use the main thread to update UI
                post(() -> {
                    // Clear any previous results and show empty state
                    mentionSuggestions.clear();
                    mentionsAdapter.notifyDataSetChanged();
                    hideMentionSuggestions();
                });
                return FCCallback.CONSUME;
            }

            @Override
            public boolean onSuccess(List<UserMention> users) {
                isSearchingUsers = false;
                
                // Use the main thread to update UI
                post(() -> {
                    // Update the suggestions list
                    mentionSuggestions.clear();
                    
                    if (users != null && !users.isEmpty()) {
                        mentionSuggestions.addAll(users);
                        showMentionSuggestions();
                    } else {
                        hideMentionSuggestions();
                    }
                    
                    mentionsAdapter.notifyDataSetChanged();
                });
                return FCCallback.CONSUME;
            }
        });
    }
    
    /**
     * Select a user mention from the suggestions list
     * 
     * @param mention The user mention to select
     */
    private void selectUserMention(UserMention mention) {
        if (mention == null || mentionStartPosition < 0) {
            return;
        }
        
        // Add the mention to the selected mentions list
        mention.setMentioned(true);
        selectedMentions.add(mention);
        
        // Replace the @query with @username
        String text = commentEditText.getText().toString();
        String beforeMention = text.substring(0, mentionStartPosition);
        String afterMention = text.substring(mentionStartPosition + currentMentionText.length() + 1);
        String newText = beforeMention + "@" + mention.getUsername() + " " + afterMention;
        
        // Update the EditText with new text
        commentEditText.setText(newText);
        
        // Move cursor after the inserted mention
        int newPosition = mentionStartPosition + mention.getUsername().length() + 2; // +2 for @ and space
        commentEditText.setSelection(newPosition);
        
        // Reset the mention state
        cancelMention();
    }
    
    /**
     * Get the selected user mentions for the current comment
     * 
     * @return List of selected user mentions
     */
    public List<UserMention> getSelectedMentions() {
        return selectedMentions;
    }
}