package com.pdftron.pdftronflutter.helpers;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import com.pdftron.pdf.PDFViewCtrl;
import com.pdftron.pdf.config.ToolStyleConfig;
import com.pdftron.pdf.controls.RubberStampDialogFragment;
import com.pdftron.pdf.model.AnnotStyle;
import com.pdftron.pdf.model.CustomStampOption;
import com.pdftron.pdf.model.StandardStampPreviewAppearance;
import com.pdftron.pdf.tools.R;
import com.pdftron.pdf.tools.RubberStampCreate;
import com.pdftron.pdf.tools.ToolManager;
import com.pdftron.pdf.utils.PdfViewCtrlSettingsManager;
import com.pdftron.pdf.widget.preset.component.PresetBarViewModel;
import com.pdftron.pdf.widget.preset.component.model.PresetBarState;
import com.pdftron.pdf.widget.toolbar.builder.ToolbarButtonType;

import org.json.JSONObject;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Hides Apryse built-in standard rubber stamps so only custom stamps (including Commnia
 * workflow stamps) appear in the picker. The Standard/Custom tab bar is hidden (custom tab only),
 * matching iOS {@code CommniaRubberStampUi}.
 */
public final class CommniaRubberStampUi {

    private static final StandardStampPreviewAppearance[] NO_STANDARD_STAMPS =
            new StandardStampPreviewAppearance[0];

    /** Custom tab index in {@link com.pdftron.pdf.adapter.StampFragmentAdapter}. */
    private static final int CUSTOM_STAMP_TAB_INDEX = 1;

    /** {@link RubberStampCreate#getCreateAnnotType()} / {@link ToolbarButtonType#STAMP}. */
    private static final int RUBBER_STAMP_ANNOT_TYPE = 12;

    private static final String STANDARD_APPROVED_LABEL = "APPROVED";

    private static final Map<ToolManager, Boolean> INSTALLED = new WeakHashMap<>();

    private static final Set<FragmentActivity> REGISTERED_ACTIVITIES =
            Collections.newSetFromMap(new WeakHashMap<FragmentActivity, Boolean>());

    private static final Set<FragmentActivity> PRESET_OBSERVER_ACTIVITIES =
            Collections.newSetFromMap(new WeakHashMap<FragmentActivity, Boolean>());

    private static final FragmentManager.FragmentLifecycleCallbacks RUBBER_STAMP_DIALOG_CALLBACKS =
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentViewCreated(
                        @NonNull FragmentManager fm,
                        @NonNull Fragment fragment,
                        @NonNull View view,
                        @Nullable android.os.Bundle savedInstanceState) {
                    if (fragment instanceof RubberStampDialogFragment) {
                        configureCustomStampsOnlyDialog(view);
                    }
                }

