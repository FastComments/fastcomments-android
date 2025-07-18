package com.fastcomments.sdk;

import android.content.Context;
import android.util.Log;

import com.fastcomments.model.PublicComment;
import com.fastcomments.model.SortDirections;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


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
    public Map<String, List<RenderableComment>> commentsByUserId;
    // Note that lots of operations have to do N-time lookups in these lists. We may want to replace these
    // with some sort of ordered map.
    public List<RenderableComment> allComments = new ArrayList<>(0); // in any order
    public List<RenderableNode> visibleNodes = new ArrayList<>(0); // in view order - can include comments and buttons
    private CommentsAdapter adapter;
    public boolean liveChatStyle = false;

    // Separate collections for easier lookup
    private RenderableButton newRootCommentsButton; // Only one of these at most
    private final Map<String, RenderableButton> newChildCommentsButtons; // Keyed by parent comment ID
    private final List<PublicComment> newRootComments; // Buffer for new root comments when showLiveRightAway is false

    public CommentsTree() {
        this.commentsById = new HashMap<>(30);
        this.commentsByUserId = new HashMap<>(30);
        this.visibleNodes = new ArrayList<>(30);
        this.newChildCommentsButtons = new HashMap<>();
        this.newRootComments = new ArrayList<>();
    }

    public void setAdapter(CommentsAdapter adapter) {
        this.adapter = adapter;
    }
    
    public CommentsAdapter getAdapter() {
        return this.adapter;
    }
    
    public Context getContext() {
        return adapter != null ? adapter.getContext() : null;
    }
    
    /**
     * Set whether this tree should use live chat style rendering (with date separators)
     * @param liveChatStyle true for live chat style
     */
    public void setLiveChatStyle(boolean liveChatStyle) {
        this.liveChatStyle = liveChatStyle;
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

        if (!liveChatStyle) {
            // Standard mode - process all comments and create RenderableComment objects
            for (PublicComment comment : comments) {
                final RenderableComment renderableComment = new RenderableComment(comment);
                addToMapAndRelated(renderableComment);
                allComments.add(renderableComment);
                visibleNodes.add(renderableComment);
                if (comment.getChildren() != null) {
                    handleChildren(allComments, visibleNodes, comment.getChildren(), renderableComment.isRepliesShown);
                }
            }
        } else {
            // Live chat mode - insert date separators
            java.time.LocalDate currentDate = null;

            for (PublicComment comment : comments) {
                final RenderableComment renderableComment = new RenderableComment(comment);
                addToMapAndRelated(renderableComment);
                allComments.add(renderableComment);
                
                // Check if we need a date separator
                if (comment.getDate() != null) {
                    java.time.LocalDate commentDate = comment.getDate().toLocalDate();
                    
                    if (currentDate == null || !currentDate.equals(commentDate)) {
                        // Add date separator for this new date
                        currentDate = commentDate;
                        visibleNodes.add(new RenderableNode.DateSeparator(currentDate));
                    }
                }
                
                visibleNodes.add(renderableComment);
                
                // In live chat, we typically don't show children/replies
                // But process them anyway in case this changes
                if (comment.getChildren() != null) {
                    handleChildren(allComments, visibleNodes, comment.getChildren(), renderableComment.isRepliesShown);
                }
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
                addToMapAndRelated(renderableComment);
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
            addToMapAndRelated(childRenderable);
            allComments.add(childRenderable);
            if (children != null) {
                children.add(comment);
            }
        }
    }

    private void handleChildren(List<RenderableComment> allComments, List<RenderableNode> visibleNodes, List<PublicComment> comments, boolean visible) {
        for (PublicComment child : comments) {
            final RenderableComment childRenderable = new RenderableComment(child);
            addToMapAndRelated(childRenderable);
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
                // Note: No need to call checkAndRequestUserPresenceStatuses() here anymore
                // because it's already called inside insertChildrenAfter
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
                    // Note: No need to call checkAndRequestUserPresenceStatuses() here anymore
                    // because it's already called inside insertChildrenAfter
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
        
        // Check if we need to fetch presence status for newly visible comments
        // Use the specific list of children that were just added for efficiency
        checkAndRequestUserPresenceStatuses(children);
    }

    private void addToMapAndRelated(RenderableComment renderableComment) {
        final PublicComment publicComment = renderableComment.getComment();
        commentsById.put(publicComment.getId(), renderableComment);
        final String userId = publicComment.getUserId();
        if (userId != null) {
            addForUser(userId, renderableComment);
        }
        final String anonUserId = publicComment.getAnonUserId();
        if (anonUserId != null) {
            addForUser(anonUserId, renderableComment);
        }
    }

    private void addForUser(String userId, RenderableComment renderableComment) {
        List<RenderableComment> commentsForUser = commentsByUserId.get(userId);
        if (commentsForUser == null) {
            commentsForUser = new ArrayList<>(1);
            commentsByUserId.put(userId, commentsForUser);
        } else {
            commentsForUser.add(renderableComment);
        }
    }

    /**
     * Backward-compatible method that defaults to NEWEST_FIRST sort direction
     * 
     * @param comment    The comment to add
     * @param displayNow Whether to show the comment immediately
     */
    public void addComment(PublicComment comment, boolean displayNow) {
        // Default to NEWEST_FIRST for backward compatibility
        addComment(comment, displayNow, SortDirections.NF);
    }

    private boolean isNewestFirst(SortDirections sortDirections) {
        return sortDirections == SortDirections.NF || sortDirections == SortDirections.MR;
    }
    
    /**
     * Add a new comment to the tree (from live events) with the specified sort direction
     *
     * @param comment    The comment to add
     * @param displayNow Whether to show the comment immediately
     * @param sortDirection The direction to sort comments (newest first or oldest first)
     */
    public void addComment(PublicComment comment, boolean displayNow, SortDirections sortDirection) {
        if (comment == null || commentsById.containsKey(comment.getId())) {
            return;
        }

        // Create a new renderable comment
        RenderableComment renderableComment = new RenderableComment(comment);
        addToMapAndRelated(renderableComment);

        if (comment.getParentId() == null) {
            // This is a root comment
            // For NEWEST_FIRST, add at index 0 (top)
            // For OLDEST_FIRST, add at the end (bottom)
            boolean isNewestFirst = isNewestFirst(sortDirection);
            
            if (isNewestFirst) {
                allComments.add(0, renderableComment);
            } else {
                allComments.add(renderableComment);
            }

            if (displayNow) {
                int position;
                if (isNewestFirst) {
                    // For newest first, add at the top
                    position = 0;
                    visibleNodes.add(0, renderableComment);
                } else {
                    // For oldest first (like chat), add at the bottom
                    position = visibleNodes.size();
                    
                    // Check if we need to add a date separator in live chat mode
                    if (liveChatStyle && comment.getDate() != null) {
                        java.time.LocalDate commentDate = comment.getDate().toLocalDate();
                        boolean needDateSeparator = true;
                        
                        // Check if there are any comments already with the same date
                        for (int i = visibleNodes.size() - 1; i >= 0; i--) {
                            RenderableNode node = visibleNodes.get(i);
                            if (node instanceof RenderableNode.DateSeparator) {
                                RenderableNode.DateSeparator separator = (RenderableNode.DateSeparator) node;
                                if (separator.getDate().equals(commentDate)) {
                                    // We already have a separator for this date
                                    needDateSeparator = false;
                                }
                                break; // Exit after finding the most recent date separator
                            } else if (node instanceof RenderableComment) {
                                RenderableComment lastComment = (RenderableComment) node;
                                if (lastComment.getComment().getDate() != null && 
                                    lastComment.getComment().getDate().toLocalDate().equals(commentDate)) {
                                    // The previous comment is from the same date
                                    needDateSeparator = false;
                                    break;
                                }
                            }
                        }
                        
                        if (needDateSeparator) {
                            RenderableNode.DateSeparator separator = new RenderableNode.DateSeparator(commentDate);
                            visibleNodes.add(separator);
                            adapter.notifyItemInserted(position);
                            position++;
                        }
                    }
                    
                    visibleNodes.add(renderableComment);
                }
                adapter.notifyItemInserted(position);
                
                // Check for new user presence (optimized for single comment)
                checkAndRequestUserPresenceStatus(renderableComment);
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

                    if (parent.isRepliesShown && displayNow) {
                        // Parent's replies are shown and we should show the new reply immediately
                        int insertionIndex = findLastChildIndex(parent) + 1;
                        visibleNodes.add(insertionIndex, renderableComment);
                        adapter.notifyItemInserted(insertionIndex);
                        
                        // Check for new user presence (optimized for single comment)
                        checkAndRequestUserPresenceStatus(renderableComment);
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
                        // don't buffer, we'll display the updated list the next time they open replies for this parent
                    }
                } else {
                    // don't buffer, we'll display the updated list the next time they open replies for this parent
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
        // Convert PublicComment list to RenderableComment list as we add them
        List<PublicComment> addedComments = new ArrayList<>();
        
        for (int i = 0; i < newRootComments.size(); i++) {
            PublicComment comment = newRootComments.get(i);
            RenderableComment renderableComment = commentsById.get(comment.getId());
            if (renderableComment != null) {
                visibleNodes.add(0, renderableComment);
                adapter.notifyItemInserted(0);
                addedComments.add(comment);
            }
        }
        
        // Check for new user presence (optimized for the specific comments)
        if (!addedComments.isEmpty()) {
            checkAndRequestUserPresenceStatuses(addedComments);
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
        
        // Check for new user presence (optimized for specific comments)
        checkAndRequestUserPresenceStatuses(newChildComments);
    }

    public PublicComment getPublicComment(String commentId) {
        RenderableComment renderableComment = commentsById.get(commentId);
        return renderableComment != null ? renderableComment.getComment() : null;
    }

    // Store known user presence status to avoid unnecessary API calls
    private Map<String, Boolean> userPresenceCache = new HashMap<>();
    
    /**
     * Update the online status for all comments by a specific user
     *
     * @param userId   The user ID
     * @param isOnline Whether the user is online or offline
     */
    public void updateUserPresence(String userId, boolean isOnline) {
        // Cache the presence status
        userPresenceCache.put(userId, isOnline);
        
        // Track which comments were updated to minimize UI updates
        final List<RenderableComment> usersComments = commentsByUserId.get(userId);
        if (usersComments == null) {
            return;
        }
        List<RenderableComment> updatedComments = new ArrayList<>();

        // Update all comments by this user
        for (RenderableComment comment : usersComments) {
            // Check if status actually changed to avoid unnecessary updates
            if (comment.isOnline != isOnline) {
                // Update status
                comment.isOnline = isOnline;
                updatedComments.add(comment);
            }
        }

        // Update UI for visible comments only
        for (RenderableComment comment : updatedComments) {
            notifyItemChanged(comment);
        }
    }
    
    /**
     * Check for newly visible comments and return any user IDs we need to fetch presence for
     * 
     * @return A set of user IDs needing presence status updates
     */
    public Set<String> checkForNewlyVisibleCommentUsers() {
        Set<String> userIdsToFetch = new HashSet<>();
        
        // Check all visible comments
        for (RenderableNode node : visibleNodes) {
            if (node instanceof RenderableComment) {
                RenderableComment comment = (RenderableComment) node;
                
                // Check regular user ID
                String userId = comment.getComment().getUserId();
                if (userId != null && !userId.isEmpty() && !userPresenceCache.containsKey(userId)) {
                    userIdsToFetch.add(userId);
                }
                
                // Check anonymous user ID
                String anonUserId = comment.getComment().getAnonUserId();
                if (anonUserId != null && !anonUserId.isEmpty() && !userPresenceCache.containsKey(anonUserId)) {
                    userIdsToFetch.add(anonUserId);
                }
            }
        }
        
        return userIdsToFetch;
    }
    
    /**
     * Check for newly visible comments and request presence status updates if needed
     */
    public void checkAndRequestUserPresenceStatuses() {
        Set<String> userIdsToFetch = checkForNewlyVisibleCommentUsers();
        requestPresenceStatusesIfNeeded(userIdsToFetch);
    }
    
    /**
     * Check for newly visible comment and request presence status updates if needed
     * 
     * @param comment The specific comment that became visible
     */
    public void checkAndRequestUserPresenceStatus(RenderableComment comment) {
        Set<String> userIdsToFetch = new HashSet<>();
        
        // Check regular user ID
        String userId = comment.getComment().getUserId();
        if (userId != null && !userId.isEmpty() && !userPresenceCache.containsKey(userId)) {
            userIdsToFetch.add(userId);
        }
        
        // Check anonymous user ID
        String anonUserId = comment.getComment().getAnonUserId();
        if (anonUserId != null && !anonUserId.isEmpty() && !userPresenceCache.containsKey(anonUserId)) {
            userIdsToFetch.add(anonUserId);
        }
        
        requestPresenceStatusesIfNeeded(userIdsToFetch);
    }
    
    /**
     * Check for newly visible comments and request presence status updates if needed
     * 
     * @param comments The specific comments that became visible
     */
    public void checkAndRequestUserPresenceStatuses(List<PublicComment> comments) {
        if (comments == null || comments.isEmpty()) {
            return;
        }
        
        Set<String> userIdsToFetch = new HashSet<>();
        
        for (PublicComment comment : comments) {
            // Check regular user ID
            String userId = comment.getUserId();
            if (userId != null && !userId.isEmpty() && !userPresenceCache.containsKey(userId)) {
                userIdsToFetch.add(userId);
            }
            
            // Check anonymous user ID
            String anonUserId = comment.getAnonUserId();
            if (anonUserId != null && !anonUserId.isEmpty() && !userPresenceCache.containsKey(anonUserId)) {
                userIdsToFetch.add(anonUserId);
            }
        }
        
        requestPresenceStatusesIfNeeded(userIdsToFetch);
    }
    
    /**
     * Request presence status updates for a set of user IDs
     * 
     * @param userIdsToFetch The set of user IDs to fetch status for
     */
    private void requestPresenceStatusesIfNeeded(Set<String> userIdsToFetch) {
        if (!userIdsToFetch.isEmpty() && presenceStatusListener != null) {
            // Create a comma-separated string of user IDs
            StringBuilder userIdsCSV = new StringBuilder();
            for (String userId : userIdsToFetch) {
                if (userIdsCSV.length() > 0) {
                    userIdsCSV.append(",");
                }
                userIdsCSV.append(userId);
            }
            
            // Request presence status updates
            presenceStatusListener.onPresenceStatusNeeded(userIdsCSV.toString());
        }
    }
    
    // Interface for requesting presence status updates
    public interface PresenceStatusListener {
        void onPresenceStatusNeeded(String userIdsCSV);
    }
    
    private PresenceStatusListener presenceStatusListener;
    
    /**
     * Set the listener for presence status update requests
     * 
     * @param listener The listener to set
     */
    public void setPresenceStatusListener(PresenceStatusListener listener) {
        this.presenceStatusListener = listener;
    }

    /**
     * Remove a comment from the tree
     *
     * @param commentId The ID of the comment to remove
     * @return true if the comment was found and removed, false otherwise
     */
    public boolean removeComment(String commentId) {
        final RenderableComment comment = commentsById.get(commentId);
        if (comment == null) {
            return false;
        }

        // Find the comment's index in the visible list
        final int visibleIndex = visibleNodes.indexOf(comment);

        // Remove from main collections
        commentsById.remove(commentId);
        allComments.remove(comment);

        // Remove this from the cached list of user's comments.
        if (comment.getComment().getUserId() != null) {
            final List<RenderableComment> usersComments = commentsByUserId.get(comment.getComment().getUserId());
            if (usersComments != null) {
                usersComments.remove(comment);
            }
        }
        if (comment.getComment().getAnonUserId() != null) {
            final List<RenderableComment> usersComments = commentsByUserId.get(comment.getComment().getAnonUserId());
            if (usersComments != null) {
                usersComments.remove(comment);
            }
        }


        // Handle parent's child count if this is a reply
        final String parentId = comment.getComment().getParentId();
        if (parentId != null) {
            final RenderableComment parent = commentsById.get(parentId);
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
    
    /**
     * Clears all data from the comments tree.
     * Use this when switching fragments to avoid memory leaks.
     */
    public void clear() {
        commentsById.clear();
        commentsByUserId.clear();
        allComments.clear();
        visibleNodes.clear();
        newChildCommentsButtons.clear();
        newRootComments.clear();
        newRootCommentsButton = null;
        userPresenceCache.clear();
        if (adapter != null) {
            adapter = null;
        }
    }
}
