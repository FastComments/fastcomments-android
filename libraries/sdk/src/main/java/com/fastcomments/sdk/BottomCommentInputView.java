package com.fastcomments.sdk;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private RichEditText commentInput;
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

    // For WYSIWYG formatting
    private final Set<String> activeFormatsForNextChar = new HashSet<>();
    private final Map<String, ImageButton> defaultToolbarButtons = new LinkedHashMap<>();
    private int lastTextChangeStart = 0;
    private int lastTextChangeCount = 0;
    private RichTextHelper.EditableImageGetter editableImageGetter;

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

        // Create reusable image getter for WYSIWYG HTML parsing
        editableImageGetter = new RichTextHelper.EditableImageGetter(commentInput);

        // Listen for cursor/selection changes to update toolbar active states
        commentInput.setOnSelectionChangedListener((selStart, selEnd) -> {
            activeFormatsForNextChar.clear();
            updateToolbarActiveStates();
        });

        setupListeners();
        setupMentions();
    }

    private void setupListeners() {
        // Text change listener to enable/disable send button, handle mentions, and apply WYSIWYG spans
        commentInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButtonState();
                hideError();

                // Track change range for WYSIWYG span application
                lastTextChangeStart = start;
                lastTextChangeCount = count;

                // Handle @mentions
                handleMentionInput(s, start, before, count);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Apply active formatting to newly typed characters
                if (!activeFormatsForNextChar.isEmpty() && lastTextChangeCount > 0) {
                    int applyStart = lastTextChangeStart;
                    int applyEnd = lastTextChangeStart + lastTextChangeCount;
                    if (applyEnd <= s.length()) {
                        for (String format : activeFormatsForNextChar) {
                            switch (format) {
                                case "bold":
                                    if (!hasMatchingStyleSpan(s, Typeface.BOLD, applyStart, applyEnd)) {
                                        s.setSpan(new StyleSpan(Typeface.BOLD), applyStart, applyEnd,
                                                Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
                                    }
                                    break;
                                case "italic":
                                    if (!hasMatchingStyleSpan(s, Typeface.ITALIC, applyStart, applyEnd)) {
                                        s.setSpan(new StyleSpan(Typeface.ITALIC), applyStart, applyEnd,
                                                Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
                                    }
                                    break;
                                case "code":
                                    if (!hasMatchingSpan(s, TypefaceSpan.class, applyStart, applyEnd)) {
                                        s.setSpan(new TypefaceSpan("monospace"), applyStart, applyEnd,
                                                Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
                                        s.setSpan(new BackgroundColorSpan(0x20808080), applyStart, applyEnd,
                                                Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
                                    }
                                    break;
                            }
                        }
                    }
                }
                updateToolbarActiveStates();
            }
        });

        // Send button click — serialize spans to HTML
        sendButton.setOnClickListener(v -> {
            String plainText = commentInput.getText().toString().trim();
            if (!plainText.isEmpty() && submitListener != null) {
                String html = getText();
                String parentId = parentComment != null ? parentComment.getComment().getId() : null;
                submitListener.onCommentSubmit(html, parentId);
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
        activeFormatsForNextChar.clear();
        if (editableImageGetter != null) {
            editableImageGetter.clearTargets();
        }
        commentInput.setSuppressSelectionEvents(true);
        commentInput.setText("");
        commentInput.setSuppressSelectionEvents(false);
        updateSendButtonState();
        updateToolbarActiveStates();
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
        return RichTextHelper.toHtml(commentInput.getText()).trim();
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
            int popupWidth = (int)(inputWidth * 0.9f); // 90% of input width
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

        // Replace the @query with @username using Editable ops to preserve spans
        Editable editable = commentInput.getText();
        int deleteEnd = mentionStartPosition + currentMentionText.length() + 1;
        deleteEnd = Math.min(deleteEnd, editable.length());
        editable.delete(mentionStartPosition, deleteEnd);
        String insertText = "@" + mention.getUsername() + " ";
        editable.insert(mentionStartPosition, insertText);

        // Move cursor after the inserted mention
        int newPosition = mentionStartPosition + insertText.length();
        commentInput.setSelection(Math.min(newPosition, editable.length()));

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
        defaultToolbarButtons.clear();

        // Bold button
        ImageButton boldBtn = addDefaultToolbarButton(R.drawable.ic_format_bold, R.string.format_bold,
                v -> toggleFormat("bold"));
        defaultToolbarButtons.put("bold", boldBtn);

        // Italic button
        ImageButton italicBtn = addDefaultToolbarButton(R.drawable.ic_format_italic, R.string.format_italic,
                v -> toggleFormat("italic"));
        defaultToolbarButtons.put("italic", italicBtn);

        // Link button
        ImageButton linkBtn = addDefaultToolbarButton(R.drawable.link_icon, R.string.add_link,
                v -> showLinkDialog());
        defaultToolbarButtons.put("link", linkBtn);

        // Code button
        ImageButton codeBtn = addDefaultToolbarButton(R.drawable.ic_code, R.string.format_code,
                v -> toggleFormat("code"));
        codeBtn.setOnLongClickListener(v -> {
            toggleFormat("codeblock");
            return true;
        });
        defaultToolbarButtons.put("code", codeBtn);
    }

    /**
     * Add a default toolbar button
     *
     * @return The created ImageButton, for tracking active states
     */
    private ImageButton addDefaultToolbarButton(int iconRes, int contentDescriptionRes, View.OnClickListener clickListener) {
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
        return button;
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
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle(R.string.add_link);

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
        int selStart = Math.max(commentInput.getSelectionStart(), 0);
        int selEnd = Math.max(commentInput.getSelectionEnd(), 0);
        boolean hasSelection = selStart != selEnd;
        String selectedText = getSelectedText();
        if (!selectedText.isEmpty()) {
            textInput.setText(selectedText);
        }
        layout.addView(textInput);

        builder.setView(layout);

        builder.setPositiveButton(R.string.add, (dialog, which) -> {
            String url = urlInput.getText().toString().trim();
            String linkText = textInput.getText().toString().trim();

            if (!url.isEmpty()) {
                if (linkText.isEmpty()) {
                    linkText = url;
                }
                Editable editable = commentInput.getText();
                if (hasSelection) {
                    // Replace selection with link text and apply URLSpan
                    int rStart = Math.min(selStart, selEnd);
                    int rEnd = Math.max(selStart, selEnd);
                    editable.replace(rStart, rEnd, linkText);
                    int linkEnd = rStart + linkText.length();
                    RichTextHelper.applyLink(editable, rStart, linkEnd, url);
                    commentInput.setSelection(linkEnd);
                } else {
                    // Insert link text at cursor and apply URLSpan
                    int pos = Math.max(commentInput.getSelectionStart(), 0);
                    editable.insert(pos, linkText);
                    int linkEnd = pos + linkText.length();
                    RichTextHelper.applyLink(editable, pos, linkEnd, url);
                    commentInput.setSelection(linkEnd);
                }
                updateSendButtonState();
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
     * Insert HTML content at the current cursor position.
     * Converts HTML to Spanned so formatting renders visually (WYSIWYG).
     *
     * @param html The HTML content to insert
     */
    public void insertHtmlAtCursor(String html) {
        if (html == null || html.isEmpty()) return;

        // If cursor is at 0 but we had a previous position, restore it
        int start = Math.max(commentInput.getSelectionStart(), 0);
        int end = Math.max(commentInput.getSelectionEnd(), 0);
        if (start == end && start == 0 && lastCursorPosition > 0) {
            int restored = Math.min(lastCursorPosition, commentInput.getText().length());
            commentInput.setSelection(restored);
        }

        lastCursorPosition = RichTextHelper.insertHtmlAtCursor(commentInput, html, editableImageGetter);
        updateSendButtonState();
    }

    /**
     * Wrap the currently selected text with formatting.
     * Recognized HTML tags are converted to WYSIWYG spans; unrecognized tags
     * fall back to literal string insertion for backward compatibility.
     *
     * @param startTag The opening tag (e.g., "&lt;b&gt;")
     * @param endTag The closing tag (e.g., "&lt;/b&gt;")
     */
    public void wrapSelection(String startTag, String endTag) {
        // Try to map HTML tags to WYSIWYG format toggles
        // (uses toggleFormat which supports no-selection cursor-position toggling)
        String formatName = RichTextHelper.parseHtmlTags(startTag, endTag);
        if (formatName != null) {
            toggleFormat(formatName);
            return;
        }

        // Check for <a href="..."> link tags
        String href = RichTextHelper.extractHref(startTag);
        if (href != null) {
            int start = Math.max(commentInput.getSelectionStart(), 0);
            int end = Math.max(commentInput.getSelectionEnd(), 0);
            if (start != end) {
                RichTextHelper.applyLink(commentInput.getText(), Math.min(start, end), Math.max(start, end), href);
            }
            updateSendButtonState();
            return;
        }

        // Unrecognized tags — fall back to literal string insertion
        lastCursorPosition = RichTextHelper.wrapSelectionLiteral(commentInput, startTag, endTag);
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

    // ===== WYSIWYG FORMATTING METHODS =====

    /**
     * Toggle a named format on the current selection, or set it as the active format
     * for the next typed character if there's no selection.
     */
    private void toggleFormat(String formatName) {
        Editable editable = commentInput.getText();
        int selStart = Math.max(commentInput.getSelectionStart(), 0);
        int selEnd = Math.max(commentInput.getSelectionEnd(), 0);

        if (selStart != selEnd) {
            // Selection exists — apply/remove span directly
            int start = Math.min(selStart, selEnd);
            int end = Math.max(selStart, selEnd);
            switch (formatName) {
                case "bold":
                    RichTextHelper.toggleBold(editable, start, end);
                    break;
                case "italic":
                    RichTextHelper.toggleItalic(editable, start, end);
                    break;
                case "code":
                    RichTextHelper.toggleCode(editable, start, end);
                    break;
                case "codeblock":
                    RichTextHelper.toggleCodeBlock(editable, start, end);
                    break;
            }
        } else {
            // No selection — check if the format is currently active from an existing span.
            // If active from a span, truncate it at the cursor (change end to EXCLUSIVE_EXCLUSIVE)
            // so new characters don't inherit. If not active, add to the set so new chars get the format.
            boolean activeFromSpan;
            switch (formatName) {
                case "bold":
                    activeFromSpan = RichTextHelper.isBoldActive(editable, selStart);
                    break;
                case "italic":
                    activeFromSpan = RichTextHelper.isItalicActive(editable, selStart);
                    break;
                case "code":
                case "codeblock":
                    activeFromSpan = RichTextHelper.isCodeActive(editable, selStart);
                    break;
                default:
                    activeFromSpan = false;
            }

            if (activeFromSpan) {
                // Currently bold from a span — truncate it so new chars aren't bold
                switch (formatName) {
                    case "bold":
                        RichTextHelper.truncateBoldAtCursor(editable, selStart);
                        break;
                    case "italic":
                        RichTextHelper.truncateItalicAtCursor(editable, selStart);
                        break;
                    case "code":
                    case "codeblock":
                        RichTextHelper.truncateCodeAtCursor(editable, selStart);
                        break;
                }
                activeFormatsForNextChar.remove(formatName);
            } else if (activeFormatsForNextChar.contains(formatName)) {
                // Was queued for next char — turn it off
                activeFormatsForNextChar.remove(formatName);
            } else {
                // Not active at all — turn on for next typed char
                activeFormatsForNextChar.add(formatName);
            }
        }
        updateToolbarActiveStates();
    }

    /**
     * Check if an existing SPAN_EXCLUSIVE_INCLUSIVE StyleSpan already covers the range.
     */
    private boolean hasMatchingStyleSpan(Editable s, int style, int start, int end) {
        for (StyleSpan span : s.getSpans(start, end, StyleSpan.class)) {
            if (span.getStyle() == style && s.getSpanStart(span) <= start && s.getSpanEnd(span) >= end) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if an existing span of the given type already covers the range.
     */
    private <T> boolean hasMatchingSpan(Editable s, Class<T> type, int start, int end) {
        for (T span : s.getSpans(start, end, type)) {
            if (s.getSpanStart(span) <= start && s.getSpanEnd(span) >= end) {
                return true;
            }
        }
        return false;
    }

    /**
     * Update toolbar button visual states based on cursor position and active formats.
     */
    private void updateToolbarActiveStates() {
        if (defaultToolbarButtons.isEmpty()) return;

        Editable editable = commentInput.getText();
        int pos = Math.max(commentInput.getSelectionStart(), 0);

        boolean boldActive = RichTextHelper.isBoldActive(editable, pos)
                || activeFormatsForNextChar.contains("bold");
        boolean italicActive = RichTextHelper.isItalicActive(editable, pos)
                || activeFormatsForNextChar.contains("italic");
        boolean codeActive = RichTextHelper.isCodeActive(editable, pos)
                || activeFormatsForNextChar.contains("code")
                || activeFormatsForNextChar.contains("codeblock");
        boolean linkActive = RichTextHelper.isLinkActive(editable, pos);

        setButtonActiveState(defaultToolbarButtons.get("bold"), boldActive);
        setButtonActiveState(defaultToolbarButtons.get("italic"), italicActive);
        setButtonActiveState(defaultToolbarButtons.get("code"), codeActive);
        setButtonActiveState(defaultToolbarButtons.get("link"), linkActive);
    }

    private void setButtonActiveState(ImageButton button, boolean active) {
        if (button == null) return;
        if (active) {
            button.setBackgroundColor(0x33000000);
        } else {
            TypedValue outValue = new TypedValue();
            getContext().getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackgroundBorderless, outValue, true);
            button.setBackgroundResource(outValue.resourceId);
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
        // Clear Glide targets
        if (editableImageGetter != null) {
            editableImageGetter.clearTargets();
        }
    }
}