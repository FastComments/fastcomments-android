package com.fastcomments.sdk;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
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
    private HorizontalScrollView toolbarContainer;
    private LinearLayout toolbar;
    private View toolbarSeparator;
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

    // For toolbar functionality
    private List<CustomToolbarButton> customButtons = new ArrayList<>();
    private boolean toolbarVisible = false;
    private boolean defaultFormattingEnabled = true;
    private int lastCursorPosition = 0;

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
        toolbarContainer = findViewById(R.id.toolbarContainer);
        toolbar = findViewById(R.id.toolbar);
        toolbarSeparator = findViewById(R.id.toolbarSeparator);
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

        // Track cursor position for insertions when EditText loses focus
        commentInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                lastCursorPosition = commentInput.getSelectionStart();
            }
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

        // Apply global toolbar configuration from SDK
        if (sdk != null) {
            sdk.applyGlobalToolbarConfiguration(this);
        } else {
            rebuildToolbar(); // Initialize toolbar when SDK is set
        }
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

    // ===== TOOLBAR MANAGEMENT METHODS =====

    /**
     * Set whether the toolbar is visible
     *
     * @param visible true to show toolbar, false to hide
     */
    public void setToolbarVisible(boolean visible) {
        this.toolbarVisible = visible;
        updateToolbarVisibility();
    }

    /**
     * Check if the toolbar is currently visible
     *
     * @return true if toolbar is visible
     */
    public boolean isToolbarVisible() {
        return toolbarVisible;
    }

    /**
     * Set whether default formatting buttons should be shown
     *
     * @param enabled true to show default formatting buttons
     */
    public void setDefaultFormattingEnabled(boolean enabled) {
        this.defaultFormattingEnabled = enabled;
        rebuildToolbar();
    }

    /**
     * Check if default formatting buttons are enabled
     *
     * @return true if default formatting is enabled
     */
    public boolean isDefaultFormattingEnabled() {
        return defaultFormattingEnabled;
    }

    /**
     * Add a custom toolbar button
     *
     * @param button The custom button to add
     */
    public void addCustomToolbarButton(CustomToolbarButton button) {
        if (button != null && !customButtons.contains(button)) {
            customButtons.add(button);
            rebuildToolbar();
        }
    }

    /**
     * Remove a custom toolbar button
     *
     * @param button The custom button to remove
     */
    public void removeCustomToolbarButton(CustomToolbarButton button) {
        if (customButtons.remove(button)) {
            rebuildToolbar();
        }
    }

    /**
     * Remove a custom toolbar button by ID
     *
     * @param buttonId The ID of the button to remove
     */
    public void removeCustomToolbarButton(String buttonId) {
        CustomToolbarButton toRemove = null;
        for (CustomToolbarButton button : customButtons) {
            if (buttonId.equals(button.getId())) {
                toRemove = button;
                break;
            }
        }
        if (toRemove != null) {
            removeCustomToolbarButton(toRemove);
        }
    }

    /**
     * Clear all custom toolbar buttons
     */
    public void clearCustomToolbarButtons() {
        customButtons.clear();
        rebuildToolbar();
    }

    /**
     * Update toolbar visibility based on current state
     */
    private void updateToolbarVisibility() {
        boolean shouldShow = toolbarVisible && (defaultFormattingEnabled || !customButtons.isEmpty());
        toolbarContainer.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
        toolbarSeparator.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
    }

    /**
     * Rebuild the entire toolbar with current buttons
     */
    private void rebuildToolbar() {
        // Clear existing buttons
        toolbar.removeAllViews();

        // Add default formatting buttons if enabled
        if (defaultFormattingEnabled) {
            addDefaultFormattingButtons();
        }

        // Add custom buttons
        for (CustomToolbarButton button : customButtons) {
            addToolbarButtonView(button);
        }

        // Update visibility
        updateToolbarVisibility();
    }

    /**
     * Add default formatting buttons to the toolbar
     */
    private void addDefaultFormattingButtons() {
        // Bold button
        addDefaultToolbarButton(R.drawable.ic_format_bold, R.string.format_bold, v -> {
            wrapSelection("<b>", "</b>");
        });

        // Italic button
        addDefaultToolbarButton(R.drawable.ic_format_italic, R.string.format_italic, v -> {
            wrapSelection("<i>", "</i>");
        });

        // Link button
        addDefaultToolbarButton(R.drawable.link_icon, R.string.add_link, v -> {
            showLinkDialog();
        });

        // Code button
        addDefaultToolbarButton(R.drawable.ic_code, R.string.format_code, v -> {
            wrapSelection("<code>", "</code>");
        });
    }

    /**
     * Add a default toolbar button
     */
    private void addDefaultToolbarButton(int iconRes, int contentDescriptionRes, View.OnClickListener clickListener) {
        ImageButton button = new ImageButton(getContext());
        button.setImageResource(iconRes);
        button.setContentDescription(getContext().getString(contentDescriptionRes));
        TypedValue outValue = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        button.setBackgroundResource(outValue.resourceId);
        button.setOnClickListener(clickListener);

        // Ensure icon scales properly within button bounds
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int padding = (int) (4 * getContext().getResources().getDisplayMetrics().density);
        button.setPadding(padding, padding, padding, padding);

        // Apply theme colors
        FastCommentsTheme theme = sdk != null ? sdk.getTheme() : null;
        int actionButtonColor = ThemeColorResolver.getActionButtonColor(getContext(), theme);
        button.setImageTintList(ColorStateList.valueOf(actionButtonColor));

        // Set consistent size (reduced from 32dp to 28dp for better proportion)
        int size = (int) (28 * getContext().getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(4, 0, 4, 0);
        button.setLayoutParams(params);

        toolbar.addView(button);
    }

    /**
     * Add a custom toolbar button view
     */
    private void addToolbarButtonView(CustomToolbarButton customButton) {
        ImageButton button = new ImageButton(getContext());
        button.setImageResource(customButton.getIconResourceId());

        if (customButton.getContentDescriptionResourceId() != 0) {
            button.setContentDescription(getContext().getString(customButton.getContentDescriptionResourceId()));
        }

        TypedValue outValue = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        button.setBackgroundResource(outValue.resourceId);
        button.setOnClickListener(v -> customButton.onClick(this, v));

        if (customButton.onLongClick(this, button)) {
            button.setOnLongClickListener(v -> customButton.onLongClick(this, v));
        }

        // Ensure icon scales properly within button bounds
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int padding = (int) (4 * getContext().getResources().getDisplayMetrics().density);
        button.setPadding(padding, padding, padding, padding);

        // Apply theme colors
        FastCommentsTheme theme = sdk != null ? sdk.getTheme() : null;
        int actionButtonColor = ThemeColorResolver.getActionButtonColor(getContext(), theme);
        button.setImageTintList(ColorStateList.valueOf(actionButtonColor));

        // Set consistent size (reduced from 32dp to 28dp for better proportion)
        int size = (int) (28 * getContext().getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(4, 0, 4, 0);
        button.setLayoutParams(params);

        // Update button state
        button.setEnabled(customButton.isEnabled(this));
        button.setVisibility(customButton.isVisible(this) ? View.VISIBLE : View.GONE);

        toolbar.addView(button);

        // Notify button it was attached
        customButton.onAttached(this, button);
    }

    /**
     * Show dialog to add a link
     */
    private void showLinkDialog() {
        // Create a simple dialog for adding links
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle(R.string.add_link);

        // Create input fields
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

        EditText urlInput = new EditText(getContext());
        urlInput.setHint(R.string.link_url_hint);
        urlInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(urlInput);

        EditText textInput = new EditText(getContext());
        textInput.setHint(R.string.link_text_hint);

        // Pre-fill with selected text if any
        String selectedText = getSelectedText();
        if (!selectedText.isEmpty()) {
            textInput.setText(selectedText);
        }
        layout.addView(textInput);

        builder.setView(layout);

        builder.setPositiveButton(R.string.add, (dialog, which) -> {
            String url = urlInput.getText().toString().trim();
            String text = textInput.getText().toString().trim();

            if (!url.isEmpty()) {
                if (text.isEmpty()) {
                    text = url;
                }
                String linkHtml = "<a href=\"" + url + "\">" + text + "</a>";

                if (!selectedText.isEmpty()) {
                    replaceSelection(linkHtml);
                } else {
                    insertHtmlAtCursor(linkHtml);
                }
            }
        });

        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    // ===== TEXT MANIPULATION METHODS =====

    /**
     * Insert plain text at the current cursor position
     *
     * @param text The text to insert
     */
    public void insertTextAtCursor(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        int start = Math.max(commentInput.getSelectionStart(), 0);
        int end = Math.max(commentInput.getSelectionEnd(), 0);

        // If no selection, use last known cursor position when EditText had focus
        if (start == end && start == 0 && lastCursorPosition > 0) {
            start = end = Math.min(lastCursorPosition, commentInput.getText().length());
        }

        commentInput.getText().replace(Math.min(start, end), Math.max(start, end), text);

        // Move cursor to end of inserted text
        int newPosition = Math.min(start, end) + text.length();
        commentInput.setSelection(newPosition);
        lastCursorPosition = newPosition;

        // Update send button state
        updateSendButtonState();
    }

    /**
     * Insert HTML content at the current cursor position
     *
     * @param html The HTML content to insert
     */
    public void insertHtmlAtCursor(String html) {
        insertTextAtCursor(html);
    }

    /**
     * Wrap the currently selected text with start and end tags
     *
     * @param startTag The opening tag (e.g., "<b>")
     * @param endTag The closing tag (e.g., "</b>")
     */
    public void wrapSelection(String startTag, String endTag) {
        int start = Math.max(commentInput.getSelectionStart(), 0);
        int end = Math.max(commentInput.getSelectionEnd(), 0);

        if (start == end) {
            // No selection, just insert the tags at cursor
            insertTextAtCursor(startTag + endTag);
            // Move cursor between the tags
            int newPosition = start + startTag.length();
            commentInput.setSelection(newPosition);
            lastCursorPosition = newPosition;
        } else {
            // Wrap the selected text
            String selectedText = commentInput.getText().subSequence(
                Math.min(start, end), Math.max(start, end)).toString();
            String wrappedText = startTag + selectedText + endTag;

            commentInput.getText().replace(Math.min(start, end), Math.max(start, end), wrappedText);

            // Select the content between the tags
            int newStart = Math.min(start, end) + startTag.length();
            int newEnd = newStart + selectedText.length();
            commentInput.setSelection(newStart, newEnd);
            lastCursorPosition = newEnd;
        }

        updateSendButtonState();
    }

    /**
     * Get the currently selected text
     *
     * @return The selected text, or empty string if no selection
     */
    public String getSelectedText() {
        int start = Math.max(commentInput.getSelectionStart(), 0);
        int end = Math.max(commentInput.getSelectionEnd(), 0);

        if (start == end) {
            return "";
        }

        return commentInput.getText().subSequence(
            Math.min(start, end), Math.max(start, end)).toString();
    }

    /**
     * Replace the currently selected text
     *
     * @param text The replacement text
     */
    public void replaceSelection(String text) {
        int start = Math.max(commentInput.getSelectionStart(), 0);
        int end = Math.max(commentInput.getSelectionEnd(), 0);

        commentInput.getText().replace(Math.min(start, end), Math.max(start, end), text);

        // Move cursor to end of replacement text
        int newPosition = Math.min(start, end) + text.length();
        commentInput.setSelection(newPosition);
        lastCursorPosition = newPosition;

        updateSendButtonState();
    }

    /**
     * Get the current cursor position
     *
     * @return The cursor position
     */
    public int getCursorPosition() {
        return Math.max(commentInput.getSelectionStart(), 0);
    }

    /**
     * Set the cursor position
     *
     * @param position The new cursor position
     */
    public void setCursorPosition(int position) {
        int safePosition = Math.max(0, Math.min(position, commentInput.getText().length()));
        commentInput.setSelection(safePosition);
        lastCursorPosition = safePosition;
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