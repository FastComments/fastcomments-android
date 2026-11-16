package com.fastcomments.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.PollCreationMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PollToolbarTest {

    private Activity activity;
    private FastCommentsSDK sdk;

    @Before
    public void setup() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_FastComments);
        CommentWidgetConfig config = new CommentWidgetConfig("tenant", "url");
        config.pollCreation = PollCreationMode.NUMBER_2;
        sdk = new FastCommentsSDK(config, true);
    }

    private static boolean toolbarShown(BottomCommentInputView input) {
        return input.findViewById(R.id.toolbarContainer).getVisibility() == View.VISIBLE;
    }

    @Test
    public void pollsShowTheToolbarWhenTheHostLeftItUnset() {
        BottomCommentInputView input = new BottomCommentInputView(activity);
        input.setSDK(sdk);
        assertNotNull(input.findViewById(R.id.fastcomments_poll_button));
        assertTrue(toolbarShown(input));
    }

    @Test
    public void sdkToolbarOffHidesThePollButton() {
        sdk.setCommentToolbarEnabled(false);
        BottomCommentInputView input = new BottomCommentInputView(activity);
        input.setSDK(sdk);
        assertNull(input.findViewById(R.id.fastcomments_poll_button));
        assertFalse(toolbarShown(input));
    }

    @Test
    public void inputToolbarOffHidesThePollButton() {
        BottomCommentInputView input = new BottomCommentInputView(activity);
        input.setSDK(sdk);
        input.setToolbarVisible(false);
        assertNull(input.findViewById(R.id.fastcomments_poll_button));
        assertFalse(toolbarShown(input));
    }

    @Test
    public void toolbarVisibilityReportsTheRowShownForPolls() {
        BottomCommentInputView input = new BottomCommentInputView(activity);
        input.setSDK(sdk);
        assertTrue(input.isToolbarVisible());

        input.setPollCreationEnabled(false);
        assertFalse(input.isToolbarVisible());
    }

    @Test
    public void pollCreationCanBeTurnedOffOnTheInput() {
        BottomCommentInputView input = new BottomCommentInputView(activity);
        input.setSDK(sdk);
        input.setPollCreationEnabled(false);
        assertNull(input.findViewById(R.id.fastcomments_poll_button));
        assertFalse(toolbarShown(input));
    }

    @Test
    public void liveChatLeavesPollsOutOfItsInput() {
        LiveChatView.setupLiveChatConfig(sdk.getConfig());
        assertNull(sdk.getConfig().useSingleLineCommentInput);
        LiveChatView chat = new LiveChatView(activity, sdk);
        assertNull(chat.findViewById(R.id.fastcomments_poll_button));
        assertTrue(sdk.canCreatePoll());
    }

    @Test
    public void pollButtonKeepsPressedFeedback() {
        BottomCommentInputView input = new BottomCommentInputView(activity);
        input.setSDK(sdk);
        View button = input.findViewById(R.id.fastcomments_poll_button);
        Drawable background = button.getBackground();
        assertTrue(background instanceof RippleDrawable);

        button.performClick();
        assertTrue(button.isActivated());
        assertTrue(((RippleDrawable) background).getDrawable(0).getCurrent() instanceof GradientDrawable);
    }

    @Test
    public void accentComesFromTheRuntimeTheme() {
        int accent = 0xFF0066CC;
        sdk.setTheme(new FastCommentsTheme.Builder().setPollAccentColor(accent).build());
        BottomCommentInputView input = new BottomCommentInputView(activity);
        input.setSDK(sdk);
        input.findViewById(R.id.fastcomments_poll_button).performClick();

        View pill = ((android.view.ViewGroup) input.findViewById(R.id.pollBuilderDurations)).getChildAt(0);
        assertTrue(pill.isSelected());
        assertTrue(pill.getBackground() instanceof android.graphics.drawable.InsetDrawable);
        android.graphics.drawable.Drawable current = ((android.graphics.drawable.InsetDrawable) pill.getBackground())
                .getDrawable()
                .getCurrent();
        assertTrue(current instanceof android.graphics.drawable.GradientDrawable);
        assertEquals(
                accent,
                ((android.graphics.drawable.GradientDrawable) current)
                        .getColor()
                        .getDefaultColor());
    }
}