                @Override
                public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment fragment) {
                    if (fragment instanceof RubberStampDialogFragment) {
                        View view = fragment.getView();
                        if (view != null) {
                            configureCustomStampsOnlyDialog(view);
                        }
                    }
                }
            };

    private CommniaRubberStampUi() {
    }

    public static void installHideStandardStamps(@NonNull ToolManager toolManager) {
        synchronized (INSTALLED) {
            if (Boolean.TRUE.equals(INSTALLED.get(toolManager))) {
                applyIfRubberStamp(toolManager, toolManager.getTool());
                ensureDialogCallbacksRegistered(toolManager);
                ensurePresetBarObserver(toolManager);
                return;
            }
            INSTALLED.put(toolManager, Boolean.TRUE);
        }

        ToolManager.ToolChangedListener listener = (newTool, oldTool) -> {
            applyIfRubberStamp(toolManager, newTool);
            ensureDialogCallbacksRegistered(toolManager);
            ensurePresetBarObserver(toolManager);
        };
        toolManager.addToolChangedListener(listener);
        toolManager.addToolCreatedListener(listener);
        ensureDialogCallbacksRegistered(toolManager);
        ensurePresetBarObserver(toolManager);
        applyIfRubberStamp(toolManager, toolManager.getTool());
    }

    /**
     * Persists the Commnia "Approved" custom stamp as the rubber-stamp preset so the preset bar
     * does not fall back to the built-in {@code APPROVED} standard stamp.
     */
    public static void saveDefaultApprovedStampPreset(@NonNull Context context) {
        String stampId = findCommniaApprovedStampId(context);
        if (stampId == null) {
            return;
        }
        persistApprovedStampPreset(context, stampId, "");
    }

    private static void ensureDialogCallbacksRegistered(@NonNull ToolManager toolManager) {
        FragmentActivity activity = toolManager.getCurrentActivity();
        if (activity == null) {
            return;
        }
        synchronized (REGISTERED_ACTIVITIES) {
            if (!REGISTERED_ACTIVITIES.add(activity)) {
                return;
            }
            activity.getSupportFragmentManager()
                    .registerFragmentLifecycleCallbacks(RUBBER_STAMP_DIALOG_CALLBACKS, false);
        }
    }

    /**
     * Observes the preset bar and replaces the built-in APPROVED preview with Commnia Approved
     * whenever the single-stamp preset strip is shown.
     */
    private static void ensurePresetBarObserver(@NonNull ToolManager toolManager) {
        FragmentActivity activity = toolManager.getCurrentActivity();
        if (activity == null) {
            return;
        }
        synchronized (PRESET_OBSERVER_ACTIVITIES) {
            if (!PRESET_OBSERVER_ACTIVITIES.add(activity)) {
                return;
            }
        }

        PresetBarViewModel viewModel;
        try {
            viewModel = new ViewModelProvider(activity).get(PresetBarViewModel.class);
        } catch (IllegalArgumentException e) {
            return;
        }

        if (!(activity instanceof LifecycleOwner)) {
            return;
        }

        LifecycleOwner lifecycleOwner = (LifecycleOwner) activity;
        Observer<PresetBarState> observer = state -> {
            if (state == null || !state.isVisible() || !state.isSinglePreset()) {
                return;
            }
            if (state.getToolbarButtonTypeId() != ToolbarButtonType.STAMP.getValue()) {
                return;
            }
            PDFViewCtrl pdfViewCtrl = toolManager.getPDFViewCtrl();
            if (pdfViewCtrl == null) {
                return;
            }
            Context context = pdfViewCtrl.getContext();
            if (context == null) {
                return;
            }
            String commniaStampId = findCommniaApprovedStampId(context);
            if (commniaStampId == null) {
                return;
            }
            String toolbarStyleId = state.getToolbarStyleId() != null
                    ? state.getToolbarStyleId() : "";
            ToolManager.Tool tool = toolManager.getTool();
            if (tool instanceof RubberStampCreate) {
                forceApprovedStampSelection(
                        (RubberStampCreate) tool, toolManager, context,
                        commniaStampId, toolbarStyleId, true);
            }
        };
        viewModel.observePresetState(lifecycleOwner, observer);
    }

    private static void applyIfRubberStamp(@NonNull ToolManager toolManager, ToolManager.Tool tool) {
        if (!(tool instanceof RubberStampCreate)) {
            return;
        }
        RubberStampCreate rubberStamp = (RubberStampCreate) tool;
        hideStandardStampsOnTool(rubberStamp);
        applyDefaultCommniaApprovedStamp(rubberStamp, toolManager, null);
    }

    private static void hideStandardStampsOnTool(@NonNull RubberStampCreate tool) {
        tool.setCustomStampAppearance(NO_STANDARD_STAMPS, null);
    }

    private static void applyDefaultCommniaApprovedStamp(
            @NonNull RubberStampCreate tool,
            @NonNull ToolManager toolManager,
            @Nullable String toolbarStyleId) {
        PDFViewCtrl pdfViewCtrl = toolManager.getPDFViewCtrl();
        if (pdfViewCtrl == null) {
            return;
        }
        Context context = pdfViewCtrl.getContext();
        if (context == null) {
            return;
        }
        String commniaStampId = findCommniaApprovedStampId(context);
        if (commniaStampId == null) {
            return;
        }

        Runnable apply = () -> forceApprovedStampSelection(
                tool, toolManager, context, commniaStampId, toolbarStyleId, false);
        pdfViewCtrl.post(apply);
        pdfViewCtrl.postDelayed(apply, 100);
        pdfViewCtrl.postDelayed(apply, 350);
        pdfViewCtrl.postDelayed(apply, 700);
    }

    private static void forceApprovedStampSelection(
            @NonNull RubberStampCreate tool,
            @NonNull ToolManager toolManager,
            @NonNull Context context,
            @NonNull String commniaStampId,
            @Nullable String toolbarStyleId,
            boolean alwaysApply) {
        String styleId = toolbarStyleId != null ? toolbarStyleId : "";
        if (!alwaysApply) {
            String currentPresetId = getSavedRubberStampPresetId(context, styleId);
            if (!shouldReplaceWithCommniaApproved(currentPresetId, context, commniaStampId)) {
                return;
            }
        }

        persistApprovedStampPreset(context, commniaStampId, styleId);
        tool.setStampName(commniaStampId);
        updatePresetBarStamp(toolManager, commniaStampId, styleId);
    }

    private static void persistApprovedStampPreset(
            @NonNull Context context,
            @NonNull String stampIdJson,
            @NonNull String toolbarStyleId) {
        try {
            AnnotStyle style = ToolStyleConfig.getInstance()
                    .getAnnotPresetStyle(context, RUBBER_STAMP_ANNOT_TYPE, 0, toolbarStyleId);
            if (style == null) {
                return;
            }
            style.setStampId(stampIdJson);
            PdfViewCtrlSettingsManager.setAnnotStylePreset(
                    context, RUBBER_STAMP_ANNOT_TYPE, 0, toolbarStyleId, style.toJSONString());
        } catch (Exception ignored) {
        }
    }

    @Nullable
    private static String getSavedRubberStampPresetId(
            @NonNull Context context,
            @NonNull String toolbarStyleId) {
        try {
            AnnotStyle style = ToolStyleConfig.getInstance()
                    .getAnnotPresetStyle(context, RUBBER_STAMP_ANNOT_TYPE, 0, toolbarStyleId);
            return style != null ? style.getStampId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String findCommniaApprovedStampId(@NonNull Context context) {
        String title = CommniaWorkflowRubberStamps.STAMP_TITLES[1];
        int count = CustomStampOption.getCustomStampsCount(context);
        for (int i = 0; i < count; i++) {
            try {
                com.pdftron.sdf.Obj obj = CustomStampOption.getCustomStampObj(context, i);
                if (obj == null) {
                    continue;
                }
                CustomStampOption option = new CustomStampOption(obj);
                if (option.text != null && title.equals(option.text.trim())) {
                    JSONObject json = new JSONObject();
                    json.put(CustomStampOption.KEY_INDEX, i);
                    return json.toString();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static boolean shouldReplaceWithCommniaApproved(
            @Nullable String currentStampLabel,
            @NonNull Context context,
            @NonNull String commniaStampId) {
        if (currentStampLabel == null || currentStampLabel.trim().isEmpty()) {
            return true;
        }
        if (STANDARD_APPROVED_LABEL.equalsIgnoreCase(currentStampLabel.trim())) {
            return true;
        }
        if (commniaStampId.equals(currentStampLabel)) {
            return false;
        }
        try {
            JSONObject json = new JSONObject(currentStampLabel);
            int index = json.optInt(CustomStampOption.KEY_INDEX, -1);
            if (index < 0) {
                return true;
            }
            String bitmapPath = CustomStampOption.getCustomStampBitmapPath(context, index);
            if (bitmapPath == null || !new File(bitmapPath).exists()) {
                return true;
            }
            com.pdftron.sdf.Obj obj = CustomStampOption.getCustomStampObj(context, index);
            if (obj == null) {
                return true;
            }
            CustomStampOption option = new CustomStampOption(obj);
            String title = CommniaWorkflowRubberStamps.STAMP_TITLES[1];
            return option.text == null || !title.equals(option.text.trim());
        } catch (Exception e) {
            return true;
        }
    }

    private static void updatePresetBarStamp(
            @NonNull ToolManager toolManager,
            @NonNull String stampIdJson,
            @NonNull String toolbarStyleId) {
        PresetBarViewModel viewModel = findPresetBarViewModel(toolManager);
        if (viewModel == null) {
            return;
        }
        FragmentActivity activity = toolManager.getCurrentActivity();
        if (activity != null) {
            viewModel.saveStampPreset(
                    activity, RUBBER_STAMP_ANNOT_TYPE, stampIdJson, toolbarStyleId, 0);
        }
        viewModel.saveStampPreset(RUBBER_STAMP_ANNOT_TYPE, stampIdJson);
        viewModel.generatePreview(ToolbarButtonType.STAMP.getValue(), stampIdJson);
    }

    @Nullable
    private static PresetBarViewModel findPresetBarViewModel(@NonNull ToolManager toolManager) {
        FragmentActivity activity = toolManager.getCurrentActivity();
        if (activity != null) {
            try {
                return new ViewModelProvider(activity).get(PresetBarViewModel.class);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (activity != null) {
            PresetBarViewModel fromFragments = findPresetBarViewModel(
                    activity.getSupportFragmentManager().getFragments());
            if (fromFragments != null) {
                return fromFragments;
            }
        }
        return null;
    }

    @Nullable
    private static PresetBarViewModel findPresetBarViewModel(@NonNull List<Fragment> fragments) {
        for (Fragment fragment : fragments) {
            if (fragment == null) {
                continue;
            }
            try {
                return new ViewModelProvider(fragment).get(PresetBarViewModel.class);
            } catch (IllegalArgumentException ignored) {
            }
            Fragment parent = fragment.getParentFragment();
            if (parent != null) {
                try {
                    return new ViewModelProvider(parent).get(PresetBarViewModel.class);
                } catch (IllegalArgumentException ignored) {
                }
            }
            List<Fragment> children = fragment.getChildFragmentManager().getFragments();
            if (!children.isEmpty()) {
                PresetBarViewModel nested = findPresetBarViewModel(children);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    /**
     * Hides the Standard/Custom tab strip and shows only the custom-stamp page.
     */
    static void configureCustomStampsOnlyDialog(@NonNull View dialogRoot) {
        View tabLayout = dialogRoot.findViewById(R.id.stamp_dialog_tab_layout);
        if (tabLayout != null) {
            tabLayout.setVisibility(View.GONE);
        }

        ViewPager pager = dialogRoot.findViewById(R.id.stamp_dialog_view_pager);
        if (pager != null) {
            dialogRoot.post(() -> {
                if (pager.getAdapter() == null) {
                    return;
                }
                int count = pager.getAdapter().getCount();
                int customIndex = count > 1 ? CUSTOM_STAMP_TAB_INDEX : 0;
                if (customIndex < count) {
                    pager.setCurrentItem(customIndex, false);
                }
            });
        }
    }
}
