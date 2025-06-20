package com.fastcomments.sdk;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fastcomments.model.APIError;
import com.fastcomments.model.UserSessionInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern bottom comment input view with user avatar and send button
 */
public class BottomCommentInputView extends FrameLayout {

    private LinearLayout replyIndicator;
    private TextView replyingToText;
    private ImageButton cancelReplyButton;
    private ImageView userAvatar;
    private EditText commentInput;
    private ImageButton sendButton;
    private ProgressBar sendProgress;
    private TextView errorMessage;
    private ListView mentionSuggestionsList;

    private OnCommentSubmitListener submitListener;
    private OnReplyStateChangeListener replyStateChangeListener;
    private RenderableComment parentComment;
    private UserSessionInfo currentUser;

    // For @mentions functionality
    private FastCommentsSDK sdk;
    private MentionSuggestionsAdapter mentionsAdapter;
    private List<UserMention> mentionSuggestions = new ArrayList<>();
    private List<UserMention> selectedMentions = new ArrayList<>();
    private int mentionStartPosition = -1;
    private String currentMentionText = "";
    private boolean isSearchingUsers = false;
    private PopupWindow mentionPopup;

    public interface OnCommentSubmitListener {
        void onCommentSubmit(String text, String parentId);
    }

    public interface OnReplyStateChangeListener {
        void onReplyStateChanged(boolean isReplying, RenderableComment parentComment);
    }

    public BottomCommentInputView(@NonNull Context context) {
        super(context);
        init();
    }

