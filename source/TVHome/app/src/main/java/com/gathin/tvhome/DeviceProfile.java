package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small, exact-match device routing data. A profile is deliberately inert when the model or
 * build fingerprint differs: vendor-specific extras must never be guessed on another firmware.
 */
final class DeviceProfile {
    private static final String TAG = "TvHome.DeviceProfile";
    private static final String PROFILE_RESOURCE = "device_profile_mitv_mftr0";

    static final class Route {
        final String label;
        final String inputId;
        final String action;
        final ComponentName component;
        final Integer inputExtra;
        final boolean shellRouteVerified;
        final boolean ordinaryAppRouteVerified;

        Route(String label, String inputId, String action, ComponentName component, Integer inputExtra,
              boolean shellRouteVerified, boolean ordinaryAppRouteVerified) {
            this.label = label;
            this.inputId = inputId;
            this.action = action;
            this.component = component;
            this.inputExtra = inputExtra;
            this.shellRouteVerified = shellRouteVerified;
            this.ordinaryAppRouteVerified = ordinaryAppRouteVerified;
        }

        Intent intent() {
            Intent intent = new Intent(action).setComponent(component);
            if (inputExtra != null) intent.putExtra("input", inputExtra);
            return intent;
        }
    }

    private final boolean matched;
    private final String model;
    private final String buildFingerprint;
    private final Map<String, Route> inputRoutes;
    private final Set<String> excludedInputIds;
    private final List<String> additionalLaunchPackages;
    private final Route settingsRoute;
    private final Route wifiRoute;
    final boolean previewEnabled;

    private DeviceProfile(boolean matched, String model, String buildFingerprint,
                          Map<String, Route> inputRoutes, Set<String> excludedInputIds,
                          List<String> additionalLaunchPackages, Route settingsRoute, Route wifiRoute,
                          boolean previewEnabled) {
        this.matched = matched;
        this.model = model;
        this.buildFingerprint = buildFingerprint;
        this.inputRoutes = Collections.unmodifiableMap(new HashMap<>(inputRoutes));
        this.excludedInputIds = Collections.unmodifiableSet(new HashSet<>(excludedInputIds));
        this.additionalLaunchPackages = Collections.unmodifiableList(new ArrayList<>(additionalLaunchPackages));
        this.settingsRoute = settingsRoute;
        this.wifiRoute = wifiRoute;
        this.previewEnabled = previewEnabled;
    }

    static DeviceProfile load(Context context) {
        int id = context.getResources().getIdentifier(PROFILE_RESOURCE, "raw", context.getPackageName());
        if (id == 0) return empty();
        try (InputStream input = context.getResources().openRawResource(id)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) != -1) bytes.write(buffer, 0, read);
            JSONObject json = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            String model = json.optString("model", "").trim();
            String fingerprint = json.optString("buildFingerprint", "").trim();
            boolean matched = Build.MODEL.equals(model) && Build.FINGERPRINT.equals(fingerprint);

            Map<String, Route> routes = new HashMap<>();
            JSONArray inputs = json.optJSONArray("inputs");
            if (inputs != null) {
                for (int i = 0; i < inputs.length(); i++) {
                    Route route = route(inputs.optJSONObject(i));
                    if (route != null && !route.inputId.isEmpty()) routes.put(route.inputId, route);
                }
            }
            Set<String> excluded = stringSet(json.optJSONArray("excludedInputIds"));
            List<String> packages = stringList(json.optJSONArray("additionalLaunchPackages"));
            Route settings = route(json.optJSONObject("settingsRoute"));
            Route wifi = route(json.optJSONObject("wifiRoute"));
            boolean preview = json.optBoolean("previewEnabled", false);
            if (!matched) {
                routes.clear();
                excluded.clear();
                packages.clear();
                settings = null;
                wifi = null;
                preview = false;
            }
            return new DeviceProfile(matched, model, fingerprint, routes, excluded, packages,
                    settings, wifi, preview);
        } catch (Exception error) {
            Log.w(TAG, "Ignoring invalid device profile", error);
            return empty();
        }
    }

    private static DeviceProfile empty() {
        return new DeviceProfile(false, "", "", Collections.emptyMap(), Collections.emptySet(),
                Collections.emptyList(), null, null, false);
    }

    private static Route route(JSONObject json) {
        if (json == null) return null;
        String label = json.optString("label", "").trim();
        String inputId = json.optString("inputId", "").trim();
        String action = json.optString("action", "").trim();
        ComponentName component = ComponentName.unflattenFromString(json.optString("component", "").trim());
        if (action.isEmpty() || component == null) return null;
        Integer inputExtra = null;
        JSONObject extras = json.optJSONObject("extras");
        if (extras != null && extras.has("input") && !extras.isNull("input")) {
            int value = extras.optInt("input", Integer.MIN_VALUE);
            if (value < 0) return null;
            inputExtra = value;
        }
        return new Route(label, inputId, action, component, inputExtra,
                json.optBoolean("shellRouteVerified", false),
                json.optBoolean("ordinaryAppRouteVerified", false));
    }

    private static List<String> stringList(JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isEmpty() && !values.contains(value)) values.add(value);
        }
        return values;
    }

    private static Set<String> stringSet(JSONArray array) {
        return new HashSet<>(stringList(array));
    }

    boolean matchesDevice() {
        return matched;
    }

    Route inputRoute(String inputId) {
        return matched ? inputRoutes.get(inputId) : null;
    }

    boolean isInputExcluded(String inputId) {
        return matched && excludedInputIds.contains(inputId);
    }

    List<Route> inputRoutes() {
        return new ArrayList<>(inputRoutes.values());
    }

    List<String> additionalLaunchPackages() {
        return additionalLaunchPackages;
    }

    boolean suppressAospSettingsTile() {
        return matched && settingsRoute != null;
    }

    Intent settingsIntent() {
        return matched && settingsRoute != null ? settingsRoute.intent() : null;
    }

    Intent wifiIntent() {
        return matched && wifiRoute != null ? wifiRoute.intent() : null;
    }
}
