package de.egym.gyms.infra.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opencensus.stats.Aggregation;
import io.opencensus.stats.Measure;
import io.opencensus.stats.Stats;
import io.opencensus.stats.View;
import io.opencensus.tags.TagContext;
import io.opencensus.tags.TagKey;
import io.opencensus.tags.TagValue;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.Arrays;
import java.util.List;

/**
 * OpenCensus JVM metrics adapter.
 */
public class JvmMetrics {
    private static final Logger log = LoggerFactory.getLogger(JvmMetrics.class);

    private static final TagKey tagKeyArea = TagKey.create("area");

    private static final Measure.MeasureLong measureMemoryUsed = Measure.MeasureLong.create("jvm/memory/used", "The amount of used memory", "By");
    private static final Measure.MeasureLong measureMemoryCommitted = Measure.MeasureLong.create("jvm/memory/committed", "The amount of memory in bytes that is committed for the Java virtual machine to use", "By");
    private static final Measure.MeasureLong measureMemoryMax = Measure.MeasureLong.create("jvm/memory/max", "The maximum amount of memory in bytes that can be used for memory management", "By");

    public static final View VIEW_MEM_USED = View.create(View.Name.create("jvm/memory/used"),
        "The amount of used memory",
        measureMemoryUsed, Aggregation.LastValue.create(), Arrays.asList(tagKeyArea));
    public static final View VIEW_MEM_COMMITTED = View.create(View.Name.create("jvm/memory/committed"),
        "The amount of memory in bytes that is committed for the Java virtual machine to use",
        measureMemoryCommitted, Aggregation.LastValue.create(), Arrays.asList(tagKeyArea));
    public static final View VIEW_MEM_MAX = View.create(View.Name.create("jvm/memory/max"),
        "The maximum amount of memory in bytes that can be used for memory management",
        measureMemoryMax, Aggregation.LastValue.create(), Arrays.asList(tagKeyArea));

    public static final List<View> ALL_VIEWS = Arrays.asList(VIEW_MEM_USED, VIEW_MEM_COMMITTED, VIEW_MEM_MAX);

    private static Thread thread;

    private static boolean stop;

    /**
     * Register all views.
     */
    public static void registerAllViews() {
        for (View view : ALL_VIEWS) {
            Stats.getViewManager().registerView(view);
        }
    }

    /**
     * Starts the metrics update thread. Does nothing if already started.
     */
    public static synchronized void start() {
        if (thread != null) {
            return;
        }

        thread = new Thread(() -> {
            while (true) {
                try {
                    update();
                } catch (Exception e) {
                    log.error("Exception while updating JVM metrics", e);
                }

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    // ignore
                }

                synchronized (JvmMetrics.class) {
                    if (stop) {
                        thread = null;
                        return;
                    }
                }
            }
        });

        thread.setName("opencensus-jvm-metrics-updater");
        thread.start();
    }

    /**
     * Stop the metrics update thread. Does nothing if already stopped.
     */
    public static synchronized void stop() {
        if (thread == null) {
            return;
        }

        stop = true;
        thread.interrupt();
    }

    private static void update() {
        for (MemoryPoolMXBean memoryPoolBean : ManagementFactory.getPlatformMXBeans(MemoryPoolMXBean.class)) {
            String area = MemoryType.HEAP.equals(memoryPoolBean.getType()) ? "heap" : "nonheap";
            final TagContext tagContext = io.opencensus.tags.Tags.getTagger().emptyBuilder().put(tagKeyArea, TagValue.create(area)).build();

            Stats.getStatsRecorder().newMeasureMap()
                .put(measureMemoryUsed, memoryPoolBean.getUsage().getUsed())
                .put(measureMemoryCommitted, memoryPoolBean.getUsage().getCommitted())
                .put(measureMemoryMax, memoryPoolBean.getUsage().getMax())
                .record(tagContext);
        }
    }
}
