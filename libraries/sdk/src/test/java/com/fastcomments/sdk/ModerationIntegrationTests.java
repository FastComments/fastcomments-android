package com.fastcomments.sdk;

import com.fastcomments.model.APIError;
import com.fastcomments.model.PublicComment;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for admin moderation operations (pin, lock, flag, block).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ModerationIntegrationTests extends IntegrationTestBase {

    @Test
    public void testFlagAndUnflag() throws Exception {
        FastCommentsSDK sdk = makeSDK("testFlagAndUnflag");
        loadSync(sdk);

        PublicComment comment = postCommentSync(sdk, "Flag target");

        // Flag
        flagCommentSync(sdk, comment.getId());

        // Reload and verify comment still exists (isFlagged is not exposed in the public API response)
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);
        PublicComment reloaded = sdk2.commentsTree.getPublicComment(comment.getId());
        assertNotNull("Comment should still exist after flagging", reloaded);

        // Unflag
        unflagCommentSync(sdk, comment.getId());

        // Reload and verify comment still exists after unflagging
        FastCommentsSDK sdk3 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk3);
        PublicComment reloaded2 = sdk3.commentsTree.getPublicComment(comment.getId());
        assertNotNull("Comment should still exist after unflagging", reloaded2);
    }

    @Test
    public void testPinAndUnpin() throws Exception {
        String urlId = makeUrlId("testPinAndUnpin");
        FastCommentsSDK sdk = makeSDKWithUrlId(urlId);
        loadSync(sdk);
        PublicComment comment = postCommentSync(sdk, "Pin target");

        // Pin with admin
        FastCommentsSDK adminSdk = makeAdminSDKWithUrlId(urlId);
        loadSync(adminSdk);
        pinCommentSync(adminSdk, comment.getId());

        // Reload and verify pinned
        FastCommentsSDK sdk2 = makeSDKWithUrlId(urlId);
        loadSync(sdk2);
        PublicComment reloaded = sdk2.commentsTree.getPublicComment(comment.getId());
        assertNotNull(reloaded);
        assertTrue("Comment should be pinned", reloaded.getIsPinned() != null && reloaded.getIsPinned());

        // Unpin
        unpinCommentSync(adminSdk, comment.getId());

        // Reload and verify unpinned
        FastCommentsSDK sdk3 = makeSDKWithUrlId(urlId);
        loadSync(sdk3);
        PublicComment reloaded2 = sdk3.commentsTree.getPublicComment(comment.getId());
        assertNotNull(reloaded2);
        assertTrue("Comment should not be pinned",
                reloaded2.getIsPinned() == null || !reloaded2.getIsPinned());
    }

    @Test
    public void testLockAndUnlock() throws Exception {
        String urlId = makeUrlId("testLockAndUnlock");
        FastCommentsSDK sdk = makeSDKWithUrlId(urlId);
        loadSync(sdk);
        PublicComment comment = postCommentSync(sdk, "Lock target");

        // Lock with admin
        FastCommentsSDK adminSdk = makeAdminSDKWithUrlId(urlId);
        loadSync(adminSdk);
        lockCommentSync(adminSdk, comment.getId());

        // Reload and verify locked
        FastCommentsSDK sdk2 = makeSDKWithUrlId(urlId);
        loadSync(sdk2);
        PublicComment reloaded = sdk2.commentsTree.getPublicComment(comment.getId());
        assertNotNull(reloaded);
        assertTrue("Comment should be locked", reloaded.getIsLocked() != null && reloaded.getIsLocked());

        // Unlock
        unlockCommentSync(adminSdk, comment.getId());

        // Reload and verify unlocked
        FastCommentsSDK sdk3 = makeSDKWithUrlId(urlId);
        loadSync(sdk3);
        PublicComment reloaded2 = sdk3.commentsTree.getPublicComment(comment.getId());
        assertNotNull(reloaded2);
        assertTrue("Comment should not be locked",
                reloaded2.getIsLocked() == null || !reloaded2.getIsLocked());
    }

    @Test
    public void testNonAdminCannotPin() throws Exception {
        FastCommentsSDK sdk = makeSDK("testNonAdminCannotPin");
        loadSync(sdk);
        PublicComment comment = postCommentSync(sdk, "Pin attempt");

        // Regular user tries to pin - should fail
        APIError error = pinCommentSyncExpectFailure(sdk, comment.getId());
        assertNotNull("Non-admin pin should fail", error);
    }

    @Test
    public void testNonAdminCannotLock() throws Exception {
        FastCommentsSDK sdk = makeSDK("testNonAdminCannotLock");
        loadSync(sdk);
        PublicComment comment = postCommentSync(sdk, "Lock attempt");

        // Regular user tries to lock - should fail
        APIError error = lockCommentSyncExpectFailure(sdk, comment.getId());
        assertNotNull("Non-admin lock should fail", error);
    }

    @Test
    public void testBlockAndUnblock() throws Exception {
        String urlId = makeUrlId("testBlockAndUnblock");

        // User A creates a comment
        FastCommentsSDK sdkA = makeSDKWithUrlId(urlId);
        loadSync(sdkA);
        PublicComment commentA = postCommentSync(sdkA, "Comment by user A");

        // User B blocks User A's comment
        FastCommentsSDK sdkB = makeSDKWithUrlId(urlId);
        loadSync(sdkB);
        blockUserSync(sdkB, commentA.getId());

        // Unblock
        unblockUserSync(sdkB, commentA.getId());
    }
}
