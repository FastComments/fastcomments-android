package com.fastcomments.sdk;

import androidx.annotation.NonNull;

/**
 * Provides follow / unfollow state for users surfaced in feed posts.
 * Register an implementation via
 * {@link FastCommentsFeedSDK#setFollowStateProvider(FollowStateProvider)}.
 *
 * <p>When no provider is registered (i.e. it is {@code null}), the
 * follow / unfollow button is not rendered in feed post items.</p>
 *
 * <p>The SDK does not persist follow state itself; the implementation is
 * expected to read from (and write to) its own backend or local cache.</p>
 *
 * <h3>When the SDK skips the provider</h3>
 * The SDK will not call into the provider, and the follow button will not be
 * rendered, in any of the following situations:
 * <ul>
 *   <li>there is no authenticated viewer ({@code sdk.getCurrentUser() == null}),</li>
 *   <li>the post has no author id (anonymous post), or</li>
 *   <li>the post is authored by the current viewer (you can't follow yourself).</li>
 * </ul>
 * Implementations therefore do not need to defend against these cases.
 *
 * <h3>Theme contrast</h3>
 * The follow button is rendered as inline text next to the post author's
 * name (no pill background). "Follow" uses the configured theme action
 * color; "Following" uses a neutral gray that adapts for light / dark
 * surfaces via {@code values-night}. While a state change is in flight the
 * label is disabled and its alpha is lowered, mirroring the iOS app.
 */
public interface FollowStateProvider {

    /**
     * Synchronously return the currently-known follow state for {@code user}.
     * Called during view binding, so it must be fast (e.g. backed by an
     * in-memory cache). Return {@code false} if state is not yet known.
     *
     * <p>Always invoked on the main (UI) thread.</p>
     *
     * @param user the post author being rendered
     * @return {@code true} if the current viewer is following {@code user}
     */
    boolean isFollowing(@NonNull UserInfo user);

    /**
     * Called when the viewer taps the follow / unfollow button. The
     * implementation should update its own state (e.g. call its backend) and
     * invoke {@code resultCallback} with the resulting state when complete.
     * If the call fails, invoke {@code resultCallback} with the unchanged
     * state to revert the optimistic UI update.
     *
     * <p>Always invoked on the main (UI) thread. The SDK temporarily disables
     * the follow button until {@code resultCallback} fires, so the
     * implementation <strong>must</strong> always invoke
     * {@code resultCallback} exactly once (success path or error path) — even
     * if the request was a no-op — otherwise the button will remain
     * disabled.</p>
     *
     * @param user             the post author being followed / unfollowed
     * @param desiredFollowing the requested new state ({@code true} = follow)
     * @param resultCallback   invoked with the actual state after the change
     */
    void onFollowStateChangeRequested(
            @NonNull UserInfo user,
            boolean desiredFollowing,
            @NonNull FollowStateCallback resultCallback);

    /**
     * Result callback used to confirm a follow-state change (or revert it).
     *
     * <p>{@link #onResult(boolean)} may be invoked from <em>any</em> thread —
     * the SDK marshals the call to the main thread internally before touching
     * the UI, so implementations are free to call back directly from a
     * network completion handler, an executor, etc.</p>
     */
    interface FollowStateCallback {
        /**
         * Invoke with the actual follow state after the requested change has
         * been processed. Pass the unchanged state to revert an optimistic UI
         * update if the change failed. Safe to call from any thread.
         */
        void onResult(boolean nowFollowing);
    }
}
