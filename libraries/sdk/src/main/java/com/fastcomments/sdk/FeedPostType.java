package com.fastcomments.sdk;

/**
 * Defines the different types of feed post layouts supported
 */
public enum FeedPostType {
    /**
     * Simple text-only post
     */
    TEXT_ONLY,
    
    /**
     * Post with a single image and optional text
     */
    SINGLE_IMAGE,
    
    /**
     * Post with multiple images/media and optional text
     */
    MULTI_IMAGE,
    
    /**
     * Task or activity post with custom action buttons
     */
    TASK
}