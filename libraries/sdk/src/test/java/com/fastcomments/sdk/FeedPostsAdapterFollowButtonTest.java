package com.fastcomments.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.FeedPost;
import com.fastcomments.model.UserSessionInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
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
