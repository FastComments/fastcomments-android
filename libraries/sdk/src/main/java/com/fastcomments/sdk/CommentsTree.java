package com.fastcomments.sdk;

import com.fastcomments.model.PublicComment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * The way the RecyclerView works is that when it needs to load an element at an index it calls onBindViewHolder(index)
 * To keep this as an n-time lookup without hashmaps it means we need to maintain an array of visible items.
 * When adding or removing items, we could rebuild the whole tree each time, but this would add to lag during certain operations, and
 * for live sessions would drain the user's battery with rebuilding the tree all the time.
 * <p>
 * So we have efficient implements for each common operation (toggling replies, adding/removing comments).
 */
public class CommentsTree {

    public Map<String, RenderableComment> commentsById; // all data including invisible
    public List<RenderableComment> allComments; // in any order
    public List<RenderableComment> visibleComments; // in view order
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
                handleChildren(allComments, visibleComments, comment.getChildren(), renderableComment.isRepliesShown);
            }
        }

        this.allComments = allComments;
        this.visibleComments = visibleComments;
    }

    /**
     * Append new comments to the existing tree (for pagination)
     *
     * @param comments The new comments to append
     */
    public void appendComments(List<PublicComment> comments) {
        if (comments == null || comments.isEmpty()) {
            return;
        }

        int initialSize = visibleComments.size();

        // Process all comments and create RenderableComment objects
        for (PublicComment comment : comments) {
            if (!commentsById.containsKey(comment.getId())) {
                final RenderableComment renderableComment = new RenderableComment(comment);
                commentsById.put(comment.getId(), renderableComment);
                allComments.add(renderableComment);
                visibleComments.add(renderableComment);
                if (comment.getChildren() != null) {
                    handleChildren(allComments, visibleComments, comment.getChildren(), renderableComment.isRepliesShown);
                }
            }
        }

        // Notify the adapter of exactly what changed (the newly added comments) TODO doesn't work right
//        int itemCount = visibleComments.size() - initialSize;
//        adapter.notifyItemRangeInserted(initialSize, itemCount);
        adapter.notifyDataSetChanged();
    }

    public void addForParent(String parentId, List<PublicComment> comments) {
        // this is structured this way to limit pointer indirection/hashmap lookups.
        final RenderableComment parent = parentId != null ? commentsById.get(parentId) : null;
        List<PublicComment> children = parent != null ? parent.getComment().getChildren() : null;
        
        // For pagination, we need to either create a new list or append to the existing one
        if (parent != null) {
            boolean isPagination = parent.childPage > 0;
            
            if (children == null) {
                children = new ArrayList<>(comments.size());
                parent.getComment().setChildren(children);
            } else if (isPagination) {
                // If this is a pagination request, we don't want to replace, just add to existing list
                // No need to do anything with the list reference, as it's already set in the parent
            }
            
            // Update the hasMoreChildren flag to reflect whether there are more replies to load
            if (parent.getComment().getChildCount() != null) {
                int totalChildCount = parent.getComment().getChildCount();
                int loadedChildCount = (children.size() + comments.size());
                parent.hasMoreChildren = (loadedChildCount < totalChildCount);
            }
        }
        
        for (PublicComment comment : comments) {
            final RenderableComment childRenderable = new RenderableComment(comment);
            commentsById.put(comment.getId(), childRenderable);
            allComments.add(childRenderable);
            if (children != null) {
                children.add(comment);
            }
        }
    }

    private void handleChildren(List<RenderableComment> allComments, List<RenderableComment> visibleComments, List<PublicComment> comments, boolean visible) {
        for (PublicComment child : comments) {
            final RenderableComment childRenderable = new RenderableComment(child);
            commentsById.put(child.getId(), childRenderable);
            allComments.add(childRenderable);
            final boolean childrenVisible = visible && childRenderable.isRepliesShown;
            if (childrenVisible) {
                visibleComments.add(childRenderable);
            }
            final List<PublicComment> children = child.getChildren();
            if (children != null) {
                handleChildren(allComments, visibleComments, children, childrenVisible);
            }
        }
    }

    public int totalSize() {
        return allComments.size();
    }

    public int visibleSize() {
        return visibleComments.size();
    }

    private void removeChildren(List<PublicComment> publicComments) {
        for (PublicComment child : publicComments) {
            final RenderableComment childRenderable = commentsById.get(child.getId());
            // see explanation at top of class
            visibleComments.remove(childRenderable);
            if (child.getChildren() != null) {
                removeChildren(child.getChildren());
            }
        }
    }

    public void setRepliesVisible(RenderableComment renderableComment, boolean areRepliesVisible, Producer<GetChildrenRequest, List<PublicComment>> getChildren) {
        final boolean wasRepliesVisible = renderableComment.isRepliesShown;
        if (wasRepliesVisible == areRepliesVisible) {
            return;
        }
        renderableComment.isRepliesShown = areRepliesVisible;
        final List<PublicComment> children = renderableComment.getComment().getChildren();
        if (areRepliesVisible) {
            if (children != null && !children.isEmpty()) {
                insertChildrenAfter(renderableComment, children);
                // Set hasMoreChildren based on child count vs. loaded children count
                if (renderableComment.getComment().getChildCount() != null) {
                    int totalChildCount = renderableComment.getComment().getChildCount();
                    int loadedChildCount = children.size();
                    renderableComment.hasMoreChildren = (loadedChildCount < totalChildCount);
                }
            } else if (Boolean.TRUE.equals(renderableComment.getComment().getHasChildren())) {
                // Reset pagination state when showing replies
                renderableComment.childPage = 0;
                renderableComment.childSkip = 0;
                renderableComment.isLoadingChildren = true;
                
                // Create GetChildrenRequest with the comment ID and the toggle button
                // Note: We can't directly get the button here, so we'll handle button reference via the adapter
                GetChildrenRequest request = new GetChildrenRequest(
                        renderableComment.getComment().getId(), 
                        null, 
                        0, 
                        renderableComment.childPageSize, 
                        false
                );
                
                getChildren.get(request, (asyncFetchedChildren) -> {
                    renderableComment.isLoadingChildren = false;
                    insertChildrenAfter(renderableComment, asyncFetchedChildren);
                    
                    // Set hasMoreChildren based on child count vs. loaded children count
                    if (renderableComment.getComment().getChildCount() != null) {
                        int totalChildCount = renderableComment.getComment().getChildCount();
                        int loadedChildCount = renderableComment.getComment().getChildren() != null ? 
                                renderableComment.getComment().getChildren().size() : 0;
                        renderableComment.hasMoreChildren = (loadedChildCount < totalChildCount);
                    }
                });
            }
        } else {
            if (children != null) {
                int myIndex = visibleComments.indexOf(renderableComment);
                removeChildren(children);
                adapter.notifyItemRangeChanged(myIndex, totalSize() - myIndex); // everything after me has changed/moved since it's a flat list
                
                // Reset pagination state when hiding replies
                renderableComment.resetChildPagination();
            }
        }
    }

    public void insertChildrenAfter(RenderableComment renderableComment, List<PublicComment> children) {
        int myIndex = visibleComments.indexOf(renderableComment);
        int indexer = myIndex + 1;
        for (int i = children.size() - 1; i >= 0; i--) {
            final PublicComment child = children.get(i);
            final RenderableComment childRenderable = commentsById.get(child.getId());
            // see explanation at top of class
            visibleComments.add(indexer, childRenderable);
        }
        adapter.notifyItemRangeChanged(myIndex, totalSize() - myIndex); // everything after me has changed/moved since it's a flat list
    }
    
    /**
     * Add a new comment to the tree (from live events)
     *
     * @param comment The comment to add
     */
    public void addComment(PublicComment comment) {
        if (comment == null || commentsById.containsKey(comment.getId())) {
            return;
        }
        
        // Create a new renderable comment
        RenderableComment renderableComment = new RenderableComment(comment);
        commentsById.put(comment.getId(), renderableComment);
        allComments.add(renderableComment);
        
        if (comment.getParentId() != null && !comment.getParentId().isEmpty()) {
            // This is a reply to an existing comment
            RenderableComment parent = commentsById.get(comment.getParentId());
            if (parent != null) {
                // Add this comment as a child of its parent
                if (parent.getComment().getChildren() == null) {
                    parent.getComment().setChildren(new ArrayList<>());
                }
                parent.getComment().getChildren().add(comment);
                
                // Increment the parent's child count if it exists
                if (parent.getComment().getChildCount() != null) {
                    parent.getComment().setChildCount(parent.getComment().getChildCount() + 1);
                } else {
                    parent.getComment().setChildCount(1);
                }
                
                // Set hasChildren flag if needed
                if (Boolean.FALSE.equals(parent.getComment().getHasChildren())) {
                    parent.getComment().setHasChildren(true);
                }
                
                // If the parent's replies are shown, add this comment to the visible list
                if (parent.isRepliesShown) {
                    int parentIndex = visibleComments.indexOf(parent);
                    if (parentIndex >= 0) {
                        // Find the right position to insert based on the hierarchical structure
                        int insertIndex = findInsertIndexForChild(parentIndex, parent);
                        visibleComments.add(insertIndex, renderableComment);
                        
                        // Notify adapter about the insertion
                        if (adapter != null) {
                            adapter.notifyItemInserted(insertIndex);
                        }
                    }
                }
            }
        } else {
            // This is a top-level comment
            // Add to the beginning or end of the visible list based on sort direction
            // For simplicity, we'll add to the beginning for now (usually newest first)
            visibleComments.add(0, renderableComment);
            
            // Notify adapter about the insertion
            if (adapter != null) {
                adapter.notifyItemInserted(0);
            }
        }
    }
    
    /**
     * Find the appropriate index to insert a child comment
     */
    private int findInsertIndexForChild(int parentIndex, RenderableComment parent) {
        // Find the last visible child of this parent
        int insertIndex = parentIndex + 1;
        int depth = parent.depth + 1;
        
        for (int i = parentIndex + 1; i < visibleComments.size(); i++) {
            RenderableComment current = visibleComments.get(i);
            if (current.depth < depth) {
                break;
            }
            insertIndex = i + 1;
        }
        
        return insertIndex;
    }
    
    /**
     * Find a comment by ID
     *
     * @param commentId The ID of the comment to find
     * @return The PublicComment object, or null if not found
     */
    public PublicComment findComment(String commentId) {
        RenderableComment renderableComment = commentsById.get(commentId);
        return renderableComment != null ? renderableComment.getComment() : null;
    }
    
    /**
     * Remove a comment from the tree (from live events)
     *
     * @param commentId The ID of the comment to remove
     * @return true if the comment was found and removed, false otherwise
     */
    public boolean removeComment(String commentId) {
        RenderableComment comment = commentsById.get(commentId);
        if (comment == null) {
            return false;
        }
        
        // Find the comment's index in the visible list
        int visibleIndex = visibleComments.indexOf(comment);
        
        // Remove from main collections
        commentsById.remove(commentId);
        allComments.remove(comment);
        
        // Handle parent's child count if this is a reply
        String parentId = comment.getComment().getParentId();
        if (parentId != null && !parentId.isEmpty()) {
            RenderableComment parent = commentsById.get(parentId);
            if (parent != null && parent.getComment().getChildren() != null) {
                // Remove from parent's children list
                List<PublicComment> children = parent.getComment().getChildren();
                for (int i = 0; i < children.size(); i++) {
                    if (commentId.equals(children.get(i).getId())) {
                        children.remove(i);
                        break;
                    }
                }
                
                // Update parent's child count
                if (parent.getComment().getChildCount() != null && parent.getComment().getChildCount() > 0) {
                    parent.getComment().setChildCount(parent.getComment().getChildCount() - 1);
                }
                
                // Update hasChildren flag if needed
                if (parent.getComment().getChildCount() != null && parent.getComment().getChildCount() == 0) {
                    parent.getComment().setHasChildren(false);
                }
            }
        }
        
        // If comment was visible, remove it and its children from visible list
        if (visibleIndex >= 0) {
            // First, find all visible child comments to remove
            List<Integer> indicesToRemove = new ArrayList<>();
            indicesToRemove.add(visibleIndex);
            
            // Find any children that need to be removed
            if (comment.getComment().getChildren() != null && comment.isRepliesShown) {
                int depth = comment.depth;
                for (int i = visibleIndex + 1; i < visibleComments.size(); i++) {
                    RenderableComment possibleChild = visibleComments.get(i);
                    if (possibleChild.depth <= depth) {
                        break;
                    }
                    indicesToRemove.add(i);
                }
            }
            
            // Remove items in reverse order to maintain correct indices
            for (int i = indicesToRemove.size() - 1; i >= 0; i--) {
                int indexToRemove = indicesToRemove.get(i);
                visibleComments.remove(indexToRemove);
                
                // Notify adapter about the removal
                if (adapter != null) {
                    adapter.notifyItemRemoved(indexToRemove);
                }
            }
        }
        
        return true;
    }
}
