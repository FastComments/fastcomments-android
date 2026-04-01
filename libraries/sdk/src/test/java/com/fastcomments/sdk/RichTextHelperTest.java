package com.fastcomments.sdk;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.graphics.drawable.Drawable;
import android.text.style.URLSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class RichTextHelperTest {

    // ========== toHtml tests ==========

    @Test
    public void toHtml_emptyEditable_returnsEmpty() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("");
        assertEquals("", RichTextHelper.toHtml(ssb));
    }

    @Test
    public void toHtml_plainText_returnsEscapedText() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("Hello world");
        assertEquals("Hello world", RichTextHelper.toHtml(ssb));
    }

    @Test
    public void toHtml_specialChars_escaped() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("a < b & c > d");
        assertEquals("a &lt; b &amp; c &gt; d", RichTextHelper.toHtml(ssb));
    }

    @Test
    public void toHtml_newlines_convertedToBr() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("line1\nline2");
        assertEquals("line1<br>line2", RichTextHelper.toHtml(ssb));
    }

    @Test
    public void toHtml_bold_emitsBTags() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("Hello world");
        ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, 5, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        assertEquals("<b>Hello</b> world", RichTextHelper.toHtml(ssb));
    }

    @Test
    public void toHtml_italic_emitsITags() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("Hello world");
        ssb.setSpan(new StyleSpan(Typeface.ITALIC), 6, 11, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        assertEquals("Hello <i>world</i>", RichTextHelper.toHtml(ssb));
    }

    @Test
    public void toHtml_code_emitsCodeTags() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("some code here");
        ssb.setSpan(new TypefaceSpan("monospace"), 5, 9, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        ssb.setSpan(new BackgroundColorSpan(0x20808080), 5, 9, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        assertEquals("some <code>code</code> here", RichTextHelper.toHtml(ssb));
    }

    @Test
    public void toHtml_codeBlock_emitsPreCodeTags() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("int x = 1;");
        ssb.setSpan(new TypefaceSpan("monospace"), 0, 10, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        ssb.setSpan(new BackgroundColorSpan(0x20808080), 0, 10, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        ssb.setSpan(new RichTextHelper.CodeBlockSpan(), 0, 10, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        assertEquals("<pre><code>int x = 1;</code></pre>", RichTextHelper.toHtml(ssb));
    }

    @Test
    public void toHtml_link_emitsAnchorTag() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("click here");
        ssb.setSpan(new URLSpan("https://example.com"), 0, 10, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        assertEquals("<a href=\"https://example.com\">click here</a>", RichTextHelper.toHtml(ssb));
    }

    @Test
    public void toHtml_linkWithQuotesInUrl_attributeEscaped() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("link");
        ssb.setSpan(new URLSpan("https://example.com?a=\"b\""), 0, 4, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        String html = RichTextHelper.toHtml(ssb);
        assertTrue(html.contains("&quot;"));
        assertFalse(html.contains("\"b\""));
    }

    @Test
    public void toHtml_nestedBoldItalic_properNesting() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("Hello");
        // Bold covers all, italic covers all — both start and end at same positions
        ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, 5, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        ssb.setSpan(new StyleSpan(Typeface.ITALIC), 0, 5, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        String html = RichTextHelper.toHtml(ssb);
        // Should be validly nested, e.g. <b><i>Hello</i></b> or <i><b>Hello</b></i>
        assertTrue(html.contains("Hello"));
        assertTrue(html.contains("<b>"));
        assertTrue(html.contains("<i>"));
        // Ensure valid nesting by checking the close tags are in reverse order of opens
        int bOpen = html.indexOf("<b>");
        int iOpen = html.indexOf("<i>");
        int bClose = html.indexOf("</b>");
        int iClose = html.indexOf("</i>");
        if (bOpen < iOpen) {
            assertTrue("Close tags should be LIFO", iClose < bClose);
        } else {
            assertTrue("Close tags should be LIFO", bClose < iClose);
        }
    }

    @Test
    public void toHtml_zeroWidthSpan_skipped() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("Hello");
        ssb.setSpan(new StyleSpan(Typeface.BOLD), 3, 3, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        assertEquals("Hello", RichTextHelper.toHtml(ssb));
    }

    @Test
    public void toHtml_overlappingBoldAndItalic() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("Hello World");
        ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, 11, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        ssb.setSpan(new StyleSpan(Typeface.ITALIC), 3, 8, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        String html = RichTextHelper.toHtml(ssb);
        // Bold wraps the whole thing, italic nests inside
        assertTrue(html.contains("<b>"));
        assertTrue(html.contains("<i>"));
        assertTrue(html.contains("</i>"));
        assertTrue(html.contains("</b>"));
    }

    // ========== fromHtml tests ==========

    @Test
    public void fromHtml_plainText() {
        Spanned result = RichTextHelper.fromHtml("Hello world");
        assertEquals("Hello world", result.toString());
    }

    @Test
    public void fromHtml_bold() {
        Spanned result = RichTextHelper.fromHtml("<b>Hello</b> world");
        StyleSpan[] spans = result.getSpans(0, result.length(), StyleSpan.class);
        boolean hasBold = false;
        for (StyleSpan s : spans) {
            if (s.getStyle() == Typeface.BOLD) {
                assertEquals(0, result.getSpanStart(s));
                assertEquals(5, result.getSpanEnd(s));
                hasBold = true;
            }
        }
        assertTrue("Should have bold span", hasBold);
    }

    @Test
    public void fromHtml_italic() {
        Spanned result = RichTextHelper.fromHtml("Hello <i>world</i>");
        StyleSpan[] spans = result.getSpans(0, result.length(), StyleSpan.class);
        boolean hasItalic = false;
        for (StyleSpan s : spans) {
            if (s.getStyle() == Typeface.ITALIC) {
                hasItalic = true;
            }
        }
        assertTrue("Should have italic span", hasItalic);
    }

    @Test
    public void fromHtml_code_appliesMonospaceAndBackground() {
        Spanned result = RichTextHelper.fromHtml("some <code>code</code> text");
        TypefaceSpan[] typefaces = result.getSpans(0, result.length(), TypefaceSpan.class);
        boolean hasMonospace = false;
        for (TypefaceSpan t : typefaces) {
            if ("monospace".equals(t.getFamily())) hasMonospace = true;
        }
        assertTrue("Should have monospace span for <code>", hasMonospace);

        BackgroundColorSpan[] bgSpans = result.getSpans(0, result.length(), BackgroundColorSpan.class);
        assertTrue("Should have background span for <code>", bgSpans.length > 0);
    }

    @Test
    public void fromHtml_preCode_appliesCodeBlockSpan() {
        Spanned result = RichTextHelper.fromHtml("<pre><code>int x = 1;</code></pre>");
        RichTextHelper.CodeBlockSpan[] blocks = result.getSpans(0, result.length(),
                RichTextHelper.CodeBlockSpan.class);
        assertTrue("Should have CodeBlockSpan for <pre><code>", blocks.length > 0);
    }

    @Test
    public void fromHtml_link() {
        Spanned result = RichTextHelper.fromHtml("<a href=\"https://example.com\">click</a>");
        URLSpan[] spans = result.getSpans(0, result.length(), URLSpan.class);
        assertTrue("Should have URLSpan", spans.length > 0);
        assertEquals("https://example.com", spans[0].getURL());
    }

    @Test
    public void fromHtml_stripTrailingNewlines() {
        Spanned result = RichTextHelper.fromHtml("<p>Hello</p>");
        assertFalse("Should not end with newline", result.toString().endsWith("\n"));
    }

    // ========== Toggle tests ==========

    @Test
    public void toggleBold_appliesAndRemoves() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("Hello");

        // Apply bold
        boolean result = RichTextHelper.toggleBold(ssb, 0, 5);
        assertTrue("Should return true when applying", result);
        StyleSpan[] spans = ssb.getSpans(0, 5, StyleSpan.class);
        boolean hasBold = false;
        for (StyleSpan s : spans) if (s.getStyle() == Typeface.BOLD) hasBold = true;
        assertTrue("Should have bold span after toggle on", hasBold);

        // Remove bold
        result = RichTextHelper.toggleBold(ssb, 0, 5);
        assertFalse("Should return false when removing", result);
        spans = ssb.getSpans(0, 5, StyleSpan.class);
        hasBold = false;
        for (StyleSpan s : spans) if (s.getStyle() == Typeface.BOLD) hasBold = true;
        assertFalse("Should not have bold span after toggle off", hasBold);
    }

    @Test
    public void toggleBold_mergesOverlappingSpans() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("Hello World");
        // Bold "Hello"
        RichTextHelper.toggleBold(ssb, 0, 5);
        // Bold "lo Wor" — overlaps with existing bold
        RichTextHelper.toggleBold(ssb, 3, 9);
        // Should have one merged span covering [0, 9]
        StyleSpan[] spans = ssb.getSpans(0, 9, StyleSpan.class);
        int boldCount = 0;
        for (StyleSpan s : spans) {
            if (s.getStyle() == Typeface.BOLD) {
                boldCount++;
                assertEquals("Merged span should start at 0", 0, ssb.getSpanStart(s));
                assertEquals("Merged span should end at 9", 9, ssb.getSpanEnd(s));
            }
        }
        assertEquals("Should have exactly one bold span after merge", 1, boldCount);
    }

    @Test
    public void toggleBold_noSelection_returnsToggleState() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("Hello");
        // No selection (cursor at position 3)
        boolean result = RichTextHelper.toggleBold(ssb, 3, 3);
        // Should indicate what to do (true = turn on, false = turn off)
        // At position 3 with no bold, should return true
        assertTrue(result);
    }

    // ========== Active-state tests ==========

    @Test
    public void isBoldActive_trueInsideBoldSpan() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("Hello");
        ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, 5, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        assertTrue(RichTextHelper.isBoldActive(ssb, 3));
    }

    @Test
    public void isBoldActive_falseOutsideBoldSpan() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("Hello World");
        ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, 5, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        assertFalse(RichTextHelper.isBoldActive(ssb, 8));
    }

    @Test
    public void isBoldActive_falseAtPositionZero() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("Hello");
        ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, 5, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        assertFalse("Position 0 has nothing before it", RichTextHelper.isBoldActive(ssb, 0));
    }

    // ========== parseHtmlTags tests ==========

    @Test
    public void parseHtmlTags_recognizedTags() {
        assertEquals("bold", RichTextHelper.parseHtmlTags("<b>", "</b>"));
        assertEquals("bold", RichTextHelper.parseHtmlTags("<strong>", "</strong>"));
        assertEquals("italic", RichTextHelper.parseHtmlTags("<i>", "</i>"));
        assertEquals("italic", RichTextHelper.parseHtmlTags("<em>", "</em>"));
        assertEquals("code", RichTextHelper.parseHtmlTags("<code>", "</code>"));
        assertEquals("codeblock", RichTextHelper.parseHtmlTags("<pre><code>", "</code></pre>"));
    }

    @Test
    public void parseHtmlTags_unrecognizedTag_returnsNull() {
        assertNull(RichTextHelper.parseHtmlTags("<custom>", "</custom>"));
        assertNull(RichTextHelper.parseHtmlTags("<div>", "</div>"));
    }

    @Test
    public void extractHref_validAnchor() {
        assertEquals("https://example.com",
                RichTextHelper.extractHref("<a href=\"https://example.com\">"));
    }

    @Test
    public void extractHref_notAnchor_returnsNull() {
        assertNull(RichTextHelper.extractHref("<b>"));
    }

    // ========== Image serialization test ==========

    @Test
    public void toHtml_richImageSpan_emitsImgTag() {
        // \uFFFC is the object replacement character used by ImageSpan
        SpannableStringBuilder ssb = new SpannableStringBuilder("before\uFFFCafter");
        Drawable dummyDrawable = new android.graphics.drawable.ColorDrawable(0xFF000000);
        dummyDrawable.setBounds(0, 0, 100, 100);
        ssb.setSpan(new RichTextHelper.RichImageSpan(dummyDrawable,
                        "https://example.com/img.png", "alt text", null),
                6, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        String html = RichTextHelper.toHtml(ssb);
        assertTrue("Should contain img tag", html.contains("<img"));
        assertTrue("Should contain src", html.contains("src=\"https://example.com/img.png\""));
        assertTrue("Should contain alt", html.contains("alt=\"alt text\""));
        assertFalse("Should not contain \\uFFFC", html.contains("\uFFFC"));
        assertTrue("Should preserve surrounding text", html.startsWith("before"));
        assertTrue("Should preserve surrounding text", html.endsWith("after"));
    }

    @Test
    public void toHtml_richImageSpan_noLeakedReplacementChar() {
        SpannableStringBuilder ssb = new SpannableStringBuilder("\uFFFC");
        Drawable dummyDrawable = new android.graphics.drawable.ColorDrawable(0xFF000000);
        dummyDrawable.setBounds(0, 0, 100, 100);
        ssb.setSpan(new RichTextHelper.RichImageSpan(dummyDrawable,
                        "https://example.com/test.gif", null, null),
                0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        String html = RichTextHelper.toHtml(ssb);
        assertFalse("Should not contain replacement char", html.contains("\uFFFC"));
        assertTrue("Should be just the img tag", html.contains("<img"));
    }

    // ========== Round-trip tests ==========

    @Test
    public void roundTrip_boldText() {
        SpannableStringBuilder original = new SpannableStringBuilder("Hello world");
        original.setSpan(new StyleSpan(Typeface.BOLD), 0, 5, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);

        String html = RichTextHelper.toHtml(original);
        Spanned parsed = RichTextHelper.fromHtml(html);

        assertEquals("Hello world", parsed.toString());
        StyleSpan[] spans = parsed.getSpans(0, parsed.length(), StyleSpan.class);
        boolean hasBold = false;
        for (StyleSpan s : spans) {
            if (s.getStyle() == Typeface.BOLD) {
                assertEquals(0, parsed.getSpanStart(s));
                assertEquals(5, parsed.getSpanEnd(s));
                hasBold = true;
            }
        }
        assertTrue("Round-trip should preserve bold", hasBold);
    }

    @Test
    public void roundTrip_linkText() {
        SpannableStringBuilder original = new SpannableStringBuilder("click here");
        original.setSpan(new URLSpan("https://example.com"), 0, 10, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);

        String html = RichTextHelper.toHtml(original);
        Spanned parsed = RichTextHelper.fromHtml(html);

        URLSpan[] spans = parsed.getSpans(0, parsed.length(), URLSpan.class);
        assertTrue("Round-trip should preserve link", spans.length > 0);
        assertEquals("https://example.com", spans[0].getURL());
    }
}
