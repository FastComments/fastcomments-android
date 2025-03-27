package com.fastcomments.sdk;

import java.util.Map;

/**
 * Represents a button to load new comments
 */
public class RenderableButton extends RenderableNode {
    public static final int TYPE_NEW_ROOT_COMMENTS = 1;
    public static final int TYPE_NEW_CHILD_COMMENTS = 2;
    
    private final int buttonType;
    private final int commentCount;
    private final String parentId; // Only used for TYPE_NEW_CHILD_COMMENTS
    
    public RenderableButton(int buttonType, int commentCount) {
        this(buttonType, commentCount, null);
    }
    
    public RenderableButton(int buttonType, int commentCount, String parentId) {
        this.buttonType = buttonType;
        this.commentCount = commentCount;
        this.parentId = parentId;
    }
    
    public int getButtonType() {
        return buttonType;
    }
    
    public int getCommentCount() {
        return commentCount;
    }
    
    public String getParentId() {
        return parentId;
    }
    
    @Override
    public int determineNestingLevel(Map<String, RenderableComment> commentMap) {
        if (buttonType == TYPE_NEW_ROOT_COMMENTS) {
            return 0; // Root level for new comments button
        } else if (buttonType == TYPE_NEW_CHILD_COMMENTS && parentId != null) {
            // For reply buttons, use parent's nesting level + 1
            RenderableComment parent = commentMap.get(parentId);
            if (parent != null) {
                return parent.determineNestingLevel(commentMap) + 1;
            }
        }
        return 0;
    }
}