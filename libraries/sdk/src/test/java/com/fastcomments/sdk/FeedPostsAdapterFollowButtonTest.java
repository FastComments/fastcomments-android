package com.fastcomments.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.FeedPost;
import com.fastcomments.model.UserSessionInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for the follow / unfollow button wiring inside
 * {@link FeedPostsAdapter}. Covers visibility rules, state queries, click
 * propagation, optimistic-update revert, and — most importantly — the
 * RecyclerView recycling race where a provider callback may arrive after the
 * holder has been rebound to a different post.
 */
@RunWith(RobolectricTestRunner.class)
// Robolectric 4.11.1 (the project's pinned version) tops out at API 34,
// even though the SDK module compiles against compileSdk = 35. Bump this
// when Robolectric is upgraded.
@Config(sdk = 34)
public class FeedPostsAdapterFollowButtonTest {

    private Context context;
    private FrameLayout parent;
    private FastCommentsFeedSDK sdk;
    private FeedPostsAdapter.OnFeedPostInteractionListener listener;
    private List<FeedPost> posts;
    private FeedPostsAdapter adapter;
    private UserSessionInfo currentUser;

    @Before
    public void setup() {
        context = RuntimeEnvironment.getApplication();
        // Apply the SDK's Material-derived theme so layouts that reference
        // ?attr/selectableItemBackground (and similar AppCompat attrs) can be
        // inflated by Robolectric.
        context.setTheme(R.style.Theme_FastComments);
        parent = new FrameLayout(context);

        sdk = mock(FastCommentsFeedSDK.class);
        listener = mock(FeedPostsAdapter.OnFeedPostInteractionListener.class);

        when(sdk.getConfig()).thenReturn(new CommentWidgetConfig());
        when(sdk.getTheme()).thenReturn(null);

        currentUser = mock(UserSessionInfo.class);
        when(currentUser.getId()).thenReturn("viewer-1");
        when(sdk.getCurrentUser()).thenReturn(currentUser);

        posts = new ArrayList<>();
        adapter = new FeedPostsAdapter(context, posts, sdk, listener);
    }

    // ---------- helpers ----------

    private FeedPost makePost(String userId, String displayName) {
        FeedPost post = new FeedPost();
        post.setFromUserId(userId);
        post.setFromUserDisplayName(displayName);
        post.setContentHTML("<p>hello</p>");
        return post;
    }

    private FeedPostsAdapter.FeedPostViewHolder createAndBind(FeedPost post) {
        posts.clear();
        posts.add(post);
        FeedPostsAdapter.FeedPostViewHolder holder = adapter.onCreateViewHolder(
                parent, FeedPostType.TEXT_ONLY.ordinal());
        adapter.onBindViewHolder(holder, 0);
        return holder;
    }

    private TextView followButton(FeedPostsAdapter.FeedPostViewHolder holder) {
        return holder.itemView.findViewById(R.id.followButton);
    }

    /** Drains posted Runnables (View#post lands on the main Looper). */
    private void drainMainLooper() {
        ShadowLooper.idleMainLooper();
    }

    // ---------- visibility rules ----------

    @Test
    public void bindFollowButton_providerNull_hidesButton() {
        when(sdk.getFollowStateProvider()).thenReturn(null);

        FeedPostsAdapter.FeedPostViewHolder holder = createAndBind(makePost("u-other", "Other"));

        assertEquals(View.GONE, followButton(holder).getVisibility());
    }

    @Test
    public void bindFollowButton_anonymousViewer_hidesButton() {
        when(sdk.getCurrentUser()).thenReturn(null);
        when(sdk.getFollowStateProvider()).thenReturn(new StubProvider(false));

        FeedPostsAdapter.FeedPostViewHolder holder = createAndBind(makePost("u-other", "Other"));

        assertEquals(View.GONE, followButton(holder).getVisibility());
    }

    @Test
    public void bindFollowButton_selfPost_hidesButton() {
        when(sdk.getFollowStateProvider()).thenReturn(new StubProvider(false));

        FeedPostsAdapter.FeedPostViewHolder holder =
                createAndBind(makePost("viewer-1", "Me"));

        assertEquals(View.GONE, followButton(holder).getVisibility());
    }

    @Test
    public void bindFollowButton_postWithNoUserId_hidesButton() {
        when(sdk.getFollowStateProvider()).thenReturn(new StubProvider(false));

        FeedPost post = makePost(null, "Anon");
        FeedPostsAdapter.FeedPostViewHolder holder = createAndBind(post);

        assertEquals(View.GONE, followButton(holder).getVisibility());
    }

    // ---------- initial state binding ----------

