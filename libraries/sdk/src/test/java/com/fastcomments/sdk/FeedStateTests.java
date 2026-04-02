package com.fastcomments.sdk;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for FastCommentsFeedSDK.FeedState serialization and defaults.
 */
public class FeedStateTests {

    @Test
    public void testInitDefaults() {
        FastCommentsFeedSDK.FeedState state = new FastCommentsFeedSDK.FeedState();

        assertNull(state.getLastPostId());
        assertFalse(state.isHasMore());
        assertEquals(0, state.getPageSize());
        assertEquals(0, state.getNewPostsCount());
        assertNull(state.getFeedPosts());
        assertNull(state.getMyReacts());
        assertNull(state.getLikeCounts());
    }

    @Test
    public void testSerializableRoundtrip() throws Exception {
        FastCommentsFeedSDK.FeedState state = new FastCommentsFeedSDK.FeedState();
        state.setLastPostId("post-123");
        state.setHasMore(true);
        state.setPageSize(20);
        state.setNewPostsCount(5);

        Map<String, Integer> likeCounts = new HashMap<>();
        likeCounts.put("post-123", 42);
        state.setLikeCounts(likeCounts);

        Map<String, Map<String, Boolean>> myReacts = new HashMap<>();
        Map<String, Boolean> reacts = new HashMap<>();
        reacts.put("l", true);
        myReacts.put("post-123", reacts);
        state.setMyReacts(myReacts);

        // Serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(state);
        oos.close();

        // Deserialize
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        FastCommentsFeedSDK.FeedState restored = (FastCommentsFeedSDK.FeedState) ois.readObject();
        ois.close();

        assertEquals("post-123", restored.getLastPostId());
        assertTrue(restored.isHasMore());
        assertEquals(20, restored.getPageSize());
        assertEquals(5, restored.getNewPostsCount());
        assertEquals(Integer.valueOf(42), restored.getLikeCounts().get("post-123"));
        assertTrue(restored.getMyReacts().get("post-123").get("l"));
    }

    @Test
    public void testReactionsTracking() {
        FastCommentsFeedSDK.FeedState state = new FastCommentsFeedSDK.FeedState();

        Map<String, Map<String, Boolean>> myReacts = new HashMap<>();
        Map<String, Boolean> reactsForPost1 = new HashMap<>();
        reactsForPost1.put("l", true);
        reactsForPost1.put("h", false);
        myReacts.put("post-1", reactsForPost1);
        state.setMyReacts(myReacts);

        Map<String, Integer> likeCounts = new HashMap<>();
        likeCounts.put("post-1", 10);
        likeCounts.put("post-2", 0);
        state.setLikeCounts(likeCounts);

        assertTrue(state.getMyReacts().get("post-1").get("l"));
        assertFalse(state.getMyReacts().get("post-1").get("h"));
        assertEquals(Integer.valueOf(10), state.getLikeCounts().get("post-1"));
        assertEquals(Integer.valueOf(0), state.getLikeCounts().get("post-2"));
    }
}
