package com.pdftron.pdftronflutter.helpers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pdftron.pdf.asynctask.CreateBitmapFromCustomStampTask;
import com.pdftron.pdf.model.CustomStampOption;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.flutter.plugin.common.MethodChannel;

/**
 * Registers four Commnia workflow rubber stamps in the Apryse custom-stamp store.
 * Idempotent: removes prior entries whose primary text matches the fixed titles, then re-adds.
 */
public final class CommniaWorkflowRubberStamps {

    private static final String TAG = "CommniaWorkflowStamps";

    static final String[] STAMP_TITLES = new String[]{
            "Approved with Comments",
            "Approved",
            "Rejected",
            "Superseded"
    };

    private static final int[] BG_COLORS = new int[]{
            0xFF2196F3,
            0xFF4CAF50,
            0xFFF44336,
            0xFFFF9800
    };

    private static final Set<String> TITLE_SET = new HashSet<>();

    static {
        Collections.addAll(TITLE_SET, STAMP_TITLES);
    }

    private CommniaWorkflowRubberStamps() {
    }

    /**
     * Parses optional ISO-8601 / common API timestamps so {@link #buildSecondLine} can format consistently.
     */
    private static Date tryParseTimestamp(String s) {
        String[] patterns = new String[]{
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd HH:mm:ss"
        };
        for (String p : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(p, Locale.US);
                sdf.setLenient(false);
                Date d = sdf.parse(s);
                if (d != null) {
                    return d;
                }
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    /** Second line: {@code By {name} at {time}, {dd MMM yyyy}} using device locale. */
    private static String buildSecondLine(String trimmedName, @Nullable String formattedTimestamp) {
        Date when = new Date();
        if (formattedTimestamp != null) {
            String t = formattedTimestamp.trim();
            if (!t.isEmpty()) {
                Date parsed = tryParseTimestamp(t);
                if (parsed != null) {
                    when = parsed;
                }
            }
        }
        SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());
        SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return "By " + trimmedName + " at " + timeFmt.format(when) + ", " + dateFmt.format(when);
    }

    public static void sync(
            @NonNull Context context,
            @NonNull String displayName,
            @Nullable String formattedTimestamp,
            @NonNull MethodChannel.Result result) {
        String trimmedName = displayName.trim();
        if (trimmedName.isEmpty()) {
            result.error("invalid_argument", "displayName must be a non-empty string", null);
            return;
        }
        String secondText = buildSecondLine(trimmedName, formattedTimestamp);

        try {
            removeExistingCommniaStamps(context);
            int singleLine = dpToPx(context, 40);
            int twoLine = dpToPx(context, 56);
            for (int i = 0; i < STAMP_TITLES.length; i++) {
                int bg = BG_COLORS[i];
                CustomStampOption option = new CustomStampOption(
                        STAMP_TITLES[i],
                        secondText,
                        bg,
                        bg,
                        Color.WHITE,
                        Color.TRANSPARENT,
                        1.0,
                        false,
                        false
                );
                Bitmap bitmap = CreateBitmapFromCustomStampTask.createBitmapFromCustomStamp(
                        option, singleLine, twoLine);
                if (bitmap == null) {
                    result.error("stamp_bitmap", "Could not build stamp bitmap for " + STAMP_TITLES[i], null);
                    return;
                }
                CustomStampOption.addCustomStamp(context, option, bitmap);
                if (!bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
            result.success(null);
        } catch (Exception e) {
            Log.e(TAG, "sync failed", e);
            result.error("stamp_sync", e.getMessage() != null ? e.getMessage() : e.toString(), null);
        }
    }

    private static void removeExistingCommniaStamps(Context context) {
        int n = CustomStampOption.getCustomStampsCount(context);
        List<Integer> toRemove = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            try {
                com.pdftron.sdf.Obj obj = CustomStampOption.getCustomStampObj(context, i);
                if (obj == null) {
                    continue;
                }
                CustomStampOption parsed = new CustomStampOption(obj);
                if (parsed.text != null && TITLE_SET.contains(parsed.text.trim())) {
                    toRemove.add(i);
                }
            } catch (Exception e) {
                Log.w(TAG, "skip index " + i, e);
            }
        }
        Collections.sort(toRemove, Collections.reverseOrder());
        for (int idx : toRemove) {
            CustomStampOption.removeCustomStamp(context, idx);
        }
    }

    private static int dpToPx(Context context, int dp) {
        float d = context.getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(dp * d));
    }
}