    @Test
    public void bindFollowButton_providerReturnsNotFollowing_showsFollowLabel() {
        when(sdk.getFollowStateProvider()).thenReturn(new StubProvider(false));

        FeedPostsAdapter.FeedPostViewHolder holder = createAndBind(makePost("u-bob", "Bob"));
        TextView btn = followButton(holder);

        assertEquals(View.VISIBLE, btn.getVisibility());
        assertEquals("Follow", btn.getText().toString());
        assertTrue(btn.isEnabled());
    }

    @Test
    public void bindFollowButton_providerReturnsFollowing_showsFollowingLabel() {
        when(sdk.getFollowStateProvider()).thenReturn(new StubProvider(true));

        FeedPostsAdapter.FeedPostViewHolder holder = createAndBind(makePost("u-bob", "Bob"));
        TextView btn = followButton(holder);

        assertEquals(View.VISIBLE, btn.getVisibility());
        assertEquals("Following", btn.getText().toString());
    }

    // ---------- click + callback ----------

    @Test
    public void clickFollow_invokesProviderAndUpdatesUI() {
        RecordingProvider provider = new RecordingProvider();
        when(sdk.getFollowStateProvider()).thenReturn(provider);

        FeedPostsAdapter.FeedPostViewHolder holder = createAndBind(makePost("u-bob", "Bob"));
        TextView btn = followButton(holder);

        assertEquals("Follow", btn.getText().toString());

        btn.performClick();

        // Optimistic UI: shown as Following + button disabled
        assertEquals("Following", btn.getText().toString());
        assertFalse(btn.isEnabled());

        // Provider was called with desired = true
        assertNotNull(provider.lastUser);
        assertEquals("u-bob", provider.lastUser.getUserId());
        assertEquals(Boolean.TRUE, provider.lastDesired);

        // Resolve the callback
        provider.lastCallback.onResult(true);
        drainMainLooper();

        assertEquals("Following", btn.getText().toString());
        assertTrue(btn.isEnabled());
    }

    @Test
    public void clickFollow_callbackWithUnchangedState_revertsOptimisticUI() {
        RecordingProvider provider = new RecordingProvider();
        when(sdk.getFollowStateProvider()).thenReturn(provider);

        FeedPostsAdapter.FeedPostViewHolder holder = createAndBind(makePost("u-bob", "Bob"));
        TextView btn = followButton(holder);

        btn.performClick();
        assertEquals("Following", btn.getText().toString());

        // Provider rejects the change → still NOT following
        provider.lastCallback.onResult(false);
        drainMainLooper();

        assertEquals("Follow", btn.getText().toString());
        assertTrue(btn.isEnabled());
    }

    // ---------- recycling race ----------

    @Test
    public void staleCallback_afterRecycling_doesNotPaintOldRow() {
        // Provider that captures the callback so the test controls when it
        // resolves.
        final RecordingProvider provider = new RecordingProvider();
        when(sdk.getFollowStateProvider()).thenReturn(provider);

        // Bind holder to Alice
        FeedPostsAdapter.FeedPostViewHolder holder =
                createAndBind(makePost("u-alice", "Alice"));
        TextView btn = followButton(holder);

        assertEquals("Follow", btn.getText().toString());

        // Tap follow on Alice's row → optimistic Following + captured callback
        btn.performClick();
        assertEquals("Following", btn.getText().toString());
        assertEquals("u-alice", provider.lastUser.getUserId());
        FollowStateProvider.FollowStateCallback aliceCallback = provider.lastCallback;
        assertNotNull(aliceCallback);

        // Simulate RecyclerView recycling: rebind THIS holder to Bob's post.
        // (Bob is not followed → button should reset to "Follow".)
        posts.set(0, makePost("u-bob", "Bob"));
        adapter.onBindViewHolder(holder, 0);
        assertEquals("Follow", btn.getText().toString());
        assertTrue("Re-bind must re-enable the button", btn.isEnabled());

        // Now Alice's stale callback fires (e.g. her network request finally
        // returned). It must NOT paint Alice's "Following" state onto Bob's
        // row.
        aliceCallback.onResult(true);
        drainMainLooper();

        assertEquals("Follow", btn.getText().toString());
        assertTrue(btn.isEnabled());
    }

    // ---------- cross-row synchronization ----------

    /**
     * When the viewer follows Alice from post #1, any other visible post also
     * authored by Alice must refresh its pill to "Following" — the whole point
     * of wiring the SDK invalidation broadcast. Mirrors the iOS
     * {@code followStateRevision} behaviour added in the companion commit on
     * fastcomments-ios.
     */
    @Test
    public void clickFollow_doesNotBroadcastUntilProviderResolves() {
        RecordingProvider provider = new RecordingProvider();
        when(sdk.getFollowStateProvider()).thenReturn(provider);

        FeedPostsAdapter.FeedPostViewHolder holder =
                createAndBind(makePost("u-alice", "Alice"));

        followButton(holder).performClick();
        // Provider hasn't called back yet → nothing broadcast either coarse
        // or scoped.
        verify(sdk, never()).invalidateFollowState();
        verify(sdk, never()).invalidateFollowState(any());
    }

