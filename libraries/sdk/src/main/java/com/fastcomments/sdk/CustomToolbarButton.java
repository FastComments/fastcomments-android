package com.fastcomments.sdk;

import android.view.View;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Interface for custom toolbar buttons that can be added to the comment input toolbar.
 * Developers can implement this interface to create custom functionality like GIF pickers,
 * emoji selectors, or any other interactive elements.
 */
public interface CustomToolbarButton {

    /**
     * Get the icon resource ID for this button
     *
     * @return Drawable resource ID for the button icon
     */
    @DrawableRes
    int getIconResourceId();

    /**
     * Get the content description for accessibility
     *
     * @return String resource ID for content description, or 0 if none
     */
    @StringRes
    int getContentDescriptionResourceId();

    /**
     * Get optional badge text to display on the button
     *
     * @return Badge text or null if no badge should be displayed
     */
    @Nullable
    String getBadgeText();

    /**
     * Called when the button is clicked
     *
     * @param view The BottomCommentInputView instance containing this button
     * @param buttonView The actual button view that was clicked
     */
    void onClick(BottomCommentInputView view, View buttonView);

    /**
     * Called when the button is long-pressed (optional)
     *
     * @param view The BottomCommentInputView instance containing this button
     * @param buttonView The actual button view that was long-pressed
     * @return true if the long press was handled, false otherwise
     */
    default boolean onLongClick(BottomCommentInputView view, View buttonView) {
        return false;
    }

    /**
     * Get a unique identifier for this button (used for removal)
     *
     * @return Unique identifier string
     */
    String getId();

    /**
     * Check if this button should be enabled based on current state
     *
     * @param view The BottomCommentInputView instance
     * @return true if button should be enabled, false otherwise
     */
    default boolean isEnabled(BottomCommentInputView view) {
        return true;
    }

    /**
     * Check if this button should be visible based on current state
     *
     * @param view The BottomCommentInputView instance
     * @return true if button should be visible, false otherwise
     */
    default boolean isVisible(BottomCommentInputView view) {
        return true;
    }

    /**
     * Called when the button is added to the toolbar (optional setup)
     *
     * @param view The BottomCommentInputView instance
     * @param buttonView The button view that was created
     */
    default void onAttached(BottomCommentInputView view, View buttonView) {
        // Default implementation does nothing
    }

    /**
     * Called when the button is removed from the toolbar (optional cleanup)
     *
     * @param view The BottomCommentInputView instance
     * @param buttonView The button view that was removed
     */
    default void onDetached(BottomCommentInputView view, View buttonView) {
        // Default implementation does nothing
    }
}