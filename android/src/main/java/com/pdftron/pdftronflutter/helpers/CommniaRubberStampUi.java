package com.pdftron.pdftronflutter.helpers;

import androidx.annotation.NonNull;

import com.pdftron.pdf.model.StandardStampPreviewAppearance;
import com.pdftron.pdf.tools.RubberStampCreate;
import com.pdftron.pdf.tools.ToolManager;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hides Apryse built-in standard rubber stamps so only custom stamps (including Commnia
 * workflow stamps) appear in the picker. The add-stamp (+) control is unchanged.
 */
public final class CommniaRubberStampUi {

    private static final StandardStampPreviewAppearance[] NO_STANDARD_STAMPS =
            new StandardStampPreviewAppearance[0];

    private static final Map<ToolManager, Boolean> INSTALLED = new WeakHashMap<>();

    private CommniaRubberStampUi() {
    }

    public static void installHideStandardStamps(@NonNull ToolManager toolManager) {
        synchronized (INSTALLED) {
            if (Boolean.TRUE.equals(INSTALLED.get(toolManager))) {
                return;
            }
            INSTALLED.put(toolManager, Boolean.TRUE);
        }

        ToolManager.ToolChangedListener listener = (newTool, oldTool) -> applyIfRubberStamp(newTool);
        toolManager.addToolChangedListener(listener);
        toolManager.addToolCreatedListener(listener);
        applyIfRubberStamp(toolManager.getTool());
    }

    private static void applyIfRubberStamp(ToolManager.Tool tool) {
        if (tool instanceof RubberStampCreate) {
            hideStandardStampsOnTool((RubberStampCreate) tool);
        }
    }

    private static void hideStandardStampsOnTool(@NonNull RubberStampCreate tool) {
        tool.setCustomStampAppearance(NO_STANDARD_STAMPS, null);
    }
}
