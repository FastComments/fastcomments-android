package com.fastcomments.sdk;

/**
 * Represents a user that can be mentioned in a comment
 */
public class UserMention {
    private String id;
    private String username;
    private String avatarUrl;
    private boolean isMentioned;
    private boolean notificationSent;

    public UserMention(String id, String username, String avatarUrl) {
        this.id = id;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.isMentioned = false;
        this.notificationSent = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public boolean isMentioned() {
        return isMentioned;
    }

    public void setMentioned(boolean mentioned) {
        isMentioned = mentioned;
    }

    public boolean isNotificationSent() {
        return notificationSent;
    }

    public void setNotificationSent(boolean notificationSent) {
        this.notificationSent = notificationSent;
    }
}