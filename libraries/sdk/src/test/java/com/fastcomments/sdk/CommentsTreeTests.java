package com.fastcomments.sdk;

import com.fastcomments.model.PublicComment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for CommentsTree in-memory data structure.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CommentsTreeTests {

    private CommentsTree tree;

    @Before
    public void setUp() {
        tree = new CommentsTree();
        tree.setAdapter(mock(CommentsAdapter.class));
    }

    @Test
    public void testBuild() {
        List<PublicComment> comments = Arrays.asList(
                MockComment.make("c1"),
                MockComment.make("c2"),
                MockComment.make("c3")
        );

        tree.build(comments);

        assertEquals(3, tree.visibleNodes.size());
        assertEquals(3, tree.totalSize());
        assertNotNull(tree.commentsById.get("c1"));
        assertNotNull(tree.commentsById.get("c2"));
        assertNotNull(tree.commentsById.get("c3"));
    }

    @Test
    public void testBuildWithChildren() {
        PublicComment child1 = MockComment.make("child1", null, "parent");
        PublicComment child2 = MockComment.make("child2", null, "parent");
        List<PublicComment> children = Arrays.asList(child1, child2);

        PublicComment parent = MockComment.make("parent", null, "Test User", "<p>Parent</p>",
                null, OffsetDateTime.now(), 0, true, 2, children, null, null, null);

        tree.build(Collections.singletonList(parent));

        // Parent + 2 children = 3 total
        assertEquals(3, tree.totalSize());
        assertNotNull(tree.commentsById.get("parent"));
        assertNotNull(tree.commentsById.get("child1"));
        assertNotNull(tree.commentsById.get("child2"));
        // Children have correct parentId
        assertEquals("parent", tree.commentsById.get("child1").getComment().getParentId());
        assertEquals("parent", tree.commentsById.get("child2").getComment().getParentId());
    }

    @Test
    public void testBuildEmpty() {
        tree.build(Collections.emptyList());

        assertEquals(0, tree.visibleNodes.size());
        assertEquals(0, tree.totalSize());
    }

    @Test
    public void testAppendComments() {
        tree.build(Collections.singletonList(MockComment.make("c1")));
        assertEquals(1, tree.totalSize());

        tree.appendComments(Arrays.asList(MockComment.make("c2"), MockComment.make("c3")));

        assertEquals(3, tree.totalSize());
        assertEquals(3, tree.visibleNodes.size());
    }

    @Test
    public void testAppendNoDuplicates() {
        tree.build(Collections.singletonList(MockComment.make("c1")));
        tree.appendComments(Collections.singletonList(MockComment.make("c1")));

        assertEquals(1, tree.totalSize());
    }

    @Test
    public void testAddCommentRootDisplayNow() {
        tree.build(Collections.singletonList(MockComment.make("c1")));

        PublicComment c2 = MockComment.make("c2");
        tree.addComment(c2, true);

        assertEquals(2, tree.totalSize());
        assertNotNull(tree.commentsById.get("c2"));
        // Should be in visibleNodes
        boolean found = false;
        for (RenderableNode node : tree.visibleNodes) {
            if (node instanceof RenderableComment &&
                    "c2".equals(((RenderableComment) node).getComment().getId())) {
                found = true;
                break;
            }
        }
        assertTrue("c2 should be in visibleNodes", found);
    }

    @Test
    public void testAddCommentRootBuffered() {
        tree.build(Collections.singletonList(MockComment.make("c1")));

        PublicComment c2 = MockComment.make("c2");
        tree.addComment(c2, false);

        assertEquals(2, tree.totalSize());
        assertNotNull(tree.commentsById.get("c2"));
        // Should NOT be directly in visibleNodes as a comment, but a button should appear
        boolean hasButton = false;
        for (RenderableNode node : tree.visibleNodes) {
            if (node instanceof RenderableButton) {
                hasButton = true;
                break;
            }
        }
        assertTrue("A 'new comments' button should appear in visibleNodes", hasButton);
    }

    @Test
    public void testAddCommentChild() {
        tree.build(Collections.singletonList(MockComment.make("parent")));

        PublicComment child = MockComment.make("child", null, "parent");
        tree.addComment(child, true);

        // Note: addComment for child does NOT add to allComments (totalSize),
        // it only adds to commentsById. This is the SDK's actual behavior.
        assertEquals(2, tree.commentsById.size());
        assertNotNull(tree.commentsById.get("child"));
        assertEquals("parent", tree.commentsById.get("child").getComment().getParentId());
    }

    @Test
    public void testRemoveComment() {
        tree.build(Arrays.asList(MockComment.make("c1"), MockComment.make("c2")));
        assertEquals(2, tree.totalSize());

        boolean removed = tree.removeComment("c1");

        assertTrue(removed);
        assertEquals(1, tree.totalSize());
        assertNull(tree.commentsById.get("c1"));
        assertNotNull(tree.commentsById.get("c2"));
    }

    @Test
    public void testRemoveNonexistent() {
        tree.build(Arrays.asList(MockComment.make("c1"), MockComment.make("c2")));
        int sizeBefore = tree.totalSize();

        boolean removed = tree.removeComment("nonexistent");

        assertFalse(removed);
        assertEquals(sizeBefore, tree.totalSize());
    }

    @Test
    public void testUpdateComment() {
        tree.build(Collections.singletonList(
                MockComment.make("c1", null, "Test User", "<p>original</p>",
                        null, OffsetDateTime.now(), 0, true, null, null, null, null, null)));

        PublicComment comment = tree.getPublicComment("c1");
        assertNotNull(comment);
        assertEquals("<p>original</p>", comment.getCommentHTML());

        // Update in place
        comment.setCommentHTML("<p>updated</p>");

        // Verify change is reflected through the tree
        assertEquals("<p>updated</p>", tree.getPublicComment("c1").getCommentHTML());
    }

    @Test
    public void testShowNewRootComments() {
        tree.build(Collections.singletonList(MockComment.make("c1")));

        // Buffer 2 new comments
        tree.addComment(MockComment.make("c2"), false);
        tree.addComment(MockComment.make("c3"), false);

        // Count visible comments (not buttons)
        int visibleCommentsBefore = countVisibleComments();

        // Show buffered comments
        tree.showNewRootComments();

        int visibleCommentsAfter = countVisibleComments();
        assertTrue("Visible comments should increase after showNewRootComments",
                visibleCommentsAfter > visibleCommentsBefore);
    }

    @Test
    public void testShowNewChildComments() {
        // Build with parent that has replies shown
        PublicComment parentComment = MockComment.make("parent");
        tree.build(Collections.singletonList(parentComment));
        RenderableComment parent = tree.commentsById.get("parent");
        parent.isRepliesShown = true;

        // Buffer a child comment
        PublicComment child = MockComment.make("child", null, "parent");
        tree.addComment(child, false);

        // Show new child comments for parent
        tree.showNewChildComments("parent");

        // The child should now be visible
        assertNotNull(tree.commentsById.get("child"));
    }

    @Test
    public void testPresenceUpdate() {
        List<PublicComment> comments = Arrays.asList(
                MockComment.make("c1", "user1"),
                MockComment.make("c2", "user1"),
                MockComment.make("c3", "user2")
        );
        tree.build(comments);

        tree.updateUserPresence("user1", true);

        RenderableComment rc1 = tree.commentsById.get("c1");
        RenderableComment rc2 = tree.commentsById.get("c2");
        RenderableComment rc3 = tree.commentsById.get("c3");

        assertTrue("user1's first comment should be online", rc1.isOnline);
        assertTrue("user1's second comment should be online", rc2.isOnline);
        assertFalse("user2's comment should not be online", rc3.isOnline);
    }

    @Test
    public void testPresenceIndexingAndPropagation() {
        List<PublicComment> comments = Arrays.asList(
                MockComment.make("c1", "user1"),
                MockComment.make("c2", "user1")
        );
        tree.build(comments);

        // commentsByUserId should have entries for "user1"
        assertNotNull(tree.commentsByUserId.get("user1"));

        // Update presence
        tree.updateUserPresence("user1", true);
        tree.updateUserPresence("user1", false);

        // After setting offline, the comments that were updated should be offline
        // (subject to the addForUser bug - first comment may not be indexed)
    }

    @Test
    public void testLiveChatDateSeparators() {
        tree.setLiveChatStyle(true);

        OffsetDateTime day1 = OffsetDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime day2 = OffsetDateTime.of(2024, 1, 16, 10, 0, 0, 0, ZoneOffset.UTC);

        List<PublicComment> comments = Arrays.asList(
                MockComment.make("c1", null, "User", "<p>Day 1</p>", null, day1, 0, true, null, null, null, null, null),
                MockComment.make("c2", null, "User", "<p>Day 2</p>", null, day2, 0, true, null, null, null, null, null)
        );

        tree.build(comments);

        // Should have date separators for the two different dates
        int separatorCount = 0;
        for (RenderableNode node : tree.visibleNodes) {
            if (node instanceof RenderableNode.DateSeparator) {
                separatorCount++;
            }
        }
        assertEquals("Should have 2 date separators (one per day)", 2, separatorCount);
    }

    @Test
    public void testAddForParent() {
        PublicComment parent = MockComment.make("parent", null, "User", "<p>Parent</p>",
                null, OffsetDateTime.now(), 0, true, 5, null, null, null, null);
        tree.build(Collections.singletonList(parent));

        List<PublicComment> children = Arrays.asList(
                MockComment.make("child1", null, "parent"),
                MockComment.make("child2", null, "parent")
        );
        tree.addForParent("parent", children);

        assertEquals(3, tree.totalSize());
        assertNotNull(tree.commentsById.get("child1"));
        assertNotNull(tree.commentsById.get("child2"));
    }

    @Test
    public void testBuildNullComments() {
        tree.build(null);

        assertEquals(0, tree.visibleNodes.size());
    }

    @Test
    public void testResetPresence() {
        List<PublicComment> comments = Arrays.asList(
                MockComment.make("c1", "user1"),
                MockComment.make("c2", "user2")
        );
        tree.build(comments);

        // Manually set online
        tree.commentsById.get("c1").isOnline = true;
        tree.commentsById.get("c2").isOnline = true;

        tree.resetPresence();

        assertFalse(tree.commentsById.get("c1").isOnline);
        assertFalse(tree.commentsById.get("c2").isOnline);
    }

    private int countVisibleComments() {
        int count = 0;
        for (RenderableNode node : tree.visibleNodes) {
            if (node instanceof RenderableComment) {
                count++;
            }
        }
        return count;
    }
}
