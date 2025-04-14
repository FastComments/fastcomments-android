package com.fastcomments.sdk;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Map;

/**
 * Base class for renderable nodes in the comment tree
 */
public abstract class RenderableNode {
    public abstract int determineNestingLevel(Map<String, RenderableComment> commentMap);
    
    /**
     * Date separator node type for grouping comments by date in live chat
     */
    public static class DateSeparator extends RenderableNode {
        private final LocalDate date;
        
        public DateSeparator(LocalDate date) {
            this.date = date;
        }
        
        public LocalDate getDate() {
            return date;
        }
        
        public String getFormattedDate() {
            // Format date based on user's locale
            DateTimeFormatter formatter = DateTimeFormatter
                    .ofLocalizedDate(FormatStyle.MEDIUM)
                    .withLocale(Locale.getDefault());
            return date.format(formatter);
        }
        
        @Override
        public int determineNestingLevel(Map<String, RenderableComment> commentMap) {
            // Date separators are always at top level
            return 0;
        }
    }
}