    /**
     * If the originating row is recycled / rebound before the provider
     * resolves, the per-row apply is correctly skipped (the stale-generation
     * guard) — but the SDK-level invalidation <em>must still fire</em>,
     * because it's a global signal about provider truth that other visible
     * rows (e.g. a second post by the same author still on screen) depend on
     * to refresh. If the broadcast is swallowed here, those other rows stay
     * stale until something else forces a rebind.
     */
    @Test
    public void clickFollow_thenRowRecycled_stillBroadcastsInvalidation() {
        RecordingProvider provider = new RecordingProvider();
        when(sdk.getFollowStateProvider()).thenReturn(provider);

        FeedPostsAdapter.FeedPostViewHolder holder =
                createAndBind(makePost("u-alice", "Alice"));

        followButton(holder).performClick();
        assertNotNull(provider.lastCallback);

        // Simulate RecyclerView recycling: rebind this holder to a different
        // post BEFORE the provider resolves. Bumps the follow generation so
        // the per-row apply path sees a stale callback.
        posts.set(0, makePost("u-bob", "Bob"));
        adapter.onBindViewHolder(holder, 0);

        provider.backingState = true;
        provider.lastCallback.onResult(true);
        drainMainLooper();

        // Even though Alice's row was recycled, the broadcast must still
        // reach any other visible Alice row. The originating user id
        // ("u-alice") is captured before recycling so the scoped variant
        // still carries the right target.
        verify(sdk).invalidateFollowState("u-alice");
    }

    /**
     * A partial-bind with the {@link FeedPostsAdapter.UpdateType#FOLLOW_STATE_UPDATE}
     * payload must re-query the provider and apply whatever state it currently
     * reports — this is the refresh path the invalidation broadcast fans out
     * to visible rows.
     */
    @Test
    public void partialBind_withFollowStatePayload_refreshesFromProvider() {
        RecordingProvider provider = new RecordingProvider();
        when(sdk.getFollowStateProvider()).thenReturn(provider);

        FeedPostsAdapter.FeedPostViewHolder holder =
                createAndBind(makePost("u-alice", "Alice"));
        TextView btn = followButton(holder);

        assertEquals("Follow", btn.getText().toString());

        // Provider's truth changes (e.g. because the user followed Alice on
        // another row). A payload-only rebind should pick that up without a
        // full rebind of the rest of the row.
        provider.backingState = true;
        adapter.onBindViewHolder(holder, 0,
                Collections.singletonList(FeedPostsAdapter.UpdateType.FOLLOW_STATE_UPDATE));

        assertEquals("Following", btn.getText().toString());
        assertTrue(btn.isEnabled());
    }

    /**
     * The adapter must register its invalidation listener on attach and drop
     * it on detach so a stale adapter isn't kept alive by the SDK after the
     * host view is gone.
     */
    @Test
    public void onAttached_registersListener_onDetached_unregisters() {
        RecyclerView rv = mock(RecyclerView.class);

        adapter.onAttachedToRecyclerView(rv);
        ArgumentCaptor<FastCommentsFeedSDK.FollowStateInvalidationListener> captor =
                ArgumentCaptor.forClass(FastCommentsFeedSDK.FollowStateInvalidationListener.class);
        verify(sdk).addFollowStateInvalidationListener(captor.capture());
        FastCommentsFeedSDK.FollowStateInvalidationListener registered = captor.getValue();
        assertNotNull(registered);

        adapter.onDetachedFromRecyclerView(rv);
        verify(sdk).removeFollowStateInvalidationListener(registered);
    }

    /**
     * Broadcast invalidation (null userId) must fan out to every currently-
     * bound post, since the caller hasn't told us which user changed.
     */
    @Test
    public void onInvalidation_broadcast_notifiesItemRangeChangedWithFollowStatePayload() {
        RecyclerView rv = mock(RecyclerView.class);
        adapter.onAttachedToRecyclerView(rv);
        ArgumentCaptor<FastCommentsFeedSDK.FollowStateInvalidationListener> captor =
                ArgumentCaptor.forClass(FastCommentsFeedSDK.FollowStateInvalidationListener.class);
        verify(sdk).addFollowStateInvalidationListener(captor.capture());

        posts.clear();
        posts.add(makePost("u-alice", "Alice"));
        posts.add(makePost("u-alice", "Alice"));
        posts.add(makePost("u-bob", "Bob"));

        RecyclerView.AdapterDataObserver observer = mock(RecyclerView.AdapterDataObserver.class);
        adapter.registerAdapterDataObserver(observer);

        captor.getValue().onFollowStateInvalidated(null);

        verify(observer).onItemRangeChanged(eq(0), eq(3),
                eq(FeedPostsAdapter.UpdateType.FOLLOW_STATE_UPDATE));
    }

