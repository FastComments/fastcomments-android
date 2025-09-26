package com.fastcomments.sdk.examples;

import android.view.View;

import com.fastcomments.sdk.BottomCommentInputView;
import com.fastcomments.sdk.CustomToolbarButton;
import com.fastcomments.sdk.R;

/**
 * Example custom toolbar button that demonstrates how to trigger the mention functionality.
 * This button inserts an @ symbol and triggers the mention user search.
 */
public class MentionToolbarButton implements CustomToolbarButton {

    private static final String BUTTON_ID = "mention_button";

    @Override
    public int getIconResourceId() {
        // Use a built-in Android icon or create your own mention icon
        return android.R.drawable.ic_menu_add;
    }

    @Override
    public int getContentDescriptionResourceId() {
        // You would add this string to your strings.xml
        return R.string.mention_user; // "Mention User"
    }

    @Override
    public String getBadgeText() {
        return null; // No badge for this button
    }

    @Override
    public void onClick(BottomCommentInputView view, View buttonView) {
        // Insert @ symbol which will trigger the mention functionality
        view.insertTextAtCursor("@");

        // Request focus on the input to show keyboard and start mention search
        view.requestInputFocus();
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
}