package com.fastcomments.sdk;

import com.fastcomments.model.PublicComment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Factory for creating PublicComment test objects with sensible defaults.
 */
class MockComment {

    static PublicComment make() {
        return make(UUID.randomUUID().toString());
    }

    static PublicComment make(String id) {
        return make(id, null, "Test User", "<p>Test comment</p>", null,
                OffsetDateTime.now(), 0, true, null, null, null, null, null);
    }

    static PublicComment make(String id, String userId) {
        return make(id, userId, "Test User", "<p>Test comment</p>", null,
                OffsetDateTime.now(), 0, true, null, null, null, null, null);
    }

    static PublicComment make(String id, String userId, String parentId) {
        return make(id, userId, "Test User", "<p>Test comment</p>", parentId,
                OffsetDateTime.now(), 0, true, null, null, null, null, null);
    }

    static PublicComment make(String id,
                              String userId,
                              String commenterName,
                              String commentHTML,
                              String parentId,
                              OffsetDateTime date,
                              Integer votes,
                              Boolean verified,
                              Integer childCount,
                              List<PublicComment> children,
                              Boolean isPinned,
                              Boolean isDeleted,
                              Boolean isLocked) {
        PublicComment comment = new PublicComment();
        comment.setId(id);
        comment.setUserId(userId);
        comment.setCommenterName(commenterName);
        comment.setCommentHTML(commentHTML);
        comment.setParentId(parentId);
        comment.setDate(date);
        comment.setVotes(votes);
        comment.setVerified(verified);
        comment.setChildCount(childCount);
        comment.setChildren(children);
        comment.setIsPinned(isPinned);
        comment.setIsDeleted(isDeleted);
        comment.setIsLocked(isLocked);
        if (children != null && !children.isEmpty()) {
            comment.setHasChildren(true);
        }
        return comment;
    }
}
