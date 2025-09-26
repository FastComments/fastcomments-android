package com.fastcomments.sdk;

import android.view.View;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Interface for custom toolbar buttons that can be added to the feed post creation toolbar.
 * Developers can implement this interface to create custom functionality like GIF pickers,
 * emoji selectors, or any other interactive elements for feed posts.
 */
public interface FeedCustomToolbarButton {

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
     * @param view The FeedPostCreateView instance containing this button
     * @param buttonView The actual button view that was clicked
     */
    void onClick(FeedPostCreateView view, View buttonView);

    /**
     * Get a unique identifier for this button (used for removal)
     *
     * @return Unique identifier string
     */
    String getId();

    /**
     * Check if this button should be enabled based on current state
     *
     * @param view The FeedPostCreateView instance
     * @return true if button should be enabled, false otherwise
     */
    default boolean isEnabled(FeedPostCreateView view) {
        return true;
    }

    /**
     * Check if this button should be visible based on current state
     *
     * @param view The FeedPostCreateView instance
     * @return true if button should be visible, false otherwise
     */
    default boolean isVisible(FeedPostCreateView view) {
        return true;
    }

    /**
     * Called when the button is added to the toolbar (optional setup)
     *
     * @param view The FeedPostCreateView instance
     * @param buttonView The button view that was created
     */
    default void onAttached(FeedPostCreateView view, View buttonView) {
        // Default implementation does nothing
    }

    /**
     * Called when the button is removed from the toolbar (optional cleanup)
     *
     * @param view The FeedPostCreateView instance
     * @param buttonView The button view that was removed
     */
    default void onDetached(FeedPostCreateView view, View buttonView) {
        // Default implementation does nothing
    }
}