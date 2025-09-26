package com.fastcomments.sdk.examples;

import android.content.Context;

import com.fastcomments.sdk.BottomCommentInputView;
import com.fastcomments.sdk.FastCommentsSDK;

/**
 * Comprehensive example showing how to configure and use custom toolbar buttons
 * in the FastComments Android SDK.
 */
public class ToolbarUsageExample {

    /**
     * Example 1: Configure global toolbar settings that apply to all comment inputs
     */
    public static void configureGlobalToolbarSettings(FastCommentsSDK sdk) {
        // Enable the toolbar globally
        sdk.setCommentToolbarEnabled(true);

        // Enable default formatting buttons (bold, italic, link, code)
        sdk.setDefaultFormattingButtonsEnabled(true);

        // Add custom buttons that will appear on all comment inputs
        sdk.addGlobalCustomToolbarButton(new GifPickerToolbarButton());
        sdk.addGlobalCustomToolbarButton(new EmojiPickerToolbarButton());
        sdk.addGlobalCustomToolbarButton(new MentionToolbarButton());
    }

    /**
     * Example 2: Configure toolbar settings for a specific comment input instance
     */
    public static void configureInstanceToolbarSettings(BottomCommentInputView inputView) {
        // Enable toolbar for this specific instance
        inputView.setToolbarVisible(true);

        // Enable default formatting buttons
        inputView.setDefaultFormattingEnabled(true);

        // Add custom buttons to this specific instance
        inputView.addCustomToolbarButton(new GifPickerToolbarButton());
        inputView.addCustomToolbarButton(new EmojiPickerToolbarButton());
    }

    /**
     * Example 3: Disable default formatting but keep custom buttons
     */
    public static void customButtonsOnly(FastCommentsSDK sdk) {
        // Enable toolbar
        sdk.setCommentToolbarEnabled(true);

        // Disable default formatting buttons
        sdk.setDefaultFormattingButtonsEnabled(false);

        // Add only custom buttons
        sdk.addGlobalCustomToolbarButton(new GifPickerToolbarButton());
        sdk.addGlobalCustomToolbarButton(new EmojiPickerToolbarButton());
    }

    /**
     * Example 4: Dynamically add/remove buttons based on user permissions
     */
    public static void dynamicButtonManagement(FastCommentsSDK sdk, boolean userCanAddGifs, boolean userCanMention) {
        // Clear existing custom buttons
        sdk.clearGlobalCustomToolbarButtons();

        // Always allow emojis
        sdk.addGlobalCustomToolbarButton(new EmojiPickerToolbarButton());

        // Conditionally add GIF button based on permissions
        if (userCanAddGifs) {
            sdk.addGlobalCustomToolbarButton(new GifPickerToolbarButton());
        }

        // Conditionally add mention button based on permissions
        if (userCanMention) {
            sdk.addGlobalCustomToolbarButton(new MentionToolbarButton());
        }
    }

    /**
     * Example 5: Remove specific buttons
     */
    public static void removeSpecificButtons(FastCommentsSDK sdk) {
        // Remove GIF button by ID
        sdk.removeGlobalCustomToolbarButton("gif_picker");

        // Or remove by button instance if you have a reference
        // sdk.removeGlobalCustomToolbarButton(gifPickerButton);
    }

    /**
     * Example 6: Complete setup in an Activity or Fragment
     */
    public static void completeSetupExample(Context context, FastCommentsSDK sdk, BottomCommentInputView inputView) {
        // 1. Configure global settings first
        configureGlobalToolbarSettings(sdk);

        // 2. Set the SDK on the input view (this applies global settings)
        inputView.setSDK(sdk);

        // 3. Optionally override settings for this specific instance
        // inputView.setDefaultFormattingEnabled(false); // Disable default buttons
        // inputView.addCustomToolbarButton(new SomeOtherCustomButton()); // Add instance-specific button

        // The toolbar will now be visible with both default formatting buttons
        // and custom buttons (GIF picker, emoji picker, mention button)
    }

    /**
     * Example 7: Programmatically insert content using the text manipulation API
     */
    public static void programmaticContentInsertion(BottomCommentInputView inputView) {
        // Insert plain text at cursor
        inputView.insertTextAtCursor("Hello world! ");

        // Insert HTML content
        inputView.insertHtmlAtCursor("<b>Bold text</b> ");

        // Wrap selected text with formatting
        inputView.wrapSelection("<i>", "</i>");

        // Replace selected text
        inputView.replaceSelection("New text");

        // Get current selection
        String selectedText = inputView.getSelectedText();

        // Move cursor to specific position
        inputView.setCursorPosition(10);
    }
}