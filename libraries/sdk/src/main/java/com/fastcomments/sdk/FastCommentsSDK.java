package com.fastcomments.sdk;

import android.content.Context;

import com.fastcomments.core.CommentClient;
import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.APICommentPublicComment;
import com.fastcomments.model.SSO;
import com.fastcomments.model.UserSessionInfo;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;

public class FastCommentsSDK {

    private static FastCommentsSDK instance;
    private final Context context;
    private UserSessionInfo currentUser;
    private CommentWidgetConfig config;
    private final Executor executor;
    private final Handler mainHandler;

    /**
     * Initialize the FastComments SDK
     * @param context Application context
     * @return FastCommentsSDK instance
     */
    public static synchronized FastCommentsSDK init(Context context) {
        if (instance == null) {
            instance = new FastCommentsSDK(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Get the FastComments SDK instance
     * @return FastCommentsSDK instance
     * @throws IllegalStateException if SDK not initialized
     */
    public static synchronized FastCommentsSDK getInstance() {
        if (instance == null) {
            throw new IllegalStateException("FastCommentsSDK not initialized. Call init() first.");
        }
        return instance;
    }

    private FastCommentsSDK(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Configure the SDK with widget configuration
     * @param config CommentWidgetConfig object
     * @return FastCommentsSDK instance for chaining
     */
    public FastCommentsSDK configure(CommentWidgetConfig config) {
        this.config = config;
        return this;
    }

    /**
     * Set the current user for SSO authentication
     * @param userInfo User session information
     * @return FastCommentsSDK instance for chaining
     */
    public FastCommentsSDK setCurrentUser(UserSessionInfo userInfo) {
        this.currentUser = userInfo;
        return this;
    }

    /**
     * Get the current widget configuration
     * @return CommentWidgetConfig object
     */
    public CommentWidgetConfig getConfig() {
        return config;
    }

    /**
     * Get the current user information
     * @return UserSessionInfo object
     */
    public UserSessionInfo getCurrentUser() {
        return currentUser;
    }

    /**
     * Callback interface for comment loading
     */
    public interface CommentsCallback {
        void onSuccess(List<APICommentPublicComment> comments);
        void onError(Exception e);
    }

    /**
     * Callback interface for comment posting
     */
    public interface CommentPostCallback {
        void onSuccess(APICommentPublicComment comment);
        void onError(Exception e);
    }

    /**
     * Load comments asynchronously
     * @param callback Callback to receive the comments
     */
    public void getComments(final CommentsCallback callback) {
        if (config == null) {
            callback.onError(new IllegalStateException("SDK not configured. Call configure() first."));
            return;
        }

        executor.execute(() -> {
            try {
                List<APICommentPublicComment> comments = commentClient.getComments(config);
                mainHandler.post(() -> callback.onSuccess(comments));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * Post a new comment asynchronously
     * @param commentText Comment text content
     * @param parentId Parent comment ID for replies (null for top-level comments)
     * @param callback Callback to receive the posted comment
     */
    public void postComment(String commentText, String parentId, final CommentPostCallback callback) {
        if (config == null) {
            callback.onError(new IllegalStateException("SDK not configured. Call configure() first."));
            return;
        }

        if (currentUser == null) {
            callback.onError(new IllegalStateException("User not set. Call setCurrentUser() first."));
            return;
        }

        executor.execute(() -> {
            try {
                APICommentPublicComment comment = commentClient.postComment(config, commentText, parentId, currentUser);
                mainHandler.post(() -> callback.onSuccess(comment));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * Create a CommentWidgetConfig with the specified tenant ID and URL ID
     * @param tenantId Your FastComments tenant ID
     * @param urlId URL ID for the comment thread
     * @return CommentWidgetConfig object
     */
    public static CommentWidgetConfig createConfig(String tenantId, String urlId) {
        CommentWidgetConfig config = new CommentWidgetConfig();
        config.setTenantId(tenantId);
        config.setUrlId(urlId);
        return config;
    }

    /**
     * Create a CommentWidgetConfig with SSO enabled
     * @param tenantId Your FastComments tenant ID
     * @param urlId URL ID for the comment thread
     * @param sso SSO configuration
     * @return CommentWidgetConfig object
     */
    public static CommentWidgetConfig createConfigWithSSO(String tenantId, String urlId, SSO sso) {
        CommentWidgetConfig config = createConfig(tenantId, urlId);
        config.setSso(sso);
        return config;
    }
}