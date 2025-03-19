package com.fastcomments.sdk;

import com.fastcomments.model.PublicComment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a comment that can be rendered in the comment list
 */
public class RenderableComment extends RenderableNode {

    private final PublicComment comment;
    public boolean isRepliesShown = false;
    
    // Pagination state for child comments
    public int childSkip = 0;
    public int childPage = 0;
    public int childPageSize = 5; // Smaller page size for replies
    public boolean hasMoreChildren = false;
    public boolean isLoadingChildren = false;
    // Tracks new child comments that haven't been shown due to showLiveRightAway=false
    // Only initialized when needed to save memory
    public List<PublicComment> newChildComments = null;

    public PublicComment getComment() {
        return comment;
    }

    /**
     * Create a new renderable comment with a parent
     *
     * @param comment The API comment object
     */
    public RenderableComment(PublicComment comment) {
        this.comment = comment;
    }

    @Override
    public int determineNestingLevel(Map<String, RenderableComment> commentMap) {
        final String parentId = getComment().getParentId();
        if (parentId == null) {
            return 0;
        }
        final RenderableComment parent = commentMap.get(parentId);
        if (parent == null) {
            return 1;
        }
        return 1 + parent.determineNestingLevel(commentMap);
    }
    
    /**
     * Reset child pagination state, typically called when hiding replies
     */
    public void resetChildPagination() {
        this.childSkip = 0;
        this.childPage = 0;
        this.isLoadingChildren = false;
    }
    
    /**
     * Get the remaining child count that can be loaded in the next page
     *
     * @return The count of remaining children to load
     */
    public int getRemainingChildCount() {
        Integer childCount = getComment().getChildCount();
        if (childCount == null) {
            return 0;
        }
        
        int loadedCount = 0;
        if (getComment().getChildren() != null) {
            loadedCount = getComment().getChildren().size();
        }
        
        int remaining = childCount - loadedCount;
        return Math.min(Math.max(remaining, 0), childPageSize);
    }
    
    /**
     * Add a new child comment that hasn't been shown yet
     * 
     * @param childComment The new child comment
     */
    public void addNewChildComment(PublicComment childComment) {
        if (newChildComments == null) {
            newChildComments = new ArrayList<>();
        }
        newChildComments.add(childComment);
    }
    
    /**
     * Get the count of new child comments that haven't been shown yet
     * 
     * @return The count of new child comments
     */
    public int getNewChildCommentsCount() {
        return newChildComments == null ? 0 : newChildComments.size();
    }
    
    /**
     * Get the list of new child comments and clear the buffer
     * 
     * @return The list of new child comments, or null if none
     */
    public List<PublicComment> getAndClearNewChildComments() {
        if (newChildComments == null || newChildComments.isEmpty()) {
            return null;
        }
        
        List<PublicComment> result = newChildComments;
        newChildComments = null;
        return result;
    }
}
