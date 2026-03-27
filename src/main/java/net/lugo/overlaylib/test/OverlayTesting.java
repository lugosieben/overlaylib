package net.lugo.overlaylib.test;

import net.lugo.overlaylib.OverlayLib;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class OverlayTesting {
    private static final String GENERAL_SCOPE = "general";
    private static final Map<String, OverlayTestRegistration> TESTS = new LinkedHashMap<>();
    private static final AtomicBoolean BOOTSTRAPPED = new AtomicBoolean(false);

    private static volatile boolean reportingEnabled = false;

    private OverlayTesting() { }

    public static void bootstrap() {
        if (!BOOTSTRAPPED.compareAndSet(false, true)) {
            return;
        }

        SimpleOverlayTest.register();
    }

    public static synchronized void registerTest(String id, OverlayTest test, Map<String, Supplier<Boolean>> functions) {
        String normalizedId = normalize(id);

        Map<String, Supplier<Boolean>> normalizedFunctions = new LinkedHashMap<>();
        if (functions != null) {
            for (Map.Entry<String, Supplier<Boolean>> entry : functions.entrySet()) {
                String normalizedFunction = normalize(entry.getKey());
                if (!normalizedFunction.isEmpty() && entry.getValue() != null) {
                    normalizedFunctions.put(normalizedFunction, entry.getValue());
                }
            }
        }

        test.id = normalizedId;
        TESTS.put(normalizedId, new OverlayTestRegistration(test, normalizedFunctions));
        OverlayLib.LOGGER.debug("Registered test '{}' with {} function(s)", normalizedId, normalizedFunctions.size());
    }

    public static synchronized List<String> getTestIds() {
        return List.copyOf(TESTS.keySet());
    }

    public static synchronized List<String> getFunctionIds(String id) {
        OverlayTestRegistration registration = TESTS.get(normalize(id));
        if (registration == null) {
            return List.of();
        }

        return List.copyOf(registration.functions().keySet());
    }

    public static synchronized boolean enable(String id) {
        OverlayTest test = getTest(id);
        if (test == null) {
            return false;
        }

        return test.enable();
    }

    public static synchronized boolean disable(String id) {
        OverlayTest test = getTest(id);
        if (test == null) {
            return false;
        }

        return test.disable();
    }

    public static synchronized @Nullable Boolean run(String id, String function) {
        OverlayTestRegistration registration = TESTS.get(normalize(id));
        if (registration == null) {
            return null;
        }

        Supplier<Boolean> action = registration.functions().get(normalize(function));
        if (action == null) {
            return null;
        }

        return Boolean.TRUE.equals(action.get());
    }

    public static synchronized boolean isEnabled(String id) {
        OverlayTest test = getTest(id);
        return test != null && test.isEnabled();
    }

    public static void setReportingEnabled(boolean enabled) {
        reportingEnabled = enabled;
        OverlayLib.LOGGER.info("[OverlayTesting] reporting {}", enabled ? "enabled" : "disabled");
    }

    public static boolean shouldReport() {
        return reportingEnabled;
    }

    public static boolean isReportingEnabled() {
        return shouldReport();
    }

    public static void report(String message) {
        report(GENERAL_SCOPE, message);
    }

    public static void report(Supplier<String> messageSupplier) {
        report(GENERAL_SCOPE, messageSupplier);
    }

    @SuppressWarnings("LoggingSimilarMessage")
    public static void report(String scope, String message) {
        if (!shouldReport()) {
            return;
        }

        OverlayLib.LOGGER.info("[OverlayTesting][{}] {}", normalizeScope(scope), message);
    }

    @SuppressWarnings("LoggingSimilarMessage")
    public static void report(String scope, Supplier<String> messageSupplier) {
        if (!shouldReport()) {
            return;
        }

        String message = messageSupplier.get();
        if (message == null) {
            return;
        }

        OverlayLib.LOGGER.info("[OverlayTesting][{}] {}", normalizeScope(scope), message);
    }

    private static OverlayTest getTest(String id) {
        OverlayTestRegistration registration = TESTS.get(normalize(id));
        return registration == null ? null : registration.test();
    }

    private static String normalize(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeScope(String scope) {
        String normalized = normalize(scope);
        return normalized.isEmpty() ? GENERAL_SCOPE : normalized;
    }

    private record OverlayTestRegistration(OverlayTest test, Map<String, Supplier<Boolean>> functions) { }

    public abstract static class OverlayTest {
        private String id;
        private boolean enabled;

        public final boolean enable() {
            if (enabled) {
                return false;
            }

            onEnable();
            enabled = true;
            report("enabled");
            return true;
        }

        public final boolean disable() {
            if (!enabled) {
                return false;
            }

            onDisable();
            enabled = false;
            report("disabled");
            return true;
        }

        public final void register(String id, Map<String, Supplier<Boolean>> functions) {
            OverlayTesting.registerTest(id, this, functions);
        }

        public final void report(String message) {
            OverlayTesting.report(id, message);
        }

        public final void report(Supplier<String> messageSupplier) {
            OverlayTesting.report(id, messageSupplier);
        }

        public final boolean isEnabled() {
            return enabled;
        }

        protected abstract void onEnable();

        protected abstract void onDisable();
    }
}