package com.fastcomments;

import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.LiveEvent;
import com.fastcomments.pubsub.LiveEventSubscriber;
import com.fastcomments.pubsub.SubscribeToChangesResult;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static org.junit.Assert.*;

/**
 * Integration test for the presence update system.
 * No views, no activity — just the pubsub library talking to the live server.
 *
 * UserA subscribes to a room. UserB subscribes to the same room.
 * UserA should receive a p-u event with UserB in the "uj" (users joined) list.
 * Then UserB disconnects and reconnects. UserA should receive another p-u
 * with UserB in "uj" again (not cancelled by "ul").
 */
@RunWith(AndroidJUnit4.class)
public class PresenceIntegrationTest extends UITestBase {

    private static final String TAG = "PresenceTest";

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        createTestTenant("android-presence@fctest.com");
    }

    @Test
    public void testUserBJoinVisibleToUserA() throws Exception {
        String urlId = "presence-" + System.currentTimeMillis();

        // Get WS params for UserA
        String ssoA = makeSecureSSOToken("presA");
        String[] wsParamsA = getWsParams(urlId, ssoA);

        // Subscribe UserA
        LiveEventSubscriber subscriberA = LiveEventSubscriber.createTesting();
        CommentWidgetConfig configA = new CommentWidgetConfig(testTenantId, urlId);
        List<String> usersJoinedSeen = new CopyOnWriteArrayList<>();
        CountDownLatch joinLatch = new CountDownLatch(1);

        subscriberA.setOnConnectionStatusChange((connected, lastEventTime) ->
                Log.d(TAG, "UserA connection: " + connected));

        SubscribeToChangesResult resultA = subscriberA.subscribeToChanges(
                configA, wsParamsA[0], urlId, wsParamsA[1], wsParamsA[2],
                null, event -> {
                    if (event.getType() != null && "p-u".equals(event.getType().getValue())) {
                        List<String> uj = event.getUj();
                        if (uj != null) {
                            for (String uid : uj) {
                                Log.d(TAG, "UserA saw join: " + uid);
                                if (uid.contains("presB")) {
                                    usersJoinedSeen.add(uid);
                                    joinLatch.countDown();
                                }
                            }
                        }
                    }
                });
        assertNotNull("UserA should subscribe", resultA);
        Log.d(TAG, "UserA subscribed, waiting 2s for connection to stabilize...");
        Thread.sleep(2000);

        // Now subscribe UserB to the same room
        String ssoB = makeSecureSSOToken("presB");
        String[] wsParamsB = getWsParams(urlId, ssoB);

        LiveEventSubscriber subscriberB = LiveEventSubscriber.createTesting();
        CommentWidgetConfig configB = new CommentWidgetConfig(testTenantId, urlId);
        SubscribeToChangesResult resultB = subscriberB.subscribeToChanges(
                configB, wsParamsB[0], urlId, wsParamsB[1], wsParamsB[2],
                null, event -> {});
        assertNotNull("UserB should subscribe", resultB);
        Log.d(TAG, "UserB subscribed, waiting for UserA to see the join...");

        boolean sawJoin = joinLatch.await(10, TimeUnit.SECONDS);
        Log.d(TAG, "UserA saw UserB join: " + sawJoin + " joins=" + usersJoinedSeen);

        resultB.close();
        resultA.close();

        assertTrue("UserA should see UserB in uj list", sawJoin);
        assertTrue("Joined list should contain presB", usersJoinedSeen.stream().anyMatch(u -> u.contains("presB")));
    }

    @Test
    public void testUserBReconnectVisibleToUserA() throws Exception {
        String urlId = "presence-reconn-" + System.currentTimeMillis();

        String ssoA = makeSecureSSOToken("reconA");
        String[] wsParamsA = getWsParams(urlId, ssoA);

        LiveEventSubscriber subscriberA = LiveEventSubscriber.createTesting();
        CommentWidgetConfig configA = new CommentWidgetConfig(testTenantId, urlId);
        List<String> allJoins = new CopyOnWriteArrayList<>();
        CountDownLatch secondJoinLatch = new CountDownLatch(1);

        subscriberA.setOnConnectionStatusChange((connected, lastEventTime) ->
                Log.d(TAG, "UserA connection: " + connected));

        SubscribeToChangesResult resultA = subscriberA.subscribeToChanges(
                configA, wsParamsA[0], urlId, wsParamsA[1], wsParamsA[2],
                null, event -> {
                    if (event.getType() != null && "p-u".equals(event.getType().getValue())) {
                        List<String> uj = event.getUj();
                        if (uj != null) {
                            for (String uid : uj) {
                                if (uid.contains("reconB")) {
                                    allJoins.add(uid);
                                    Log.d(TAG, "UserA saw reconB join #" + allJoins.size());
                                    if (allJoins.size() >= 2) {
                                        secondJoinLatch.countDown();
                                    }
                                }
                            }
                        }
                    }
                });
        Thread.sleep(2000);

        // First connect
        String ssoB = makeSecureSSOToken("reconB");
        String[] wsParamsB = getWsParams(urlId, ssoB);
        LiveEventSubscriber subscriberB1 = LiveEventSubscriber.createTesting();
        CommentWidgetConfig configB = new CommentWidgetConfig(testTenantId, urlId);
        SubscribeToChangesResult resultB1 = subscriberB1.subscribeToChanges(
                configB, wsParamsB[0], urlId, wsParamsB[1], wsParamsB[2],
                null, event -> {});
        Log.d(TAG, "UserB first connection established");
        Thread.sleep(3000); // Let the join propagate

        // Disconnect
        resultB1.close();
        Log.d(TAG, "UserB disconnected");
        Thread.sleep(3000); // Let the leave propagate

        // Reconnect
        String[] wsParamsB2 = getWsParams(urlId, ssoB);
        LiveEventSubscriber subscriberB2 = LiveEventSubscriber.createTesting();
        SubscribeToChangesResult resultB2 = subscriberB2.subscribeToChanges(
                configB, wsParamsB2[0], urlId, wsParamsB2[1], wsParamsB2[2],
                null, event -> {});
        Log.d(TAG, "UserB reconnected, waiting for UserA to see second join...");

        boolean sawSecondJoin = secondJoinLatch.await(10, TimeUnit.SECONDS);
        Log.d(TAG, "Result: sawSecondJoin=" + sawSecondJoin + " totalJoins=" + allJoins.size());

        resultB2.close();
        resultA.close();

        assertTrue("UserA should see UserB join at least twice (initial + reconnect)", sawSecondJoin);
    }

    private String[] getWsParams(String urlId, String sso) throws Exception {
        String encodedSso = java.net.URLEncoder.encode(sso, "UTF-8");
        OkHttpClient client = new OkHttpClient.Builder().build();
        Request req = new Request.Builder()
                .url("https://fastcomments.com/comments/" + testTenantId
                        + "/?urlId=" + urlId + "&sso=" + encodedSso
                        + "&direction=NF&count=30&asTree=true")
                .build();
        try (Response resp = client.newCall(req).execute()) {
            String body = resp.body().string();
            JSONObject json = new JSONObject(body);
            client.dispatcher().executorService().shutdown();
            return new String[]{
                    json.optString("tenantIdWS", ""),
                    json.optString("urlIdWS", ""),
                    json.optString("userIdWS", "")
            };
        }
    }
}