    public BottomCommentInputView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BottomCommentInputView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.bottom_comment_input, this, true);

        // Initialize views
        replyIndicator = findViewById(R.id.replyIndicator);
        replyingToText = findViewById(R.id.replyingToText);
        cancelReplyButton = findViewById(R.id.cancelReplyButton);
        userAvatar = findViewById(R.id.userAvatar);
        commentInput = findViewById(R.id.commentInput);
        sendButton = findViewById(R.id.sendButton);
        sendProgress = findViewById(R.id.sendProgress);
        errorMessage = findViewById(R.id.errorMessage);
        mentionSuggestionsList = findViewById(R.id.mentionSuggestionsList);

        setupListeners();
        setupMentions();
    }

    private void setupListeners() {
        // Text change listener to enable/disable send button and handle mentions
        commentInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButtonState();
                hideError();
                
                // Handle @mentions
                handleMentionInput(s, start, before, count);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Send button click
        sendButton.setOnClickListener(v -> {
            String text = commentInput.getText().toString().trim();
            if (!text.isEmpty() && submitListener != null) {
                String parentId = parentComment != null ? parentComment.getComment().getId() : null;
                submitListener.onCommentSubmit(text, parentId);
            }
        });

        // Cancel reply button
        cancelReplyButton.setOnClickListener(v -> {
            clearReplyState();
        });
    }

    private void updateSendButtonState() {
        boolean hasText = !commentInput.getText().toString().trim().isEmpty();
        sendButton.setEnabled(hasText);
        sendButton.setAlpha(hasText ? 1.0f : 0.5f);
    }

    public void setOnCommentSubmitListener(OnCommentSubmitListener listener) {
        this.submitListener = listener;
    }

    public void setOnReplyStateChangeListener(OnReplyStateChangeListener listener) {
        this.replyStateChangeListener = listener;
    }

    public void setCurrentUser(UserSessionInfo user) {
        this.currentUser = user;
        updateUserAvatar();
    }

    private void updateUserAvatar() {
        if (currentUser != null && currentUser.getAvatarSrc() != null) {
            // Load user avatar using AvatarFetcher
            AvatarFetcher.fetchTransformInto(getContext(), currentUser.getAvatarSrc(), userAvatar);
        } else {
            AvatarFetcher.fetchTransformInto(getContext(), R.drawable.default_avatar, userAvatar);
        }
    }

    public void setReplyingTo(RenderableComment comment) {
        this.parentComment = comment;
        if (comment != null) {
            String userName = comment.getComment().getCommenterName();
            if (userName == null || userName.isEmpty()) {
                userName = getContext().getString(R.string.anonymous);
            }
            replyingToText.setText(getContext().getString(R.string.replying_to, userName));
            replyIndicator.setVisibility(View.VISIBLE);
            commentInput.setHint(R.string.reply_hint);
            commentInput.requestFocus();
        } else {
            clearReplyState();
        }

        if (replyStateChangeListener != null) {
            replyStateChangeListener.onReplyStateChanged(comment != null, comment);
        }
    }

    public void clearReplyState() {
        this.parentComment = null;
        replyIndicator.setVisibility(View.GONE);
        commentInput.setHint(R.string.add_comment_hint);

        if (replyStateChangeListener != null) {
            replyStateChangeListener.onReplyStateChanged(false, null);
        }
    }

    public void clearText() {
        commentInput.setText("");
        updateSendButtonState();
    }

    public void setSubmitting(boolean submitting) {
        sendButton.setVisibility(submitting ? View.GONE : View.VISIBLE);
        sendProgress.setVisibility(submitting ? View.VISIBLE : View.GONE);
        commentInput.setEnabled(!submitting);
        sendButton.setEnabled(!submitting && !commentInput.getText().toString().trim().isEmpty());
    }

    public void showError(String error) {
        errorMessage.setText(error);
        errorMessage.setVisibility(View.VISIBLE);
    }

    public void hideError() {
        errorMessage.setVisibility(View.GONE);
    }

    public boolean isTextEmpty() {
        return commentInput.getText().toString().trim().isEmpty();
    }

    public RenderableComment getParentComment() {
        return parentComment;
    }

    public String getText() {
        return commentInput.getText().toString().trim();
    }

    public void requestInputFocus() {
        commentInput.requestFocus();
    }

    /**
     * Set up mention suggestions functionality
     */
    public void setMentionSuggestionsAdapter(MentionSuggestionsAdapter adapter) {
        mentionSuggestionsList.setAdapter(adapter);
    }

    /**
     * Show or hide mention suggestions
     */
    public void showMentionSuggestions(boolean show) {
        mentionSuggestionsList.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * Get the EditText for advanced text processing (like mentions)
     */
    public EditText getEditText() {
        return commentInput;
    }

    /**
     * Get selected mentions
     */
    public List<UserMention> getSelectedMentions() {
        return selectedMentions;
    }

    /**
     * Set the SDK instance for API access and mention functionality
     */
    public void setSDK(FastCommentsSDK sdk) {
        this.sdk = sdk;
        applyTheme();
    }

    /**
     * Set up mentions functionality
     */
    private void setupMentions() {
        // Initialize mention suggestions adapter
        mentionsAdapter = new MentionSuggestionsAdapter(getContext(), mentionSuggestions);
        
        // Create the popup window for mentions
        createMentionPopup();
    }
    
    /**
     * Create the popup window for mention suggestions
     */
    private void createMentionPopup() {
        // Create a new ListView for the popup
        ListView popupListView = new ListView(getContext());
        popupListView.setAdapter(mentionsAdapter);
        popupListView.setBackgroundColor(getContext().getResources().getColor(android.R.color.white));
        popupListView.setDivider(getContext().getResources().getDrawable(android.R.color.darker_gray));
        popupListView.setDividerHeight(1);
        popupListView.setPadding(8, 8, 8, 8);
        
        // Handle mention selection
        popupListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < mentionSuggestions.size()) {
                selectUserMention(mentionSuggestions.get(position));
            }
        });
        
        // Create the popup window
        mentionPopup = new PopupWindow(popupListView, 
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT);
        mentionPopup.setOutsideTouchable(true);
        mentionPopup.setFocusable(false);
        mentionPopup.setElevation(12);
    }

    /**
     * Handle mention input detection and processing
     */
    private void handleMentionInput(CharSequence s, int start, int before, int count) {
        // Check for @ character to trigger mention suggestions
        if (count == 1 && start < s.length() && s.charAt(start) == '@') {
            // Start tracking a new mention
            mentionStartPosition = start;
            currentMentionText = "";
            showMentionSuggestions();
        } else if (mentionStartPosition >= 0) {
            // Check if the @ symbol was deleted
            if (mentionStartPosition >= s.length() || s.charAt(mentionStartPosition) != '@') {
                // The @ symbol was deleted, cancel mention
                cancelMention();
                return;
            }
            
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

    /**
     * Show the mention suggestions list
     */
    private void showMentionSuggestions() {
        if (mentionPopup != null && !mentionSuggestions.isEmpty()) {
            // Calculate height based on number of items (max 3 visible)
            int maxItems = Math.min(mentionSuggestions.size(), 3);
            float density = getContext().getResources().getDisplayMetrics().density;
            int itemHeight = (int)(50 * density); // 50dp per item
            int totalHeight = maxItems * itemHeight + (int)(16 * density); // Add padding
            
            // Set the popup height
            mentionPopup.setHeight(totalHeight);
            
            // Calculate position and size
            int[] location = new int[2];
            commentInput.getLocationOnScreen(location);
            int inputWidth = commentInput.getWidth();
            int popupWidth = (int)(inputWidth * 0.8f); // 80% of input width
            mentionPopup.setWidth(popupWidth);
            
            // Position popup above the entire bottom input view
            int[] thisViewLocation = new int[2];
            this.getLocationOnScreen(thisViewLocation);
            
            int xPos = location[0] + (int)(12 * density); // Small margin from left of input
            int yPos = thisViewLocation[1] - totalHeight - (int)(8 * density); // Above the entire view with margin
            
            if (!mentionPopup.isShowing()) {
                mentionPopup.showAtLocation(commentInput, android.view.Gravity.NO_GRAVITY, xPos, yPos);
            }
        } else {
            hideMentionSuggestions();
        }
    }
    
    /**
     * Hide the mention suggestions list
     */
    private void hideMentionSuggestions() {
        if (mentionPopup != null && mentionPopup.isShowing()) {
            mentionPopup.dismiss();
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
        
        sdk.searchUsers(searchTerm, new FCCallback<List<UserMention>>() {
            @Override
            public boolean onFailure(APIError error) {
                isSearchingUsers = false;
                // Use the main thread to update UI
                post(() -> {
                    // Clear any previous results
                    mentionSuggestions.clear();
                    mentionsAdapter.notifyDataSetChanged();
                    
                    // Hide the list on error
                    hideMentionSuggestions();
                });
                return FCCallback.CONSUME;
            }
            
            @Override
            public boolean onSuccess(List<UserMention> users) {
                isSearchingUsers = false;
                // Use the main thread to update UI
                post(() -> {
                    // Clear previous suggestions
                    mentionSuggestions.clear();
                    
                    if (users != null && !users.isEmpty()) {
                        // Add new suggestions
                        mentionSuggestions.addAll(users);
                        mentionsAdapter.notifyDataSetChanged();
                        
                        // Show the suggestions list
                        showMentionSuggestions();
                    } else {
                        // No results, hide the list
                        hideMentionSuggestions();
                    }
                });
                return FCCallback.CONSUME;
            }
        });
    }

    /**
     * Select a user mention from the suggestions
     */
    private void selectUserMention(UserMention mention) {
        if (mention == null || mentionStartPosition < 0) {
            return;
        }
        
        // Add the mention to the selected mentions list
        mention.setMentioned(true);
        selectedMentions.add(mention);
        
        // Replace the @query with @username
        String text = commentInput.getText().toString();
        String beforeMention = text.substring(0, mentionStartPosition);
        String afterMention = text.substring(mentionStartPosition + currentMentionText.length() + 1);
        String newText = beforeMention + "@" + mention.getUsername() + " " + afterMention;
        
        // Update the EditText with new text
        commentInput.setText(newText);
        
        // Move cursor after the inserted mention
        int newPosition = mentionStartPosition + mention.getUsername().length() + 2; // +2 for @ and space
        commentInput.setSelection(newPosition);
        
        // Reset the mention state
        cancelMention();
    }
    
    /**
     * Apply theme colors to the UI elements
     */
    private void applyTheme() {
        FastCommentsTheme theme = sdk != null ? sdk.getTheme() : null;
        
        // Apply action button color to the send button
        int actionButtonColor = ThemeColorResolver.getActionButtonColor(getContext(), theme);
        sendButton.setImageTintList(ColorStateList.valueOf(actionButtonColor));
        
        // Also apply to cancel reply button
        if (cancelReplyButton != null) {
            cancelReplyButton.setImageTintList(ColorStateList.valueOf(actionButtonColor));
        }
    }
    
    /**
     * Clean up resources when the view is detached
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Dismiss popup to prevent memory leaks
        if (mentionPopup != null && mentionPopup.isShowing()) {
            mentionPopup.dismiss();
        }
    }
}