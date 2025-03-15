package com.fastcomments.sdk;

import com.fastcomments.model.PublicComment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RenderableComment {


    private PublicComment comment;
    private RenderableComment parent;
    private List<RenderableComment> children;
    private boolean expanded = true;
    private static int BASE_INDENT = 50;
    public int nestingLevel;

    public PublicComment getComment() {
        return comment;
    }

    /**
     * Create a new renderable comment with a parent
     *
     * @param comment The API comment object
     * @param parent  The parent comment
     */
    public RenderableComment(PublicComment comment, RenderableComment parent) {
        this.comment = comment;
        this.parent = parent;
        this.nestingLevel = determineNestingLevel(parent);
        this.children = new ArrayList<>();
    }

    /**
     * Add a child comment
     *
     * @param child The child comment to add
     */
    public void addChild(RenderableComment child) {
        children.add(child);
        child.setParent(this);
    }

    public List<RenderableComment> getChildren() {
        return children;
    }

    public void setParent(RenderableComment parent) {
        this.parent = parent;
        this.nestingLevel = determineNestingLevel(parent);
    }

    private int determineNestingLevel(RenderableComment parent) {
        return parent != null ? parent.nestingLevel + 1 : 0;
    }

    public int getIndentMargin() {
        return nestingLevel * BASE_INDENT;
    }

    /**
     * Toggle the expanded state
     *
     * @return The new expanded state
     */
    public boolean toggleExpanded() {
        this.expanded = !this.expanded;
        return this.expanded;
    }

    /**
     * Recursively get all visible comments (this comment and all expanded children)
     *
     * @return List of visible comments
     */
    public List<RenderableComment> getVisibleComments() {
        List<RenderableComment> result = new ArrayList<>();
        result.add(this);

        if (expanded && children != null && !children.isEmpty()) {
            for (RenderableComment child : children) {
                result.addAll(child.getVisibleComments());
            }
        }

        return result;
    }

    /**
     * Get the total number of comments in this thread (including this one)
     *
     * @return The total comment count
     */
    public int getChildCount() {
        int count = 0;

        if (children != null) {
            for (RenderableComment child : children) {
                count += child.getChildCount();
            }
        }

        return count;
    }

    /**
     * Build a tree of RenderableComment objects from a flat list of PublicComment objects
     *
     * @param comments The flat list of comments
     * @return A list of root RenderableComment objects with proper nesting
     */
    public static List<RenderableComment> buildCommentTree(Map<String, RenderableComment> commentMap, List<PublicComment> comments) {
        if (comments == null || comments.isEmpty()) {
            return new ArrayList<>();
        }

        // Process all comments and create RenderableComment objects
        for (PublicComment comment : comments) {
            commentMap.put(comment.getId(), new RenderableComment(comment, null));
        }

        // Second pass - build the tree by connecting parents and children
        List<RenderableComment> rootComments = new ArrayList<>();

        for (PublicComment comment : comments) {
            final String commentId = comment.getId();
            final String parentId = comment.getParentId();

            RenderableComment renderableComment = commentMap.get(commentId);
            if (renderableComment == null) continue;

            if (parentId == null || parentId.isEmpty()) {
                rootComments.add(renderableComment);
            } else {
                // This is a child comment
                RenderableComment parentComment = commentMap.get(parentId);
                //noinspection StatementWithEmptyBody
                if (parentComment != null) {
                    parentComment.addChild(renderableComment);
                } else {
                    // Parent not found, ignore
                }
            }
        }

        return rootComments;
    }


    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }
}
