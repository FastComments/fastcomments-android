package com.fastcomments.sdk;

import com.fastcomments.model.UserSessionInfo;

import java.util.List;

/**
 * Interface for providing tags to filter feed posts.
 * The tags returned by this supplier will be used to:
 * 1. Filter the feed when fetching posts
 * 2. Automatically include when creating new posts
 */
public interface TagSupplier {
    /**
     * Get the list of tags to use for filtering feed posts.
     * This method is called when loading the feed and when creating new posts.
     * 
     * @param currentUser The current authenticated user, or null if not authenticated
     * @return List of tags to filter by, or null/empty list for no filtering
     */
    List<String> getTags(UserSessionInfo currentUser);
}