package com.fastcomments.sdk;

import com.fastcomments.model.PublicComment;
import com.fastcomments.model.FeedPost;

public class UserInfo {
    
    private final String userId;
    private final String userName;
    private final String userAvatarUrl;
    
    public UserInfo(String userId, String userName, String userAvatarUrl) {
        this.userId = userId;
        this.userName = userName;
        this.userAvatarUrl = userAvatarUrl;
    }
    
    public static UserInfo fromComment(PublicComment comment) {
        return new UserInfo(
            comment.getUserId(),
            comment.getCommenterName(),
            comment.getAvatarSrc()
        );
    }
    
    public static UserInfo fromFeedPost(FeedPost feedPost) {
        return new UserInfo(
            feedPost.getFromUserId(),
            feedPost.getFromUserDisplayName(),
            feedPost.getFromUserAvatar()
        );
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getUserName() {
        return userName;
    }
    
    public String getUserAvatarUrl() {
        return userAvatarUrl;
    }
    
    public String getDisplayName() {
        return userName != null ? userName : "Anonymous";
    }
}