package com.fastcomments.sdk;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

/**
 * Thin EditText subclass that exposes selection change events.
 * Used for WYSIWYG toolbar active-state updates when the cursor moves.
 */
public class RichEditText extends AppCompatEditText {

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selStart, int selEnd);
    }

    private OnSelectionChangedListener selectionListener;
    private boolean suppressSelectionEvents = false;

    public RichEditText(@NonNull Context context) {
        super(context);
    }

    public RichEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public RichEditText(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionListener = listener;
    }

    /**
     * Suppress selection change callbacks during programmatic setText/clear operations.
     * This prevents NPEs and spurious toolbar updates when fired before toolbar is initialized.
     */
    public void setSuppressSelectionEvents(boolean suppress) {
        this.suppressSelectionEvents = suppress;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (selectionListener != null && !suppressSelectionEvents) {
            selectionListener.onSelectionChanged(selStart, selEnd);
        }
    }
}
