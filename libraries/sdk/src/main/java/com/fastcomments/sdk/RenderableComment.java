package com.fastcomments.sdk;

public class RenderableComment {

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    private APICommentPublicComment comment;
    private int nestingLevel;
    private RenderableComment parent;
    private List<RenderableComment> children;
    private boolean expanded = true;

    /**
     * Create a new renderable comment
     *
     * @param comment The API comment object
     * @param nestingLevel The nesting level (0 for root comments)
     */
    public RenderableComment(APICommentPublicComment comment, int nestingLevel) {
        this.comment = comment;
        this.nestingLevel = nestingLevel;
        this.children = new ArrayList<>();
    }

    /**
     * Create a new renderable comment with a parent
     *
     * @param comment The API comment object
     * @param parent The parent comment
     */
    public RenderableComment(APICommentPublicComment comment, RenderableComment parent) {
        this.comment = comment;
        this.parent = parent;
        this.nestingLevel = parent != null ? parent.getNestingLevel() + 1 : 0;
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

    /**
     * Get the indent margin for this comment based on nesting level
     *
     * @param baseIndent The base indent value for each level
     * @return The total indent margin
     */
    public int getIndentMargin(int baseIndent) {
        return nestingLevel * baseIndent;
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
    public int getTotalCount() {
        int count = 1; // This comment

        if (children != null) {
            for (RenderableComment child : children) {
                count += child.getTotalCount();
            }
        }

        return count;
    }

    /**
     * Build a tree of RenderableComment objects from a flat list of APICommentPublicComment objects
     *
     * @param comments The flat list of comments
     * @return A list of root RenderableComment objects with proper nesting
     */
    public static List<RenderableComment> buildCommentTree(List<APICommentPublicComment> comments) {
        if (comments == null || comments.isEmpty()) {
            return new ArrayList<>();
        }

        // First pass - create renderable comments and store in a map by their ID
        Map<String, RenderableComment> commentMap = new HashMap<>();

        // Process all comments and create RenderableComment objects
        for (APICommentPublicComment comment : comments) {
            String commentId = getCommentId(comment);
            if (commentId != null) {
                commentMap.put(commentId, new RenderableComment(comment, 0));
            }
        }

        // Second pass - build the tree by connecting parents and children
        List<RenderableComment> rootComments = new ArrayList<>();

        for (APICommentPublicComment comment : comments) {
            String commentId = getCommentId(comment);
            String parentId = getParentId(comment);

            RenderableComment renderableComment = commentMap.get(commentId);
            if (renderableComment == null) continue;

            if (parentId == null || parentId.isEmpty()) {
                // This is a root comment
                rootComments.add(renderableComment);
            } else {
                // This is a child comment
                RenderableComment parentComment = commentMap.get(parentId);
                if (parentComment != null) {
                    parentComment.addChild(renderableComment);
                } else {
                    // Parent not found, treat as root
                    rootComments.add(renderableComment);
                }
            }
        }

        return rootComments;
    }

    /**
     * Get the comment ID from an APICommentPublicComment object
     *
     * @param comment The comment object
     * @return The comment ID or null if not available
     */
    private static String getCommentId(APICommentPublicComment comment) {
        // This would need to be implemented based on the actual structure of APICommentPublicComment
        // Placeholder implementation - actual field names may vary
        try {
            java.lang.reflect.Field field = comment.getClass().getDeclaredField("id");
            field.setAccessible(true);
            return (String) field.get(comment);
        } catch (Exception e) {
            // Handle reflection exceptions
            return null;
        }
    }

    /**
     * Get the parent ID from an APICommentPublicComment object
     *
     * @param comment The comment object
     * @return The parent ID or null if not available
     */
    private static String getParentId(APICommentPublicComment comment) {
        // This would need to be implemented based on the actual structure of APICommentPublicComment
        // Placeholder implementation - actual field names may vary
        try {
            java.lang.reflect.Field field = comment.getClass().getDeclaredField("parentId");
            field.setAccessible(true);
            return (String) field.get(comment);
        } catch (Exception e) {
            // Handle reflection exceptions
            return null;
        }
    }

    // Getters and setters

    public APICommentPublicComment getComment() {
        return comment;
    }

    public void setComment(APICommentPublicComment comment) {
        this.comment = comment;
    }

    public int getNestingLevel() {
        return nestingLevel;
    }

    public void setNestingLevel(int nestingLevel) {
        this.nestingLevel = nestingLevel;
    }

    public RenderableComment getParent() {
        return parent;
    }

    public void setParent(RenderableComment parent) {
        this.parent = parent;
        this.nestingLevel = parent != null ? parent.getNestingLevel() + 1 : 0;
    }

    public List<RenderableComment> getChildren() {
        return children;
    }

    public void setChildren(List<RenderableComment> children) {
        this.children = children;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }
}
