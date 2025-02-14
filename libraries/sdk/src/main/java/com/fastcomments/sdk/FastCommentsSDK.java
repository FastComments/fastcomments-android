package com.fastcomments.sdk;

import android.content.Context;

import com.fastcomments.model.APICommentPublicComment;

import java.util.ArrayList;
import java.util.List;

public class FastCommentsSDK {

    private static volatile FastCommentsSDK instance;
    private Context context;
    private User currentUser;
    private SSOOptions ssoOptions;

    private FastCommentsSDK(Context context) {
        this.context = context;
    }

    public static FastCommentsSDK init(Context context) {
        if (instance == null) {
            synchronized (FastCommentsSDK.class) {
                if (instance == null) {
                    instance = new FastCommentsSDK(context);
                }
            }
        }
        return instance;
    }
    public void fetchComments(int page, CommentsCallback callback) {
        callback.onCommentsFetched(comments);
    }

    public interface CommentsCallback {
        void onCommentsFetched(List<APICommentPublicComment> comments);
    }
}
