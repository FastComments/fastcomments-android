package com.fastcomments.sdk;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for RenderableNode/RenderableComment nesting level calculations.
 */
public class RenderableNodeTests {

    @Test
    public void testNestingLevelRoot() {
        Map<String, RenderableComment> map = new HashMap<>();
        RenderableComment root = new RenderableComment(MockComment.make("root"));
        map.put("root", root);

        assertEquals(0, root.determineNestingLevel(map));
    }

    @Test
    public void testNestingLevelDepth1() {
        Map<String, RenderableComment> map = new HashMap<>();
        RenderableComment root = new RenderableComment(MockComment.make("root"));
        RenderableComment child = new RenderableComment(MockComment.make("child", null, "root"));
        map.put("root", root);
        map.put("child", child);

        assertEquals(1, child.determineNestingLevel(map));
    }

    @Test
    public void testNestingLevelDepth3() {
        Map<String, RenderableComment> map = new HashMap<>();
        RenderableComment c0 = new RenderableComment(MockComment.make("c0"));
        RenderableComment c1 = new RenderableComment(MockComment.make("c1", null, "c0"));
        RenderableComment c2 = new RenderableComment(MockComment.make("c2", null, "c1"));
        RenderableComment c3 = new RenderableComment(MockComment.make("c3", null, "c2"));
        map.put("c0", c0);
        map.put("c1", c1);
        map.put("c2", c2);
        map.put("c3", c3);

        assertEquals(3, c3.determineNestingLevel(map));
    }

    @Test
    public void testNestingLevelOrphan() {
        Map<String, RenderableComment> map = new HashMap<>();
        RenderableComment orphan = new RenderableComment(MockComment.make("orphan", null, "missing-parent"));
        map.put("orphan", orphan);

        // Android returns 1 for orphans (parent not in map but parentId is set)
        assertEquals(1, orphan.determineNestingLevel(map));
    }
}
