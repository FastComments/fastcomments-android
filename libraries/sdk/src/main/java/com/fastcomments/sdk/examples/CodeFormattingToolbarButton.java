package com.fastcomments.sdk.examples;

import android.view.View;

import com.fastcomments.sdk.BottomCommentInputView;
import com.fastcomments.sdk.CustomToolbarButton;
import com.fastcomments.sdk.R;

/**
 * Example custom toolbar button that demonstrates code formatting functionality.
 * This button wraps selected text in HTML code tags or inserts code block formatting.
 */
public class CodeFormattingToolbarButton implements CustomToolbarButton {

    private static final String BUTTON_ID = "code_formatter";

    @Override
    public int getIconResourceId() {
        return R.drawable.ic_code;
    }

    @Override
    public int getContentDescriptionResourceId() {
        return R.string.format_code; // You may need to add this string resource
    }

    @Override
    public String getBadgeText() {
        return null; // No badge for this button
    }

    @Override
    public void onClick(BottomCommentInputView view, View buttonView) {
        String selectedText = view.getSelectedText();

        if (selectedText != null && !selectedText.trim().isEmpty()) {
            // If text is selected, wrap it in inline code tags
            view.wrapSelection("<code>", "</code>");
        }
        // If no text is selected, do nothing (like other formatting buttons)
    }

    @Override
    public boolean onLongClick(BottomCommentInputView view, View buttonView) {
        // Long press creates a multi-line code block
        String selectedText = view.getSelectedText();

        if (selectedText != null && !selectedText.trim().isEmpty()) {
            // Wrap selected text in a pre-formatted code block
            view.wrapSelection("<pre><code>", "</code></pre>");
            return true; // Indicate that we handled the long press
        }

        return false; // No selection, let default behavior handle it
    }

    @Override
    public String getId() {
        return BUTTON_ID;
    }

    @Override
    public boolean isEnabled(BottomCommentInputView view) {
        return true; // Always enabled
    }

    @Override
    public boolean isVisible(BottomCommentInputView view) {
        return true; // Always visible
    }

    @Override
    public void onAttached(BottomCommentInputView view, View buttonView) {
        // Optional: Set up any listeners or initialize state when button is added
    }

    @Override
    public void onDetached(BottomCommentInputView view, View buttonView) {
        // Optional: Clean up any resources when button is removed
    }
}