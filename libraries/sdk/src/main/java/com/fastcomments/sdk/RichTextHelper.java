package com.fastcomments.sdk;

import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.util.DisplayMetrics;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import org.xml.sax.XMLReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for WYSIWYG rich text editing using Android's Spannable system.
 *
 * <p>All newly applied spans use {@code SPAN_EXCLUSIVE_INCLUSIVE} so that text typed at
 * the end of a formatted region inherits the formatting (matches user intent of
 * "I'm still typing bold").</p>
 */
public final class RichTextHelper {

    private static final int SPAN_FLAGS = Spanned.SPAN_EXCLUSIVE_INCLUSIVE;
    private static final int CODE_BG_COLOR = 0x20808080;

    private RichTextHelper() {}

    // ========== Marker spans for code blocks ==========

    /**
     * Marker span to distinguish {@code <pre><code>} blocks from inline {@code <code>}.
     * Carries no visual effect itself; used by {@link #toHtml} to choose the correct tags.
     */
    public static class CodeBlockSpan {
        // Marker only — no fields needed
    }

    /**
     * ImageSpan subclass that preserves src/alt/style attributes for HTML round-tripping.
     */
    public static class RichImageSpan extends ImageSpan {
        private final String src;
        private final String alt;
        private final String style;

        public RichImageSpan(@NonNull Drawable drawable, @NonNull String src,
                             @Nullable String alt, @Nullable String style) {
            super(drawable);
            this.src = src;
            this.alt = alt;
            this.style = style;
        }

        public String getSrc() { return src; }
        public String getAlt() { return alt; }
        public String getStyle() { return style; }
    }

    // ========== EditableImageGetter ==========

    /**
     * Html.ImageGetter safe for use with EditText. Refreshes via invalidate/requestLayout
     * instead of setText (which would destroy undo stack and fire watchers).
     */
    public static class EditableImageGetter implements Html.ImageGetter {
        private final EditText editText;
        private final List<CustomTarget<?>> activeTargets = new ArrayList<>();
        private final int maxWidth;
        private final int maxHeightPx;

        public EditableImageGetter(@NonNull EditText editText) {
            this.editText = editText;
            int viewWidth = editText.getWidth() - editText.getPaddingLeft() - editText.getPaddingRight();
            if (viewWidth <= 0) {
                // Not yet laid out — use display width minus padding as fallback
                DisplayMetrics dm = editText.getResources().getDisplayMetrics();
                viewWidth = dm.widthPixels - (int) (64 * dm.density);
            }
            this.maxWidth = Math.max(viewWidth, 100);
            this.maxHeightPx = (int) (300 * editText.getResources().getDisplayMetrics().density);
        }

        @Override
        public Drawable getDrawable(String source) {
            final URLDrawable urlDrawable = new URLDrawable();
            // Placeholder bounds until image loads
            int placeholderH = (int) (48 * editText.getResources().getDisplayMetrics().density);
            urlDrawable.setBounds(0, 0, maxWidth, placeholderH);

            boolean isGif = source != null && (source.endsWith(".gif") || source.contains(".gif?") || source.contains("/giphy.gif"));

            if (isGif) {
                loadGif(source, urlDrawable);
            } else {
                loadBitmap(source, urlDrawable);
            }

            return urlDrawable;
        }

        private void loadGif(String source, URLDrawable urlDrawable) {
            CustomTarget<com.bumptech.glide.load.resource.gif.GifDrawable> target =
                    new CustomTarget<com.bumptech.glide.load.resource.gif.GifDrawable>() {
                @Override
                public void onResourceReady(@NonNull com.bumptech.glide.load.resource.gif.GifDrawable resource,
                                            @Nullable Transition<? super com.bumptech.glide.load.resource.gif.GifDrawable> transition) {
                    int w = resource.getIntrinsicWidth();
                    int h = resource.getIntrinsicHeight();
                    if (w > maxWidth) {
                        h = (int) ((long) h * maxWidth / w);
                        w = maxWidth;
                    }
                    if (h > maxHeightPx) {
                        w = (int) ((long) w * maxHeightPx / h);
                        h = maxHeightPx;
                    }

                    resource.setBounds(0, 0, w, h);
                    resource.setLoopCount(com.bumptech.glide.load.resource.gif.GifDrawable.LOOP_FOREVER);

                    resource.setCallback(new Drawable.Callback() {
                        @Override
                        public void invalidateDrawable(@NonNull Drawable who) {
                            editText.invalidate();
                        }
                        @Override
                        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
                            editText.postDelayed(what, when - android.os.SystemClock.uptimeMillis());
                        }
                        @Override
                        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
                            editText.removeCallbacks(what);
                        }
                    });

                    urlDrawable.setDrawable(resource);
                    urlDrawable.setBounds(0, 0, w, h);
                    resource.start();
                    editText.invalidate();
                    editText.requestLayout();
                }