    /**
     * Scoped invalidation (a specific user id) must only fan out to rows
     * whose author matches — so in a feed of 50 posts, a single follow tap
     * doesn't force 50 calls into {@code FollowStateProvider.isFollowing}.
     */
    @Test
    public void onInvalidation_scopedToUserId_onlyNotifiesMatchingRows() {
        RecyclerView rv = mock(RecyclerView.class);
        adapter.onAttachedToRecyclerView(rv);
        ArgumentCaptor<FastCommentsFeedSDK.FollowStateInvalidationListener> captor =
                ArgumentCaptor.forClass(FastCommentsFeedSDK.FollowStateInvalidationListener.class);
        verify(sdk).addFollowStateInvalidationListener(captor.capture());

        posts.clear();
        posts.add(makePost("u-alice", "Alice"));      // 0
        posts.add(makePost("u-bob", "Bob"));          // 1
        posts.add(makePost("u-alice", "Alice"));      // 2
        posts.add(makePost("u-bob", "Bob"));          // 3
        posts.add(makePost("u-alice", "Alice"));      // 4

        RecyclerView.AdapterDataObserver observer = mock(RecyclerView.AdapterDataObserver.class);
        adapter.registerAdapterDataObserver(observer);

        captor.getValue().onFollowStateInvalidated("u-alice");

        // Only the three Alice rows should be invalidated.
        verify(observer).onItemRangeChanged(eq(0), eq(1),
                eq(FeedPostsAdapter.UpdateType.FOLLOW_STATE_UPDATE));
        verify(observer).onItemRangeChanged(eq(2), eq(1),
                eq(FeedPostsAdapter.UpdateType.FOLLOW_STATE_UPDATE));
        verify(observer).onItemRangeChanged(eq(4), eq(1),
                eq(FeedPostsAdapter.UpdateType.FOLLOW_STATE_UPDATE));
        // And none of the Bob rows.
        verify(observer, never()).onItemRangeChanged(eq(1), eq(1),
                eq(FeedPostsAdapter.UpdateType.FOLLOW_STATE_UPDATE));
        verify(observer, never()).onItemRangeChanged(eq(3), eq(1),
                eq(FeedPostsAdapter.UpdateType.FOLLOW_STATE_UPDATE));
    }

    /**
     * A follow tap must broadcast the author's user id, not the coarse
     * no-arg form, so adapters can skip rows whose author didn't change.
     */
    @Test
    public void clickFollow_broadcastsInvalidationScopedToAuthorUserId() {
        RecordingProvider provider = new RecordingProvider();
        when(sdk.getFollowStateProvider()).thenReturn(provider);

        FeedPostsAdapter.FeedPostViewHolder holder =
                createAndBind(makePost("u-alice", "Alice"));

        followButton(holder).performClick();
        provider.backingState = true;
        provider.lastCallback.onResult(true);
        drainMainLooper();

        verify(sdk).invalidateFollowState("u-alice");
    }

    // ---------- test doubles ----------

    /** Returns a fixed isFollowing value; ignores writes. */
    private static class StubProvider implements FollowStateProvider {
        private final boolean following;

        StubProvider(boolean following) {
            this.following = following;
        }

        @Override
        public boolean isFollowing(@NonNull UserInfo user) {
            return following;
        }

        @Override
        public void onFollowStateChangeRequested(@NonNull UserInfo user, boolean desiredFollowing,
                                                 @NonNull FollowStateCallback resultCallback) {
            // No-op. Tests using this stub never tap the button.
        }
    }

    /** Records the most recent change request and exposes the callback. */
    private static class RecordingProvider implements FollowStateProvider {
        UserInfo lastUser;
        Boolean lastDesired;
        FollowStateCallback lastCallback;
        boolean backingState = false;

        @Override
        public boolean isFollowing(@NonNull UserInfo user) {
            return backingState;
        }

        @Override
        public void onFollowStateChangeRequested(@NonNull UserInfo user, boolean desiredFollowing,
                                                 @NonNull FollowStateCallback resultCallback) {
            lastUser = user;
            lastDesired = desiredFollowing;
            lastCallback = resultCallback;
        }
    }
}
