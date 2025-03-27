package com.fastcomments.sdk;

import android.widget.Button;

/**
 * Request object that contains both the parent ID and the button that was clicked.
 * Used when loading children comments.
 */
public class GetChildrenRequest {
    private final String parentId;
    private final Button toggleButton;
    private final Integer skip;
    private final Integer limit;
    private final boolean isLoadMore;

    public GetChildrenRequest(String parentId, Button toggleButton) {
        this(parentId, toggleButton, null, null, false);
    }
    
    public GetChildrenRequest(String parentId, Button toggleButton, Integer skip, Integer limit, boolean isLoadMore) {
        this.parentId = parentId;
        this.toggleButton = toggleButton;
        this.skip = skip;
        this.limit = limit;
        this.isLoadMore = isLoadMore;
    }

    public String getParentId() {
        return parentId;
    }

    public Button getToggleButton() {
        return toggleButton;
    }
    
    public Integer getSkip() {
        return skip;
    }
    
    public Integer getLimit() {
        return limit;
    }
    
    public boolean isLoadMore() {
        return isLoadMore;
    }
}