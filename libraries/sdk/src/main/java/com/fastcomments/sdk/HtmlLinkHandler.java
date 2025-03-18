package com.fastcomments.sdk;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.xml.sax.XMLReader;

import java.util.Locale;

/**
 * Custom HTML tag handler for handling links in comment content
 */
public class HtmlLinkHandler {

    /**
     * Process the HTML content and make links clickable
     * @param context Context for opening links
     * @param html The HTML string
     * @param textView The TextView to display the text
     * @return Spanned content with clickable links
     */
    public static Spanned parseHtml(Context context, String html, TextView textView) {
        Spanned spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY, 
            new CustomImageGetter(context, textView), null);
        
        // Process the spanned text to make links clickable
        return makeLinkClickable(context, spanned);
    }
    
    /**
     * Makes links in text clickable and opens them in external browser
     * @param context Context for opening links
     * @param spanned The spanned text containing links
     * @return Spanned text with clickable links
     */
    private static Spanned makeLinkClickable(final Context context, Spanned spanned) {
        Editable editableText = Editable.Factory.getInstance().newEditable(spanned);
        
        // Find all URLSpans in the text
        URLSpan[] urlSpans = editableText.getSpans(0, editableText.length(), URLSpan.class);
        for (final URLSpan span : urlSpans) {
            int start = editableText.getSpanStart(span);
            int end = editableText.getSpanEnd(span);
            int flags = editableText.getSpanFlags(span);
            
            // Create a custom clickable span that opens links in browser
            ClickableSpan clickableSpan = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    // Get the URL from the span
                    String url = span.getURL();
                    
                    // Ensure URL has proper scheme
                    if (!url.toLowerCase(Locale.US).startsWith("http://") && 
                            !url.toLowerCase(Locale.US).startsWith("https://")) {
                        url = "https://" + url;
                    }
                    
                    // Open URL in external browser
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    context.startActivity(intent);
                }
            };
            
            // Replace the URLSpan with our custom ClickableSpan
            editableText.removeSpan(span);
            editableText.setSpan(clickableSpan, start, end, flags);
        }
        
        return editableText;
    }
}