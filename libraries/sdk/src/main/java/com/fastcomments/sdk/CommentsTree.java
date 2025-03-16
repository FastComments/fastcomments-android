package com.fastcomments.sdk;

import com.fastcomments.model.PublicComment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommentsTree {

    public Map<String, RenderableComment> commentsById;
    public List<RenderableComment> allComments;
    public List<RenderableComment> visibleComments;
    private CommentsAdapter adapter;

    public CommentsTree() {
        this.commentsById = new HashMap<>(30);
        this.visibleComments = new ArrayList<>(30);
    }

    public void setAdapter(CommentsAdapter adapter) {
        this.adapter = adapter;
    }

    public void build(List<PublicComment> comments) {
        List<RenderableComment> allComments = new ArrayList<>(commentsById.size());
        List<RenderableComment> visibleComments = new ArrayList<>(commentsById.size());
        if (comments == null || comments.isEmpty()) {
            this.visibleComments = visibleComments;
            return;
        }

        // Process all comments and create RenderableComment objects
        for (PublicComment comment : comments) {
            final RenderableComment renderableComment = new RenderableComment(comment);
            commentsById.put(comment.getId(), renderableComment);
            allComments.add(renderableComment);
            visibleComments.add(renderableComment);
            if (comment.getChildren() != null) {
                handleChildren(allComments, visibleComments, comment.getChildren(), renderableComment.isRepliesShown());
            }
        }

        this.allComments = allComments;
        this.visibleComments = visibleComments;
    }

    private void handleChildren(List<RenderableComment> allComments, List<RenderableComment> visibleComments, List<PublicComment> comments, boolean visible) {
        for (PublicComment child : comments) {
            final RenderableComment childRenderable = new RenderableComment(child);
            commentsById.put(child.getId(), childRenderable);
            allComments.add(childRenderable);
            final boolean childrenVisible = visible && childRenderable.isRepliesShown();
            if (childrenVisible) {
                visibleComments.add(childRenderable);
            }
            final List<PublicComment> children = child.getChildren();
            if (children != null) {
                handleChildren(allComments, visibleComments, children, childrenVisible);
            }
        }
    }

    public int visibleSize() {
        return visibleComments.size();
    }

    public void setRepliesVisible(RenderableComment renderableComment, boolean areRepliesVisible) {
        final boolean wasRepliesVisible = renderableComment.isRepliesShown();
        if (wasRepliesVisible == areRepliesVisible) {
            return;
        }
        renderableComment.setRepliesShown(areRepliesVisible);
        if (areRepliesVisible) {
            if (renderableComment.getComment().getChildren() != null) {
                for (PublicComment child : renderableComment.getComment().getChildren()) {
                    final RenderableComment childRenderable = commentsById.get(child.getId());
                    final int index = allComments.indexOf(childRenderable);
                    visibleComments.add(index, childRenderable);
                }
            }
        } else {
            if (renderableComment.getComment().getChildren() != null) {
                for (PublicComment child : renderableComment.getComment().getChildren()) {
                    final RenderableComment childRenderable = commentsById.get(child.getId());
                    visibleComments.remove(childRenderable);
                }
            }
        }
    }
}
