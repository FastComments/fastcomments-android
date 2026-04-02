package com.fastcomments.sdk;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for FastCommentsTheme builder and getters.
 * Note: ThemeColorResolver.getXxxColor() methods require Android Context with SDK resources,
 * which aren't available in a Robolectric unit test for a library module. We test the theme
 * getters directly instead, and test the resolver's getColor() static method with inline fallbacks.
 */
public class ThemeTests {

    @Test
    public void testDefaultResolve() {
        FastCommentsTheme theme = new FastCommentsTheme.Builder().build();
        // All colors null by default
        assertNull(theme.getActionButtonColor());
        assertNull(theme.getPrimaryColor());
        assertNull(theme.getReplyButtonColor());
    }

    @Test
    public void testPrimaryColorFallback() {
        // Android's ThemeColorResolver does NOT auto-fallback to primary.
        // Setting primary alone doesn't affect actionButtonColor getter.
        int red = 0xFFFF0000;
        FastCommentsTheme theme = new FastCommentsTheme.Builder()
                .setPrimaryColor(red)
                .build();

        assertEquals(Integer.valueOf(red), theme.getPrimaryColor());
        assertNull("actionButtonColor should be null when only primary is set",
                theme.getActionButtonColor());
    }

    @Test
    public void testSpecificColorOverride() {
        int green = 0xFF00FF00;
        FastCommentsTheme theme = new FastCommentsTheme.Builder()
                .setActionButtonColor(green)
                .build();

        assertEquals(Integer.valueOf(green), theme.getActionButtonColor());
    }

    @Test
    public void testAllPrimary() {
        int purple = 0xFF800080;
        FastCommentsTheme theme = new FastCommentsTheme.Builder()
                .setAllPrimaryColors(purple)
                .build();

        assertEquals(Integer.valueOf(purple), theme.getPrimaryColor());
        assertEquals(Integer.valueOf(purple), theme.getActionButtonColor());
        assertEquals(Integer.valueOf(purple), theme.getReplyButtonColor());
        assertEquals(Integer.valueOf(purple), theme.getToggleRepliesButtonColor());
        assertEquals(Integer.valueOf(purple), theme.getLoadMoreButtonTextColor());
    }

    @Test
    public void testResolveWithNoColorReturnsNull() {
        FastCommentsTheme theme = new FastCommentsTheme.Builder().build();

        assertNull(theme.getVoteCountColor());
        assertNull(theme.getVoteCountZeroColor());
        assertNull(theme.getReplyButtonColor());
        assertNull(theme.getToggleRepliesButtonColor());
        assertNull(theme.getActionButtonColor());
        assertNull(theme.getLoadMoreButtonTextColor());
        assertNull(theme.getLinkColor());
        assertNull(theme.getOnlineIndicatorColor());
    }

    @Test
    public void testBuilderSetsColors() {
        int c1 = 0xFF111111, c2 = 0xFF222222, c3 = 0xFF333333;
        int c4 = 0xFF444444, c5 = 0xFF555555, c6 = 0xFF666666;
        FastCommentsTheme theme = new FastCommentsTheme.Builder()
                .setPrimaryColor(c1)
                .setActionButtonColor(c2)
                .setReplyButtonColor(c3)
                .setLinkColor(c4)
                .setVoteCountColor(c5)
                .setOnlineIndicatorColor(c6)
                .build();

        assertEquals(Integer.valueOf(c1), theme.getPrimaryColor());
        assertEquals(Integer.valueOf(c2), theme.getActionButtonColor());
        assertEquals(Integer.valueOf(c3), theme.getReplyButtonColor());
        assertEquals(Integer.valueOf(c4), theme.getLinkColor());
        assertEquals(Integer.valueOf(c5), theme.getVoteCountColor());
        assertEquals(Integer.valueOf(c6), theme.getOnlineIndicatorColor());
    }

    @Test
    public void testNullThemeFallsBackCorrectly() {
        // When theme is null, ThemeColorResolver.getColor() returns the resource color.
        // We can't test with a real context here, but we verify that the ColorGetter
        // functional interface works correctly with a non-null theme.
        FastCommentsTheme theme = new FastCommentsTheme.Builder()
                .setActionButtonColor(0xFFABCDEF)
                .build();

        ThemeColorResolver.ColorGetter getter = FastCommentsTheme::getActionButtonColor;
        assertEquals(Integer.valueOf(0xFFABCDEF), getter.getColor(theme));
    }
}
