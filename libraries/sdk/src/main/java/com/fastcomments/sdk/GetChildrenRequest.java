package com.fastcomments.sdk;

import android.widget.Button;

/**
 * Request object that contains both the parent ID and the button that was clicked.
 * Used when loading children comments.
 */
public class GetChildrenRequest {
    private final String parentId;
    private final Button toggleButton;

    public GetChildrenRequest(String parentId, Button toggleButton) {
        this.parentId = parentId;
        this.toggleButton = toggleButton;
    }

    public String getParentId() {
        return parentId;
    }

    public Button getToggleButton() {
        return toggleButton;
    }
}