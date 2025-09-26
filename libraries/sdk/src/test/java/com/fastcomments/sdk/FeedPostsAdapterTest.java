package com.fastcomments.sdk;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.widget.ImageButton;

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.FeedPost;
import com.fastcomments.model.FeedPostLink;
import com.fastcomments.model.FeedPostMediaItem;
import com.fastcomments.model.FeedPostMediaItemAsset;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for the FeedPostsAdapter, focusing on the multi-image navigation
 */
@RunWith(RobolectricTestRunner.class)
public class FeedPostsAdapterTest {

    @Mock
    private Context context;

    @Mock
    private FastCommentsFeedSDK sdk;

    @Mock
    private FeedPostsAdapter.OnFeedPostInteractionListener listener;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        
        // Set up SDK config
        CommentWidgetConfig config = new CommentWidgetConfig();
        when(sdk.getConfig()).thenReturn(config);
    }

    @Test
    public void testDeterminePostType() {
        List<FeedPost> posts = new ArrayList<>();
        FeedPostsAdapter adapter = new FeedPostsAdapter(context, posts, sdk, listener);
        
        // Create test posts
        FeedPost textPost = createTextOnlyPost();
        FeedPost singleImagePost = createSingleImagePost();
        FeedPost multiImagePost = createMultiImagePost();
        FeedPost taskPost = createTaskPost();
        
        // Test through reflection since the method is private
        FeedPostType textType = FeedPostType.TEXT_ONLY;
        FeedPostType singleImageType = FeedPostType.SINGLE_IMAGE;
        FeedPostType multiImageType = FeedPostType.MULTI_IMAGE;
        FeedPostType taskType = FeedPostType.TASK;
        
        // We can't test private methods directly, so we'll test the getItemViewType
        posts.clear();
        posts.add(textPost);
        assertEquals(textType.ordinal(), adapter.getItemViewType(0));
        
        posts.clear();
        posts.add(singleImagePost);
        assertEquals(singleImageType.ordinal(), adapter.getItemViewType(0));
        
        posts.clear();
        posts.add(multiImagePost);
        assertEquals(multiImageType.ordinal(), adapter.getItemViewType(0));
        
        posts.clear();
        posts.add(taskPost);
        assertEquals(taskType.ordinal(), adapter.getItemViewType(0));
    }

    // Helper methods to create test posts
    private FeedPost createTextOnlyPost() {
        FeedPost post = new FeedPost();
        post.setContentHTML("<p>This is a text-only post</p>");
        return post;
    }

    private FeedPost createSingleImagePost() {
        FeedPost post = new FeedPost();
        post.setContentHTML("<p>This is a post with a single image</p>");
        
        List<FeedPostMediaItem> media = new ArrayList<>();
        FeedPostMediaItem item = new FeedPostMediaItem();
        FeedPostMediaItemAsset sizes = new FeedPostMediaItemAsset();
        sizes.setSrc("https://example.com/image.jpg");
        item.setSizes(Arrays.asList(sizes));
        media.add(item);
        
        post.setMedia(media);
        return post;
    }

    private FeedPost createMultiImagePost() {
        FeedPost post = new FeedPost();
        post.setContentHTML("<p>This is a post with multiple images</p>");
        
        List<FeedPostMediaItem> media = new ArrayList<>();
        
        // Add first image
        FeedPostMediaItem item1 = new FeedPostMediaItem();
        FeedPostMediaItemAsset sizes1 = new FeedPostMediaItemAsset();
        sizes1.setSrc("https://example.com/image1.jpg");
        item1.setSizes(Arrays.asList(sizes1));
        media.add(item1);
        
        // Add second image
        FeedPostMediaItem item2 = new FeedPostMediaItem();
        FeedPostMediaItemAsset sizes2 = new FeedPostMediaItemAsset();
        sizes2.setSrc("https://example.com/image2.jpg");
        item2.setSizes(Arrays.asList(sizes2));
        media.add(item2);
        
        post.setMedia(media);
        return post;
    }

    private FeedPost createTaskPost() {
        FeedPost post = new FeedPost();
        post.setContentHTML("<p>This is a task post with action links</p>");

        // Add links
        FeedPostLink link = new FeedPostLink();
        link.setTitle("Take Action");
        link.setUrl("https://example.com/action");
        post.setLinks(Arrays.asList(link));

        return post;
    }
}