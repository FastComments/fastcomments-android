package com.fastcomments.sdk;

import com.fastcomments.model.PublicComment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RenderableComment {


    private PublicComment comment;
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

    /**
     * Toggle the expanded state
     *
     * @return The new expanded state
     */
    public boolean toggleExpanded() {
        this.isRepliesShown = !this.isRepliesShown;
        return this.isRepliesShown;
    }

    /**
     * Build a tree of RenderableComment objects from a flat list of PublicComment objects.
     * The tree is "flat" at the top - children are still in order at the root - to make rendering easier.
     *
     * @param comments The flat list of comments
     * @return A list of root RenderableComment objects with proper nesting
     */
    public static List<RenderableComment> transformTreeToUIRenderableList(Map<String, RenderableComment> commentMap, List<PublicComment> comments) {
        List<RenderableComment> commentsTree = new ArrayList<>(commentMap.size());
        if (comments == null || comments.isEmpty()) {
            return commentsTree;
        }

        // Process all comments and create RenderableComment objects
        for (PublicComment comment : comments) {
            final RenderableComment renderableComment = new RenderableComment(comment);
            commentMap.put(comment.getId(), renderableComment);
            commentsTree.add(renderableComment);
            if (comment.getChildren() != null) {
                handleChildren(commentMap, commentsTree, comment.getChildren());
            }
        }

        return commentsTree;
    }

    private static void handleChildren(Map<String, RenderableComment> commentMap, List<RenderableComment> commentsTree, List<PublicComment> comments) {
        for (PublicComment child : comments) {
            final RenderableComment childRenderable = new RenderableComment(child);
            commentMap.put(child.getId(), childRenderable);
            commentsTree.add(childRenderable);
            if (child.getChildren() != null) {
                handleChildren(commentMap, commentsTree, child.getChildren());
            }
        }
    }


    public boolean isRepliesShown() {
        return isRepliesShown;
    }

    public void setRepliesShown(boolean repliesShown) {
        this.isRepliesShown = repliesShown;
    }
}