                @Override
                public void onLoadCleared(@Nullable Drawable placeholder) {
                }
            };
            activeTargets.add(target);

            Glide.with(editText)
                    .asGif()
                    .load(source)
                    .into(target);
        }

        private void loadBitmap(String source, URLDrawable urlDrawable) {
            CustomTarget<Bitmap> target = new CustomTarget<Bitmap>() {
                @Override
                public void onResourceReady(@NonNull Bitmap resource,
                                            @Nullable Transition<? super Bitmap> transition) {
                    BitmapDrawable bd = new BitmapDrawable(editText.getResources(), resource);
                    int w = bd.getIntrinsicWidth();
                    int h = bd.getIntrinsicHeight();

                    if (w > maxWidth) {
                        h = (int) ((long) h * maxWidth / w);
                        w = maxWidth;
                    }
                    if (h > maxHeightPx) {
                        w = (int) ((long) w * maxHeightPx / h);
                        h = maxHeightPx;
                    }

                    bd.setBounds(0, 0, w, h);
                    urlDrawable.setBounds(0, 0, w, h);
                    urlDrawable.setDrawable(bd);

                    editText.invalidate();
                    editText.requestLayout();
                }

                @Override
                public void onLoadCleared(@Nullable Drawable placeholder) {
                }
            };
            activeTargets.add(target);

            Glide.with(editText)
                    .asBitmap()
                    .load(source)
                    .into(target);
        }

        /**
         * Clear all pending Glide targets. Call in clearText(), onDetachedFromWindow(), dismiss().
         */
        public void clearTargets() {
            for (CustomTarget<?> t : activeTargets) {
                try {
                    Glide.with(editText).clear(t);
                } catch (Exception ignored) {
                    // View may already be detached
                }
            }
            activeTargets.clear();
        }
    }

    // ========== Span toggle methods ==========

    /**
     * Toggle bold on the given range. Returns true if bold was applied (or was already on),
     * false if it was removed.
     */
    public static boolean toggleBold(Editable editable, int selStart, int selEnd) {
        return toggleStyleSpan(editable, selStart, selEnd, Typeface.BOLD);
    }

    public static boolean toggleItalic(Editable editable, int selStart, int selEnd) {
        return toggleStyleSpan(editable, selStart, selEnd, Typeface.ITALIC);
    }

    public static boolean toggleCode(Editable editable, int selStart, int selEnd) {
        if (selStart == selEnd) {
            return !isCodeActive(editable, selStart);
        }
        if (isRangeCovered(editable, selStart, selEnd, TypefaceSpan.class,
                s -> "monospace".equals(s.getFamily()))) {
            removeSpansInRange(editable, selStart, selEnd, TypefaceSpan.class,
                    s -> "monospace".equals(s.getFamily()));
            removeSpansInRange(editable, selStart, selEnd, BackgroundColorSpan.class, null);
            removeSpansInRange(editable, selStart, selEnd, CodeBlockSpan.class, null);
            return false;
        } else {
            int[] merged = mergeRange(editable, selStart, selEnd, TypefaceSpan.class,
                    s -> "monospace".equals(s.getFamily()));
            removeSpansInRange(editable, merged[0], merged[1], TypefaceSpan.class,
                    s -> "monospace".equals(s.getFamily()));
            removeSpansInRange(editable, merged[0], merged[1], BackgroundColorSpan.class, null);
            removeSpansInRange(editable, merged[0], merged[1], CodeBlockSpan.class, null);
            editable.setSpan(new TypefaceSpan("monospace"), merged[0], merged[1], SPAN_FLAGS);
            editable.setSpan(new BackgroundColorSpan(CODE_BG_COLOR), merged[0], merged[1], SPAN_FLAGS);
            return true;
        }
    }

    public static boolean toggleCodeBlock(Editable editable, int selStart, int selEnd) {
        if (selStart == selEnd) {
            return false; // code blocks require a selection
        }
        // Check if already a code block
        CodeBlockSpan[] blocks = editable.getSpans(selStart, selEnd, CodeBlockSpan.class);
        if (blocks.length > 0 && isRangeCovered(editable, selStart, selEnd, TypefaceSpan.class,
                s -> "monospace".equals(s.getFamily()))) {
            removeSpansInRange(editable, selStart, selEnd, TypefaceSpan.class,
                    s -> "monospace".equals(s.getFamily()));
            removeSpansInRange(editable, selStart, selEnd, BackgroundColorSpan.class, null);
            removeSpansInRange(editable, selStart, selEnd, CodeBlockSpan.class, null);
            return false;
        } else {
            int[] merged = mergeRange(editable, selStart, selEnd, TypefaceSpan.class,
                    s -> "monospace".equals(s.getFamily()));
            removeSpansInRange(editable, merged[0], merged[1], TypefaceSpan.class,
                    s -> "monospace".equals(s.getFamily()));
            removeSpansInRange(editable, merged[0], merged[1], BackgroundColorSpan.class, null);
            removeSpansInRange(editable, merged[0], merged[1], CodeBlockSpan.class, null);
            editable.setSpan(new TypefaceSpan("monospace"), merged[0], merged[1], SPAN_FLAGS);
            editable.setSpan(new BackgroundColorSpan(CODE_BG_COLOR), merged[0], merged[1], SPAN_FLAGS);
            editable.setSpan(new CodeBlockSpan(), merged[0], merged[1], SPAN_FLAGS);
            return true;
        }
    }

    public static void applyLink(Editable editable, int selStart, int selEnd, String url) {
        if (selStart == selEnd || url == null || url.isEmpty()) return;
        removeSpansInRange(editable, selStart, selEnd, URLSpan.class, null);
        editable.setSpan(new URLSpan(url), selStart, selEnd, SPAN_FLAGS);
    }

    public static void removeLink(Editable editable, int selStart, int selEnd) {
        removeSpansInRange(editable, selStart, selEnd, URLSpan.class, null);
    }

    // ========== Active-state query ==========

    public static boolean isBoldActive(Editable editable, int position) {
        if (position <= 0 || editable.length() == 0) return false;
        int checkPos = Math.min(position - 1, editable.length() - 1);
        StyleSpan[] spans = editable.getSpans(checkPos, checkPos + 1, StyleSpan.class);
        for (StyleSpan s : spans) {
            if (s.getStyle() == Typeface.BOLD) return true;
        }
        return false;
    }

    public static boolean isItalicActive(Editable editable, int position) {
        if (position <= 0 || editable.length() == 0) return false;
        int checkPos = Math.min(position - 1, editable.length() - 1);
        StyleSpan[] spans = editable.getSpans(checkPos, checkPos + 1, StyleSpan.class);
        for (StyleSpan s : spans) {
            if (s.getStyle() == Typeface.ITALIC) return true;
        }
        return false;
    }

    public static boolean isCodeActive(Editable editable, int position) {
        if (position <= 0 || editable.length() == 0) return false;
        int checkPos = Math.min(position - 1, editable.length() - 1);
        TypefaceSpan[] spans = editable.getSpans(checkPos, checkPos + 1, TypefaceSpan.class);
        for (TypefaceSpan s : spans) {
            if ("monospace".equals(s.getFamily())) return true;
        }
        return false;
    }

    public static boolean isLinkActive(Editable editable, int position) {
        if (position <= 0 || editable.length() == 0) return false;
        int checkPos = Math.min(position - 1, editable.length() - 1);
        URLSpan[] spans = editable.getSpans(checkPos, checkPos + 1, URLSpan.class);
        return spans.length > 0;
    }

    @Nullable
    public static String getLinkUrl(Editable editable, int position) {
        if (position <= 0 || editable.length() == 0) return null;
        int checkPos = Math.min(position - 1, editable.length() - 1);
        URLSpan[] spans = editable.getSpans(checkPos, checkPos + 1, URLSpan.class);
        return spans.length > 0 ? spans[0].getURL() : null;
    }

    // ========== HTML serialization ==========

    /**
     * Serialize an Editable (with spans) to an HTML string.
     * Uses a custom serializer to avoid Html.toHtml() issues with {@code <p>} wrapping
     * and {@code <tt>} vs {@code <code>}.
     */
    public static String toHtml(Editable editable) {
        if (editable == null || editable.length() == 0) return "";

        String text = editable.toString();

        // Collect span events
        List<SpanEvent> events = new ArrayList<>();

        // StyleSpan (bold/italic)
        for (StyleSpan span : editable.getSpans(0, editable.length(), StyleSpan.class)) {
            int start = editable.getSpanStart(span);
            int end = editable.getSpanEnd(span);
            if (start == end) continue; // skip zero-width
            String tag;
            if (span.getStyle() == Typeface.BOLD) {
                tag = "b";
            } else if (span.getStyle() == Typeface.ITALIC) {
                tag = "i";
            } else {
                continue;
            }
            events.add(new SpanEvent(start, true, tag, null, span));
            events.add(new SpanEvent(end, false, tag, null, span));
        }

        // Code spans (TypefaceSpan monospace) — distinguish inline vs block
        for (TypefaceSpan span : editable.getSpans(0, editable.length(), TypefaceSpan.class)) {
            if (!"monospace".equals(span.getFamily())) continue;
            int start = editable.getSpanStart(span);
            int end = editable.getSpanEnd(span);
            if (start == end) continue;

            // Check for CodeBlockSpan marker at same range
            boolean isBlock = false;
            for (CodeBlockSpan cbs : editable.getSpans(start, end, CodeBlockSpan.class)) {
                int cbsStart = editable.getSpanStart(cbs);
                int cbsEnd = editable.getSpanEnd(cbs);
                if (cbsStart == start && cbsEnd == end) {
                    isBlock = true;
                    break;
                }
            }

            if (isBlock) {
                // Emit as <pre><code>...</code></pre>
                // Use a synthetic compound tag — open order: pre then code, close order: code then pre
                events.add(new SpanEvent(start, true, "pre", null, span));
                events.add(new SpanEvent(start, true, "code", null, span));
                events.add(new SpanEvent(end, false, "code", null, span));
                events.add(new SpanEvent(end, false, "pre", null, span));
            } else {
                events.add(new SpanEvent(start, true, "code", null, span));
                events.add(new SpanEvent(end, false, "code", null, span));
            }
        }

        // URLSpan
        for (URLSpan span : editable.getSpans(0, editable.length(), URLSpan.class)) {
            int start = editable.getSpanStart(span);
            int end = editable.getSpanEnd(span);
            if (start == end) continue;
            String attrs = " href=\"" + escapeAttr(span.getURL()) + "\"";
            events.add(new SpanEvent(start, true, "a", attrs, span));
            events.add(new SpanEvent(end, false, "a", null, span));
        }

        // RichImageSpan — self-closing, emitted at span start
        for (RichImageSpan span : editable.getSpans(0, editable.length(), RichImageSpan.class)) {
            int start = editable.getSpanStart(span);
            StringBuilder attrs = new StringBuilder(" src=\"" + escapeAttr(span.getSrc()) + "\"");
            if (span.getAlt() != null) {
                attrs.append(" alt=\"").append(escapeAttr(span.getAlt())).append("\"");
            }
            if (span.getStyle() != null) {
                attrs.append(" style=\"").append(escapeAttr(span.getStyle())).append("\"");
            }
            events.add(new SpanEvent(start, true, "img", attrs.toString(), span));
        }

        // Sort: by position, then closes before opens at same position,
        // and for closes at the same position, reverse-open order (LIFO / stack discipline)
        Collections.sort(events, new SpanEventComparator(editable));

        // Build HTML
        StringBuilder sb = new StringBuilder();
        int cursor = 0;
        for (SpanEvent ev : events) {
            // Emit text between cursor and this event
            if (ev.position > cursor) {
                appendEscapedText(sb, text, cursor, ev.position);
            }
            cursor = ev.position;

            if ("img".equals(ev.tag)) {
                sb.append("<img").append(ev.attrs != null ? ev.attrs : "").append(" />");
                // Skip past the \uFFFC object replacement character occupied by the ImageSpan
                cursor = Math.max(cursor + 1, editable.getSpanEnd(ev.span));
            } else if (ev.isOpen) {
                sb.append("<").append(ev.tag);
                if (ev.attrs != null) sb.append(ev.attrs);
                sb.append(">");
            } else {
                sb.append("</").append(ev.tag).append(">");
            }
        }
        // Remaining text
        if (cursor < text.length()) {
            appendEscapedText(sb, text, cursor, text.length());
        }

        return sb.toString();
    }

    // ========== HTML deserialization ==========

    /**
     * Parse HTML into a Spanned, using the given EditableImageGetter for images.
     */
    public static Spanned fromHtml(@NonNull String html, @Nullable EditableImageGetter imageGetter) {
        Spanned result = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT,
                imageGetter, new CodePreTagHandler());
        SpannableStringBuilder ssb = new SpannableStringBuilder(result);

        // Replace plain ImageSpan instances (created by Html.fromHtml) with RichImageSpan
        // so that toHtml() can serialize them back to <img> tags.
        for (ImageSpan imgSpan : ssb.getSpans(0, ssb.length(), ImageSpan.class)) {
            if (imgSpan instanceof RichImageSpan) continue; // already rich
            int start = ssb.getSpanStart(imgSpan);
            int end = ssb.getSpanEnd(imgSpan);
            int flags = ssb.getSpanFlags(imgSpan);
            String src = imgSpan.getSource();
            if (src == null) src = "";
            ssb.removeSpan(imgSpan);
            ssb.setSpan(new RichImageSpan(imgSpan.getDrawable(), src, null, null),
                    start, end, flags);
        }

        // Strip trailing newlines added by Html.fromHtml
        while (ssb.length() > 0 && ssb.charAt(ssb.length() - 1) == '\n') {
            ssb.delete(ssb.length() - 1, ssb.length());
        }
        return ssb;
    }

    /**
     * Convenience: create a new EditableImageGetter for the given EditText.
     * Returns a FromHtmlResult so the caller can store the getter for cleanup.
     */
    public static FromHtmlResult fromHtml(@NonNull String html, @NonNull EditText editText) {
        EditableImageGetter getter = new EditableImageGetter(editText);
        Spanned spanned = fromHtml(html, getter);
        return new FromHtmlResult(spanned, getter);
    }

    /**
     * Test-friendly overload with no image support.
     */
    public static Spanned fromHtml(@NonNull String html) {
        return fromHtml(html, (EditableImageGetter) null);
    }

    public static class FromHtmlResult {
        public final Spanned spanned;
        public final EditableImageGetter imageGetter;

        FromHtmlResult(Spanned spanned, EditableImageGetter imageGetter) {
            this.spanned = spanned;
            this.imageGetter = imageGetter;
        }
    }

    // ========== Tag-to-format mapper ==========

    /**
     * Map HTML start/end tags to a format name for backward compatibility with
     * {@code wrapSelection("<b>", "</b>")}.
     *
     * @return format name ("bold", "italic", "code", "codeblock") or null if unrecognized
     */
    @Nullable
    public static String parseHtmlTags(@NonNull String startTag, @NonNull String endTag) {
        String normalized = startTag.toLowerCase().trim();
        if (normalized.equals("<b>") || normalized.equals("<strong>")) return "bold";
        if (normalized.equals("<i>") || normalized.equals("<em>")) return "italic";
        if (normalized.equals("<pre><code>")) return "codeblock";
        if (normalized.equals("<code>")) return "code";
        return null;
    }

    /**
     * Extract href URL from an anchor start tag, e.g. {@code <a href="...">}.
     * Returns null if not an anchor tag or no href found.
     */
    @Nullable
    public static String extractHref(@NonNull String startTag) {
        if (!startTag.toLowerCase().trim().startsWith("<a ")) return null;
        Pattern p = Pattern.compile("href\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(startTag);
        if (m.find()) return m.group(1);
        return null;
    }

    // ========== Shared editing helpers for insertHtmlAtCursor / wrapSelection ==========

    /**
     * Insert HTML content at the cursor position of an EditText.
     * Converts HTML to Spanned so formatting renders visually (WYSIWYG).
     *
     * @param editText      The target EditText
     * @param html          The HTML content to insert
     * @param imageGetter   Optional image getter for rendering images (may be null)
     * @return The new cursor position after insertion
     */
    public static int insertHtmlAtCursor(@NonNull EditText editText, @NonNull String html,
                                          @Nullable EditableImageGetter imageGetter) {
        Spanned spanned = fromHtml(html, imageGetter);
        int start = Math.max(editText.getSelectionStart(), 0);
        int end = Math.max(editText.getSelectionEnd(), 0);
        editText.getText().replace(Math.min(start, end), Math.max(start, end), spanned);
        int newPosition = Math.min(start, end) + spanned.length();
        editText.setSelection(newPosition);
        return newPosition;
    }

    /**
     * Shared fallback for wrapSelection when tags are not recognized as format toggles or links.
     * Wraps the selected text with literal start/end tag strings.
     *
     * @param editText The target EditText
     * @param startTag The opening tag string
     * @param endTag   The closing tag string
     * @return The new cursor position after wrapping
     */
    public static int wrapSelectionLiteral(@NonNull EditText editText, @NonNull String startTag, @NonNull String endTag) {
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();
        Editable editable = editText.getText();

        if (start == end) {
            editable.insert(start, startTag + endTag);
            int newPosition = start + startTag.length();
            editText.setSelection(newPosition);
            return newPosition;
        } else {
            int lo = Math.min(start, end);
            int hi = Math.max(start, end);
            String selectedText = editable.subSequence(lo, hi).toString();
            editable.replace(lo, hi, startTag + selectedText + endTag);
            int newStart = lo + startTag.length();
            int newEnd = newStart + selectedText.length();
            editText.setSelection(newStart, newEnd);
            return newEnd;
        }
    }

    /**
     * Apply a format toggle or link from parsed HTML tags on an EditText's selection.
     *
     * @param editText  The target EditText
     * @param startTag  The opening tag string
     * @param endTag    The closing tag string
     * @return true if handled as a recognized format/link, false if caller should use literal fallback
     */
    public static boolean applyWrapFormat(@NonNull EditText editText, @NonNull String startTag, @NonNull String endTag) {
        Editable editable = editText.getText();
        int selStart = editText.getSelectionStart();
        int selEnd = editText.getSelectionEnd();

        String formatName = parseHtmlTags(startTag, endTag);
        if (formatName != null) {
            if (selStart != selEnd) {
                int start = Math.min(selStart, selEnd);
                int end = Math.max(selStart, selEnd);
                switch (formatName) {
                    case "bold": toggleBold(editable, start, end); break;
                    case "italic": toggleItalic(editable, start, end); break;
                    case "code": toggleCode(editable, start, end); break;
                    case "codeblock": toggleCodeBlock(editable, start, end); break;
                }
            }
            return true;
        }

        String href = extractHref(startTag);
        if (href != null) {
            if (selStart != selEnd) {
                applyLink(editable, Math.min(selStart, selEnd), Math.max(selStart, selEnd), href);
            }
            return true;
        }

        return false;
    }

    // ========== Cursor-position span truncation (for no-selection toggle) ==========

    /**
     * Truncate a bold span at the cursor so that subsequently typed characters
     * do NOT inherit bold from SPAN_EXCLUSIVE_INCLUSIVE extension.
     * Call when the user wants to stop typing bold at the cursor.
     */
    public static void truncateBoldAtCursor(Editable editable, int cursor) {
        truncateStyleAtCursor(editable, cursor, Typeface.BOLD);
    }

    public static void truncateItalicAtCursor(Editable editable, int cursor) {
        truncateStyleAtCursor(editable, cursor, Typeface.ITALIC);
    }

    public static void truncateCodeAtCursor(Editable editable, int cursor) {
        if (cursor <= 0 || editable.length() == 0) return;
        int checkPos = Math.min(cursor - 1, editable.length() - 1);
        for (TypefaceSpan s : editable.getSpans(checkPos, checkPos + 1, TypefaceSpan.class)) {
            if (!"monospace".equals(s.getFamily())) continue;
            int spanEnd = editable.getSpanEnd(s);
            if (spanEnd >= cursor) {
                int spanStart = editable.getSpanStart(s);
                int flags = editable.getSpanFlags(s);
                editable.removeSpan(s);
                editable.setSpan(new TypefaceSpan("monospace"), spanStart, cursor,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (spanEnd > cursor) {
                    editable.setSpan(new TypefaceSpan("monospace"), cursor, spanEnd, flags);
                }
            }
        }
        // Also truncate the background span
        for (BackgroundColorSpan s : editable.getSpans(checkPos, checkPos + 1, BackgroundColorSpan.class)) {
            int spanEnd = editable.getSpanEnd(s);
            if (spanEnd >= cursor) {
                int spanStart = editable.getSpanStart(s);
                int flags = editable.getSpanFlags(s);
                int color = s.getBackgroundColor();
                editable.removeSpan(s);
                editable.setSpan(new BackgroundColorSpan(color), spanStart, cursor,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (spanEnd > cursor) {
                    editable.setSpan(new BackgroundColorSpan(color), cursor, spanEnd, flags);
                }
            }
        }
    }

    private static void truncateStyleAtCursor(Editable editable, int cursor, int style) {
        if (cursor <= 0 || editable.length() == 0) return;
        int checkPos = Math.min(cursor - 1, editable.length() - 1);
        for (StyleSpan s : editable.getSpans(checkPos, checkPos + 1, StyleSpan.class)) {
            if (s.getStyle() != style) continue;
            int spanEnd = editable.getSpanEnd(s);
            if (spanEnd >= cursor) {
                // Truncate: end the span at cursor with EXCLUSIVE_EXCLUSIVE so new chars don't inherit
                int spanStart = editable.getSpanStart(s);
                int flags = editable.getSpanFlags(s);
                editable.removeSpan(s);
                editable.setSpan(new StyleSpan(style), spanStart, cursor,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                // If the span extended beyond cursor, re-create the remainder with original flags
                if (spanEnd > cursor) {
                    editable.setSpan(new StyleSpan(style), cursor, spanEnd, flags);
                }
            }
        }
    }

    // ========== Internal helpers ==========

    private static boolean toggleStyleSpan(Editable editable, int selStart, int selEnd, int style) {
        if (selStart == selEnd) {
            // No selection — caller handles via activeFormatsForNextChar
            return !isStyleActive(editable, selStart, style);
        }
        if (isRangeCoveredByStyle(editable, selStart, selEnd, style)) {
            removeStyleSpans(editable, selStart, selEnd, style);
            return false;
        } else {
            int[] merged = mergeStyleRange(editable, selStart, selEnd, style);
            removeStyleSpans(editable, merged[0], merged[1], style);
            editable.setSpan(new StyleSpan(style), merged[0], merged[1], SPAN_FLAGS);
            return true;
        }
    }

    private static boolean isStyleActive(Editable editable, int position, int style) {
        if (position <= 0 || editable.length() == 0) return false;
        int checkPos = Math.min(position - 1, editable.length() - 1);
        for (StyleSpan s : editable.getSpans(checkPos, checkPos + 1, StyleSpan.class)) {
            if (s.getStyle() == style) return true;
        }
        return false;
    }

    private static boolean isRangeCoveredByStyle(Editable editable, int start, int end, int style) {
        // Every character in [start, end) must be covered by at least one StyleSpan of the given style
        for (int i = start; i < end; i++) {
            boolean covered = false;
            for (StyleSpan s : editable.getSpans(i, i + 1, StyleSpan.class)) {
                if (s.getStyle() == style) {
                    covered = true;
                    break;
                }
            }
            if (!covered) return false;
        }
        return true;
    }

    private static void removeStyleSpans(Editable editable, int start, int end, int style) {
        for (StyleSpan s : editable.getSpans(start, end, StyleSpan.class)) {
            if (s.getStyle() == style) {
                int spanStart = editable.getSpanStart(s);
                int spanEnd = editable.getSpanEnd(s);
                editable.removeSpan(s);
                // Preserve portions outside the removal range
                if (spanStart < start) {
                    editable.setSpan(new StyleSpan(style), spanStart, start, SPAN_FLAGS);
                }
                if (spanEnd > end) {
                    editable.setSpan(new StyleSpan(style), end, spanEnd, SPAN_FLAGS);
                }
            }
        }
    }

    private static int[] mergeStyleRange(Editable editable, int selStart, int selEnd, int style) {
        int mergedStart = selStart;
        int mergedEnd = selEnd;
        for (StyleSpan s : editable.getSpans(selStart, selEnd, StyleSpan.class)) {
            if (s.getStyle() == style) {
                mergedStart = Math.min(mergedStart, editable.getSpanStart(s));
                mergedEnd = Math.max(mergedEnd, editable.getSpanEnd(s));
            }
        }
        return new int[]{mergedStart, mergedEnd};
    }

    /**
     * Generic range coverage check with an optional filter predicate.
     */
    private static <T> boolean isRangeCovered(Editable editable, int start, int end,
                                              Class<T> clazz, SpanFilter<T> filter) {
        for (int i = start; i < end; i++) {
            boolean covered = false;
            for (T s : editable.getSpans(i, i + 1, clazz)) {
                if (filter == null || filter.matches(s)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) return false;
        }
        return true;
    }

    private static <T> void removeSpansInRange(Editable editable, int start, int end,
                                                Class<T> clazz, SpanFilter<T> filter) {
        for (T s : editable.getSpans(start, end, clazz)) {
            if (filter != null && !filter.matches(s)) continue;
            int spanStart = editable.getSpanStart(s);
            int spanEnd = editable.getSpanEnd(s);
            editable.removeSpan(s);
            // Preserve portions outside the removal range (for TypefaceSpan and BackgroundColorSpan)
            if (s instanceof TypefaceSpan && spanStart < start) {
                editable.setSpan(new TypefaceSpan(((TypefaceSpan) s).getFamily()), spanStart, start, SPAN_FLAGS);
            }
            if (s instanceof TypefaceSpan && spanEnd > end) {
                editable.setSpan(new TypefaceSpan(((TypefaceSpan) s).getFamily()), end, spanEnd, SPAN_FLAGS);
            }
            if (s instanceof BackgroundColorSpan && spanStart < start) {
                editable.setSpan(new BackgroundColorSpan(((BackgroundColorSpan) s).getBackgroundColor()), spanStart, start, SPAN_FLAGS);
            }
            if (s instanceof BackgroundColorSpan && spanEnd > end) {
                editable.setSpan(new BackgroundColorSpan(((BackgroundColorSpan) s).getBackgroundColor()), end, spanEnd, SPAN_FLAGS);
            }
        }
    }

    private static <T> int[] mergeRange(Editable editable, int selStart, int selEnd,
                                         Class<T> clazz, SpanFilter<T> filter) {
        int mergedStart = selStart;
        int mergedEnd = selEnd;
        for (T s : editable.getSpans(selStart, selEnd, clazz)) {
            if (filter != null && !filter.matches(s)) continue;
            mergedStart = Math.min(mergedStart, editable.getSpanStart(s));
            mergedEnd = Math.max(mergedEnd, editable.getSpanEnd(s));
        }
        return new int[]{mergedStart, mergedEnd};
    }

    private interface SpanFilter<T> {
        boolean matches(T span);
    }

    // ========== Serialization helpers ==========

    private static class SpanEvent {
        final int position;
        final boolean isOpen;
        final String tag;
        final String attrs; // for open tags only
        final Object span;  // the original span, for determining open-order

        SpanEvent(int position, boolean isOpen, String tag, String attrs, Object span) {
            this.position = position;
            this.isOpen = isOpen;
            this.tag = tag;
            this.attrs = attrs;
            this.span = span;
        }
    }

    /**
     * Comparator for SpanEvents: sort by position, then closes before opens,
     * and for closes at the same position use LIFO order (reverse of open order).
     */
    private static class SpanEventComparator implements Comparator<SpanEvent> {
        private final Editable editable;

        SpanEventComparator(Editable editable) {
            this.editable = editable;
        }

        @Override
        public int compare(SpanEvent a, SpanEvent b) {
            if (a.position != b.position) return Integer.compare(a.position, b.position);
            // At the same position: closes before opens
            if (a.isOpen != b.isOpen) return a.isOpen ? 1 : -1;
            if (!a.isOpen) {
                // Both closing at same position: LIFO — the one that opened later closes first
                int aStart = editable.getSpanStart(a.span);
                int bStart = editable.getSpanStart(b.span);
                return Integer.compare(bStart, aStart); // later open → earlier close
            }
            // Both opening at same position: the one with the wider range opens first
            int aEnd = editable.getSpanEnd(a.span);
            int bEnd = editable.getSpanEnd(b.span);
            return Integer.compare(bEnd, aEnd); // wider range opens first (closes later)
        }
    }

    private static void appendEscapedText(StringBuilder sb, String text, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\uFFFC': break; // skip object replacement char (used by ImageSpan)
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '&': sb.append("&amp;"); break;
                case '\n': sb.append("<br>"); break;
                default: sb.append(c);
            }
        }
    }

    private static String escapeAttr(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    // ========== Custom TagHandler for <code> and <pre> ==========

    private static class CodePreTagHandler implements Html.TagHandler {
        // Marker class for tracking tag open positions
        private static class CodeMark {}
        private static class PreMark {}

        @Override
        public void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader) {
            if ("code".equalsIgnoreCase(tag)) {
                if (opening) {
                    output.setSpan(new CodeMark(), output.length(), output.length(),
                            Spanned.SPAN_MARK_MARK);
                } else {
                    CodeMark mark = getLast(output, CodeMark.class);
                    if (mark != null) {
                        int start = output.getSpanStart(mark);
                        output.removeSpan(mark);
                        if (start < output.length()) {
                            // Check if this code is inside a <pre> block
                            PreMark preMark = getLast(output, PreMark.class);
                            boolean isBlock = preMark != null &&
                                    output.getSpanStart(preMark) <= start;

                            output.setSpan(new TypefaceSpan("monospace"), start, output.length(), SPAN_FLAGS);
                            output.setSpan(new BackgroundColorSpan(CODE_BG_COLOR), start, output.length(), SPAN_FLAGS);
                            if (isBlock) {
                                output.setSpan(new CodeBlockSpan(), start, output.length(), SPAN_FLAGS);
                            }
                        }
                    }
                }
            } else if ("pre".equalsIgnoreCase(tag)) {
                if (opening) {
                    output.setSpan(new PreMark(), output.length(), output.length(),
                            Spanned.SPAN_MARK_MARK);
                } else {
                    PreMark mark = getLast(output, PreMark.class);
                    if (mark != null) {
                        output.removeSpan(mark);
                    }
                }
            }
        }

        private static <T> T getLast(Editable text, Class<T> kind) {
            T[] spans = text.getSpans(0, text.length(), kind);
            if (spans.length == 0) return null;
            return spans[spans.length - 1];
        }
    }
}
