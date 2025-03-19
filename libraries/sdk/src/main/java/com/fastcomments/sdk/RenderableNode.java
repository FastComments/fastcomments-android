package com.fastcomments.sdk;

import java.util.Map;

/**
 * Base class for renderable nodes in the comment tree
 */
public abstract class RenderableNode {
    public abstract int determineNestingLevel(Map<String, RenderableComment> commentMap);
}