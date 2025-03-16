package com.fastcomments.sdk;

import com.fastcomments.model.PublicComment;

import java.util.Map;

public class RenderableComment {


    private final PublicComment comment;
    private boolean isRepliesShown = false;

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


    public boolean isRepliesShown() {
        return isRepliesShown;
    }

    public void setRepliesShown(boolean repliesShown) {
        this.isRepliesShown = repliesShown;
    }
}
