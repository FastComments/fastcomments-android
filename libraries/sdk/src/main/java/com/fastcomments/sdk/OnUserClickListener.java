package com.fastcomments.sdk;

/**
 * Interface for handling user click events on names and avatars.
 * Uses a unified approach with a single method and context object.
 */
public interface OnUserClickListener {
    
    /**
     * Called when a user is clicked in any context (comments or feed posts)
     * 
     * @param context The context containing either a comment or feed post
     * @param userInfo Information about the user that was clicked
     * @param source Whether the click came from the name or avatar
     */
    void onUserClicked(UserClickContext context, UserInfo userInfo, UserClickSource source);
}