package com.fastcomments.sdk;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

/**
 * Hosts {@link PollVotersDialog} so it survives rotation and process death with its page and search.
 */
public class PollVotersDialogFragment extends DialogFragment {

    public static final String TAG = "fastcomments_poll_voters";

    private static final String ARG_COMMENT_ID = "commentId";
    private static final String ARG_TENANT_ID = "tenantId";
    private static final String STATE_PAGE = "page";
    private static final String STATE_SEARCH = "search";

    // Not saved: a recreated dialog uses the SDK of the comments view the new activity builds.
    private FastCommentsSDK sdk;
    private PollVotersDialog votersDialog;

    /**
     * Shows who voted on a poll. Without a FragmentActivity host it falls back to a plain dialog that closes with
     * the activity's window.
     */
    public static void show(@NonNull Context context, @NonNull FastCommentsSDK sdk, @NonNull String commentId) {
        FragmentActivity fragmentActivity = findContext(context, FragmentActivity.class);
        if (fragmentActivity == null) {
            showWithoutFragment(context, sdk, commentId);
            return;
        }
        FragmentManager fragmentManager = fragmentActivity.getSupportFragmentManager();
        if (fragmentManager.isStateSaved()) {
            return;
        }
        Bundle args = new Bundle();
        args.putString(ARG_COMMENT_ID, commentId);
        args.putString(ARG_TENANT_ID, sdk.getConfig().tenantId);
        PollVotersDialogFragment fragment = new PollVotersDialogFragment();
        fragment.setArguments(args);
        fragment.sdk = sdk;
        fragment.show(fragmentManager, TAG);
    }

    private static void showWithoutFragment(Context context, FastCommentsSDK sdk, String commentId) {
        PollVotersDialog dialog = new PollVotersDialog(context, sdk, commentId);
        Activity activity = findContext(context, Activity.class);
        if (activity != null) {
            View decor = activity.getWindow().getDecorView();
            View.OnAttachStateChangeListener closeWithWindow = new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {}

                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {
                    dialog.dismiss();
                }
            };
            decor.addOnAttachStateChangeListener(closeWithWindow);
            dialog.setOnDismissListener(d -> decor.removeOnAttachStateChangeListener(closeWithWindow));
        }
        dialog.show();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        if (sdk == null) {
            sdk = findSdk(requireActivity().getWindow().getDecorView(), args.getString(ARG_TENANT_ID));
        }
        if (sdk == null) {
            // The recreated activity has no comments view to load voters through; onStart closes this.
            return super.onCreateDialog(savedInstanceState);
        }
        int page = savedInstanceState != null ? savedInstanceState.getInt(STATE_PAGE) : 0;
        String search = savedInstanceState != null ? savedInstanceState.getString(STATE_SEARCH, "") : "";
        votersDialog = new PollVotersDialog(requireContext(), sdk, args.getString(ARG_COMMENT_ID, ""), page, search);
        return votersDialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (votersDialog == null) {
            dismissAllowingStateLoss();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (votersDialog != null) {
            outState.putInt(STATE_PAGE, votersDialog.getPage());
            outState.putString(STATE_SEARCH, votersDialog.getSearch());
        }
    }

    @Nullable
    private static FastCommentsSDK findSdk(View view, @Nullable String tenantId) {
        FastCommentsSDK candidate = null;
        if (view instanceof FastCommentsView) {
            candidate = ((FastCommentsView) view).getSDK();
        } else if (view instanceof LiveChatView) {
            candidate = ((LiveChatView) view).getSDK();
        }
        if (candidate != null && candidate.getConfig().tenantId.equals(tenantId)) {
            return candidate;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                FastCommentsSDK found = findSdk(group.getChildAt(i), tenantId);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Nullable
    private static <T> T findContext(Context context, Class<T> type) {
        while (context != null) {
            if (type.isInstance(context)) {
                return type.cast(context);
            }
            context = context instanceof ContextWrapper ? ((ContextWrapper) context).getBaseContext() : null;
        }
        return null;
    }
}
