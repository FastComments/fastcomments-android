package com.fastcomments.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.text.style.URLSpan;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit tests for {@link HtmlLinkHandler#findWrappedImageUrl}, which decides whether a tapped
 * link should open a full-screen image instead of the browser.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class HtmlLinkHandlerTest {

    private static Drawable stubDrawable() {
        Drawable d = new ColorDrawable(Color.RED);
        d.setBounds(0, 0, 10, 10);
        return d;
    }

    @Test
    public void linkWrappingImagePrefersAnchorHref() {
        // FastComments wraps uploaded images as <a href="full"><img src="thumb"></a>.
        SpannableStringBuilder text = new SpannableStringBuilder("￼");
        ImageSpan image = new ImageSpan(stubDrawable(), "https://example.com/thumb.jpg");
        URLSpan link = new URLSpan("https://example.com/full.jpg");
        text.setSpan(image, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(link, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        assertEquals("https://example.com/full.jpg", HtmlLinkHandler.findWrappedImageUrl(text, link));
    }

    @Test
    public void linkWrappingImageFallsBackToImageSource() {
        // Anchor with no usable href should fall back to the inline image source.
        SpannableStringBuilder text = new SpannableStringBuilder("￼");
        ImageSpan image = new ImageSpan(stubDrawable(), "https://example.com/thumb.jpg");
        URLSpan link = new URLSpan("");
        text.setSpan(image, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(link, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        assertEquals("https://example.com/thumb.jpg", HtmlLinkHandler.findWrappedImageUrl(text, link));
    }

    @Test
    public void plainTextLinkReturnsNull() {
        // A normal text link must keep opening in the browser, so no image URL is returned.
        SpannableStringBuilder text = new SpannableStringBuilder("click here");
        URLSpan link = new URLSpan("https://example.com");
        text.setSpan(link, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        assertNull(HtmlLinkHandler.findWrappedImageUrl(text, link));
    }
}
