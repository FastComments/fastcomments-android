package com.fastcomments.sdk;

import android.util.Log;
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
    // Note that lots of operations have to do N-time lookups in these lists. We may want to replace these
    // with some sort of ordered map.
    public List<RenderableComment> allComments = new ArrayList<>(0); // in any order
    public List<RenderableNode> visibleNodes = new ArrayList<>(0); // in view order - can include comments and buttons
    private CommentsAdapter adapter;
    
    // Separate collections for easier lookup
    private RenderableButton newRootCommentsButton; // Only one of these at most
    private final Map<String, RenderableButton> newChildCommentsButtons; // Keyed by parent comment ID
    private final List<PublicComment> newRootComments; // Buffer for new root comments when showLiveRightAway is false

    public CommentsTree() {
        this.commentsById = new HashMap<>(30);
        this.visibleNodes = new ArrayList<>(30);
        this.newChildCommentsButtons = new HashMap<>();
        this.newRootComments = new ArrayList<>();
    }

    public void setAdapter(CommentsAdapter adapter) {
        this.adapter = adapter;
    }

    public void notifyItemChanged(RenderableNode node) {
        final int index = this.visibleNodes.indexOf(node);
        if (index >= 0) {
            adapter.notifyItemChanged(index);
        }
    }

    public void build(List<PublicComment> comments) {
        List<RenderableComment> allComments = new ArrayList<>(commentsById.size());
        List<RenderableNode> visibleNodes = new ArrayList<>(commentsById.size());
        if (comments == null || comments.isEmpty()) {
            this.visibleNodes = visibleNodes;
            return;
        }

        // Process all comments and create RenderableComment objects
        for (PublicComment comment : comments) {
            final RenderableComment renderableComment = new RenderableComment(comment);
            commentsById.put(comment.getId(), renderableComment);
            allComments.add(renderableComment);
            visibleNodes.add(renderableComment);
            if (comment.getChildren() != null) {
                handleChildren(allComments, visibleNodes, comment.getChildren(), renderableComment.isRepliesShown);
            }
        }

        this.allComments = allComments;
        this.visibleNodes = visibleNodes;
        this.newRootCommentsButton = null;
        this.newChildCommentsButtons.clear();
        this.newRootComments.clear();
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

        int initialSize = visibleNodes.size();

        // Process all comments and create RenderableComment objects
        for (PublicComment comment : comments) {
            if (!commentsById.containsKey(comment.getId())) {
                final RenderableComment renderableComment = new RenderableComment(comment);
                commentsById.put(comment.getId(), renderableComment);
                allComments.add(renderableComment);
                visibleNodes.add(renderableComment);
                if (comment.getChildren() != null) {
                    handleChildren(allComments, visibleNodes, comment.getChildren(), renderableComment.isRepliesShown);
                }
            }
        }

        // Notify the adapter of exactly what changed (the newly added comments) TODO doesn't work right
//        int itemCount = visibleNodes.size() - initialSize;
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

    private void handleChildren(List<RenderableComment> allComments, List<RenderableNode> visibleNodes, List<PublicComment> comments, boolean visible) {
        for (PublicComment child : comments) {
            final RenderableComment childRenderable = new RenderableComment(child);
            commentsById.put(child.getId(), childRenderable);
            allComments.add(childRenderable);
            final boolean childrenVisible = visible && childRenderable.isRepliesShown;
            if (childrenVisible) {
                visibleNodes.add(childRenderable);
            }
            final List<PublicComment> children = child.getChildren();
            if (children != null) {
                handleChildren(allComments, visibleNodes, children, childrenVisible);
            }
        }
    }

    public int totalSize() {
        return allComments.size();
    }

    public int visibleSize() {
        return visibleNodes.size();
    }

    public void hideChildren(List<PublicComment> publicComments) {
        for (PublicComment child : publicComments) {
            final RenderableComment childRenderable = commentsById.get(child.getId());
            // see explanation at top of class
            visibleNodes.remove(childRenderable);
            if (child.getChildren() != null) {
                hideChildren(child.getChildren());
            }
        }
    }

    public void removeChildren(List<PublicComment> publicComments, List<Integer> indexesRemoved) {
        for (PublicComment child : publicComments) {
            final RenderableComment childRenderable = commentsById.get(child.getId());
            // see explanation at top of class
            allComments.remove(childRenderable);
            int index = visibleNodes.indexOf(childRenderable); // TODO optimize away lookup if parent does not have children visible
            if (index >= 0) {
                indexesRemoved.add(index);
                visibleNodes.remove(index);
            }
            if (child.getChildren() != null) {
                removeChildren(child.getChildren(), indexesRemoved);
            }
        }
    }

    public void setRepliesVisible(RenderableComment renderableComment, boolean areRepliesVisible, Producer<GetChildrenRequest, List<PublicComment>> getChildren) {
        final boolean wasRepliesVisible = renderableComment.isRepliesShown;
        if (wasRepliesVisible == areRepliesVisible) {
            return;
        }
        final int myIndex = visibleNodes.indexOf(renderableComment);
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
                
                // Check for new child comments and add a button if needed
                if (renderableComment.getNewChildCommentsCount() > 0) {
                    // Find the last visible child of this comment
                    int lastChildIndex = findLastChildIndex(renderableComment);
                    
                    // Create a button to show new replies
                    RenderableButton newRepliesButton = new RenderableButton(
                            RenderableButton.TYPE_NEW_CHILD_COMMENTS,
                            renderableComment.getNewChildCommentsCount(),
                            renderableComment.getComment().getId()
                    );
                    
                    // Add the button after the last child and track it
                    String parentId = renderableComment.getComment().getId();
                    visibleNodes.add(lastChildIndex + 1, newRepliesButton);
                    newChildCommentsButtons.put(parentId, newRepliesButton);
                    adapter.notifyItemInserted(lastChildIndex + 1);
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

                final String parentId = renderableComment.getComment().getId();
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
                    
                    // Check for new child comments and add a button if needed
                    if (renderableComment.getNewChildCommentsCount() > 0) {
                        // Find the last visible child of this comment
                        int lastChildIndex = findLastChildIndex(renderableComment);
                        
                        // Create a button to show new replies
                        RenderableButton newRepliesButton = new RenderableButton(
                                RenderableButton.TYPE_NEW_CHILD_COMMENTS,
                                renderableComment.getNewChildCommentsCount(),
                                parentId
                        );
                        
                        // Add the button after the last child and track it
                        visibleNodes.add(lastChildIndex + 1, newRepliesButton);
                        newChildCommentsButtons.put(parentId, newRepliesButton);
                        adapter.notifyItemInserted(lastChildIndex + 1);
                    }
                });
            }
        } else {
            // Remove any "new child comments" button for this parent
            String parentId = renderableComment.getComment().getId();
            RenderableButton button = newChildCommentsButtons.remove(parentId);
            if (button != null) {
                int buttonIndex = visibleNodes.indexOf(button);
                if (buttonIndex >= 0) {
                    visibleNodes.remove(buttonIndex);
                    adapter.notifyItemRemoved(buttonIndex);
                }
            }
            
            // Hide children
            if (children != null) {
                hideChildren(children);
                adapter.notifyItemRangeRemoved(myIndex + 1, children.size());

                // Reset pagination state when hiding replies
                renderableComment.resetChildPagination();
                adapter.notifyItemChanged(myIndex);
            }
        }
        adapter.notifyItemChanged(myIndex);
    }
    
    /**
     * Find the last visible child index for a parent comment
     *
     * @param parentComment The parent comment
     * @return The index of the last visible child, or the parent's index if no children are visible
     */
    private int findLastChildIndex(RenderableComment parentComment) {
        int parentIndex = visibleNodes.indexOf(parentComment);
        if (parentIndex < 0 || parentIndex >= visibleNodes.size() - 1) {
            return parentIndex;
        }
        
        String parentId = parentComment.getComment().getId();
        int lastChildIndex = parentIndex;
        
        // Scan forward from parent until we find a node that isn't a child
        for (int i = parentIndex + 1; i < visibleNodes.size(); i++) {
            RenderableNode node = visibleNodes.get(i);
            if (node instanceof RenderableComment) {
                RenderableComment comment = (RenderableComment) node;
                if (!parentId.equals(comment.getComment().getParentId())) {
                    break;
                }
                lastChildIndex = i;
            } else if (node instanceof RenderableButton) {
                RenderableButton button = (RenderableButton) node;
                if (button.getButtonType() == RenderableButton.TYPE_NEW_CHILD_COMMENTS && 
                        parentId.equals(button.getParentId())) {
                    lastChildIndex = i;
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        
        return lastChildIndex;
    }

    public void insertChildrenAfter(RenderableComment renderableComment, List<PublicComment> children) {
        int myIndex = visibleNodes.indexOf(renderableComment);
        int indexer = myIndex + 1;
        for (int i = children.size() - 1; i >= 0; i--) {
            final PublicComment child = children.get(i);
            final RenderableComment childRenderable = commentsById.get(child.getId());
            // see explanation at top of class
            visibleNodes.add(indexer, childRenderable);
        }
        adapter.notifyItemRangeInserted(myIndex + 1, children.size()); // everything after me has changed/moved since it's a flat list
    }

    /**
     * Add a new comment to the tree (from live events)
     *
     * @param comment The comment to add
     * @param showLiveRightAway Whether to show the comment immediately
     */
    public void addComment(PublicComment comment, boolean showLiveRightAway) {
        if (comment == null || commentsById.containsKey(comment.getId())) {
            return;
        }
        
        // Create a new renderable comment
        RenderableComment renderableComment = new RenderableComment(comment);
        commentsById.put(comment.getId(), renderableComment);
        
        if (comment.getParentId() == null) {
            // This is a root comment
            allComments.add(0, renderableComment);
            
            if (showLiveRightAway) {
                // Show the comment right away at the top of the list
                visibleNodes.add(0, renderableComment);
                adapter.notifyItemInserted(0);
            } else {
                // Buffer the comment and show a notice instead
                newRootComments.add(comment);
                
                // If this is the first new comment, add a "New Comments" button at the top
                if (newRootCommentsButton == null) {
                    newRootCommentsButton = new RenderableButton(
                            RenderableButton.TYPE_NEW_ROOT_COMMENTS,
                            newRootComments.size()
                    );
                    visibleNodes.add(0, newRootCommentsButton);
                    adapter.notifyItemInserted(0);
                } else {
                    // Update the existing button count
                    int buttonIndex = visibleNodes.indexOf(newRootCommentsButton);
                    if (buttonIndex >= 0) {
                        // Replace with updated button
                        newRootCommentsButton = new RenderableButton(
                                RenderableButton.TYPE_NEW_ROOT_COMMENTS,
                                newRootComments.size()
                        );
                        visibleNodes.set(buttonIndex, newRootCommentsButton);
                        adapter.notifyItemChanged(buttonIndex);
                    }
                }
            }
        } else {
            // This is a reply to an existing comment
            RenderableComment parent = commentsById.get(comment.getParentId());
            if (parent != null) {
                final PublicComment publicComment = parent.getComment();
                // Add this comment as a child of its parent
                if (publicComment.getChildren() == null) {
                    publicComment.setChildren(new ArrayList<>());
                }
                publicComment.getChildren().add(comment);

                // Increment the parent's child count if it exists
                if (publicComment.getChildCount() != null) {
                    publicComment.setChildCount(publicComment.getChildCount() + 1);
                } else {
                    publicComment.setChildCount(1);
                }

                // Set hasChildren flag if needed
                if (Boolean.FALSE.equals(publicComment.getHasChildren())) {
                    publicComment.setHasChildren(true);
                }

                // Handle visibility based on showLiveRightAway and parent state
                final int parentIndex = visibleNodes.indexOf(parent);
                if (parentIndex >= 0) {
                    adapter.notifyItemChanged(parentIndex); // re-render reply count
                    
                    if (parent.isRepliesShown && showLiveRightAway) {
                        // Parent's replies are shown and we should show the new reply immediately
                        int insertionIndex = findLastChildIndex(parent) + 1;
                        visibleNodes.add(insertionIndex, renderableComment);
                        adapter.notifyItemInserted(insertionIndex);
                    } else if (parent.isRepliesShown) {
                        // Parent's replies are shown but we should not show the new reply immediately
                        // Add to parent's buffered new comments
                        parent.addNewChildComment(comment);
                        
                        // Find any existing "new child comments" button for this parent
                        String parentId = parent.getComment().getId();
                        RenderableButton existingButton = newChildCommentsButtons.get(parentId);
                        
                        if (existingButton != null) {
                            // Update the existing button
                            int buttonIndex = visibleNodes.indexOf(existingButton);
                            if (buttonIndex >= 0) {
                                RenderableButton updatedButton = new RenderableButton(
                                        RenderableButton.TYPE_NEW_CHILD_COMMENTS,
                                        parent.getNewChildCommentsCount(),
                                        parentId
                                );
                                visibleNodes.set(buttonIndex, updatedButton);
                                newChildCommentsButtons.put(parentId, updatedButton);
                                adapter.notifyItemChanged(buttonIndex);
                            }
                        } else {
                            // Create a new button
                            int insertionIndex = findLastChildIndex(parent) + 1;
                            RenderableButton newRepliesButton = new RenderableButton(
                                    RenderableButton.TYPE_NEW_CHILD_COMMENTS,
                                    parent.getNewChildCommentsCount(),
                                    parentId
                            );
                            visibleNodes.add(insertionIndex, newRepliesButton);
                            newChildCommentsButtons.put(parentId, newRepliesButton);
                            adapter.notifyItemInserted(insertionIndex);
                        }
                    } else {
                        // Parent's replies are not shown, just buffer the comment
                        parent.addNewChildComment(comment);
                    }
                } else {
                    // Parent is not visible, just buffer the comment
                    parent.addNewChildComment(comment);
                }
            }
        }
    }
    
    /**
     * Show all new root comments that were buffered
     */
    public void showNewRootComments() {
        if (newRootComments.isEmpty() || newRootCommentsButton == null) {
            return;
        }
        
        // Remove the "New Comments" button
        int buttonIndex = visibleNodes.indexOf(newRootCommentsButton);
        if (buttonIndex >= 0) {
            visibleNodes.remove(buttonIndex);
            adapter.notifyItemRemoved(buttonIndex);
        }
        
        // Add all buffered comments at the top of the list in chronological order (oldest first)
        for (int i = 0; i < newRootComments.size(); i++) {
            PublicComment comment = newRootComments.get(i);
            RenderableComment renderableComment = commentsById.get(comment.getId());
            if (renderableComment != null) {
                visibleNodes.add(0, renderableComment);
                adapter.notifyItemInserted(0);
            }
        }
        
        // Clear the buffer and button reference
        newRootComments.clear();
        newRootCommentsButton = null;
    }
    
    /**
     * Show all new child comments for a specific parent
     *
     * @param parentId The parent comment ID
     */
    public void showNewChildComments(String parentId) {
        RenderableComment parent = commentsById.get(parentId);
        if (parent == null || parent.getNewChildCommentsCount() == 0) {
            return;
        }
        
        // Get and clear the buffered comments
        List<PublicComment> newChildComments = parent.getAndClearNewChildComments();
        if (newChildComments == null) {
            return;
        }
        
        // Remove the "New Child Comments" button
        RenderableButton button = newChildCommentsButtons.remove(parentId);
        if (button != null) {
            int buttonIndex = visibleNodes.indexOf(button);
            if (buttonIndex >= 0) {
                visibleNodes.remove(buttonIndex);
                adapter.notifyItemRemoved(buttonIndex);
            }
        }
        
        // Find the insertion point (after the last visible child of this parent)
        int insertionIndex = findLastChildIndex(parent) + 1;
        
        // Add all the new child comments in chronological order (oldest first)
        for (int i = 0; i < newChildComments.size(); i++) {
            PublicComment childComment = newChildComments.get(i);
            RenderableComment childRenderable = commentsById.get(childComment.getId());
            if (childRenderable != null) {
                visibleNodes.add(insertionIndex, childRenderable);
                // Increment insertion index to maintain correct order
                insertionIndex++;
            }
        }
        
        // Notify adapter of the insertions
        // insertionIndex is now at the position after the last inserted item, so we need to calculate
        // the starting position by subtracting the number of inserted items
        int startPosition = insertionIndex - newChildComments.size();
        adapter.notifyItemRangeInserted(startPosition, newChildComments.size());
    }

    public PublicComment getPublicComment(String commentId) {
        RenderableComment renderableComment = commentsById.get(commentId);
        return renderableComment != null ? renderableComment.getComment() : null;
    }
    
    /**
     * Update the online status for all comments by a specific user
     *
     * @param userId The user ID
     * @param isOnline Whether the user is online or offline
     */
    public void updateUserPresence(String userId, boolean isOnline) {
        // Track which comments were updated to minimize UI updates
        List<RenderableComment> updatedComments = new ArrayList<>();
        
        // Update all comments by this user
        for (RenderableComment comment : commentsById.values()) {
            if (comment.getComment() != null && 
                    userId.equals(comment.getComment().getUserId())) {
                
                // Check if status actually changed to avoid unnecessary updates
                Boolean currentStatus = comment.getComment().getIsOnline();
                if (currentStatus == null || currentStatus != isOnline) {
                    // Update status
                    comment.getComment().setIsOnline(isOnline);
                    updatedComments.add(comment);
                }
            }
        }
        
        // Update UI for visible comments only
        for (RenderableComment comment : updatedComments) {
            notifyItemChanged(comment);
        }
    }

    /**
     * Remove a comment from the tree
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
        final int visibleIndex = visibleNodes.indexOf(comment);

        // Remove from main collections
        commentsById.remove(commentId);
        allComments.remove(comment);

        // Handle parent's child count if this is a reply
        final String parentId = comment.getComment().getParentId();
        if (parentId != null) {
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

                Integer childCount = parent.getComment().getChildCount();
                if (childCount != null && childCount > 0) {
                    childCount--;
                    parent.getComment().setChildCount(childCount);
                }

                // Update hasChildren flag if needed
                if (childCount != null && childCount == 0) {
                    parent.getComment().setHasChildren(false);
                }
            }
            final int parentVisibleIndex = visibleNodes.indexOf(parent);
            if (parentVisibleIndex >= 0) {
                adapter.notifyItemChanged(parentVisibleIndex);
            }
        }

        // If comment was visible, remove it and its children from visible list
        if (visibleIndex >= 0) {
            visibleNodes.remove(visibleIndex);
            adapter.notifyItemRemoved(visibleIndex);

            if (comment.isRepliesShown && comment.getComment().getChildren() != null) {
                final List<Integer> indexesToRemove = new ArrayList<>(comment.getComment().getChildren().size());
                removeChildren(comment.getComment().getChildren(), indexesToRemove);
                for (Integer i : indexesToRemove) {
                    adapter.notifyItemRemoved(i); // TODO worth to optimize into one notification?
                }
            }
        }

        return true;
    }
}
