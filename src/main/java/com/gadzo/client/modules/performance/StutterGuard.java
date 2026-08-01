package com.gadzo.client.modules.performance;

import com.gadzo.client.core.module.Module;
import com.gadzo.client.core.module.ModuleCategory;
import com.gadzo.client.core.setting.BooleanSetting;
import com.gadzo.client.core.setting.NumberSetting;
import com.gadzo.client.core.system.FrameTimeMonitor;
import com.gadzo.client.core.system.PerformanceDiagnosis;
import com.gadzo.client.ui.notify.Notifications;

/**
 * Notices when the game starts hitching and says what is causing it.
 *
 * <p>Stutter is the performance problem players are least equipped to diagnose. It does not
 * show up in the frame counter — the average can be excellent while the game feels awful — and
 * the usual response is to change render settings, which is the wrong lever when the cause is
 * the garbage collector. This watches for the pattern and names it once, with the number that
 * makes the case.
 *
 * <p>Warnings are rate-limited hard and only fire while the measurement actually supports one.
 * A performance warning that appears twice is advice; one that appears every minute is noise
 * that trains the player to dismiss it without reading.
 */
public class StutterGuard extends Module {

    /** Ticks between checks — no point sampling a one-minute window twenty times a second. */
    private static final int CHECK_INTERVAL_TICKS = 40;

    private final NumberSetting threshold;
    private final NumberSetting cooldownMinutes;
    private final BooleanSetting onlyWhenActionable;

    private int tickCounter;
    private long lastWarnedAt;

    public StutterGuard() {
        super("Stutter guard", "Watch frame pacing and name the cause when the game hitches",
                ModuleCategory.PERFORMANCE);

        this.threshold = addNumber("Stutters a minute", 8, 2, 60, 1,
                "How much hitching has to happen before a warning is worth showing");
        this.cooldownMinutes = addNumber("Quiet for", 10, 1, 60, 1,
                "Minimum gap between warnings")
                .suffix(" min");
        this.onlyWhenActionable = addBool("Only when fixable", true,
                "Stay quiet unless there is something specific to change");
    }

    @Override
    protected void onEnable() {
        // Do not fire a warning built from frames rendered before the module was on.
        lastWarnedAt = System.currentTimeMillis();
    }

    @Override
    public void onTick() {
        if (++tickCounter < CHECK_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        if (!FrameTimeMonitor.hasData()) {
            return;
        }
        if (FrameTimeMonitor.spikesInWindow() < threshold.getInt()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastWarnedAt < cooldownMinutes.get() * 60_000L) {
            return;
        }

        PerformanceDiagnosis.Finding finding = PerformanceDiagnosis.headline();
        if (onlyWhenActionable.get()
                && finding.severity() != PerformanceDiagnosis.Severity.BAD
                && finding.severity() != PerformanceDiagnosis.Severity.WARN) {
            return;
        }

        lastWarnedAt = now;
        if (finding.severity() == PerformanceDiagnosis.Severity.BAD) {
            Notifications.warning(finding.title(), finding.detail());
        } else {
            Notifications.info(finding.title(), finding.detail());
        }
    }
}
