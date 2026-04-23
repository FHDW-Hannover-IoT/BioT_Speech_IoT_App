package com.fhdw.biot.speech.iot.config;

import com.fhdw.biot.speech.iot.BuildConfig;

import java.util.Locale;

public final class AppConfig {

    private AppConfig() {}

    public static String mqttBrokerUrl() {
        return isEmulator() ? BuildConfig.MQTT_EMULATOR_BROKER_URL : BuildConfig.MQTT_PHONE_BROKER_URL;
    }

    public static String mcpBaseUrl() {
        String host = isEmulator() ? BuildConfig.LLM_HOST_EMULATOR : BuildConfig.LLM_HOST_PHONE;
        return "http://" + host + ":" + BuildConfig.LLM_PORT;
    }

    public static String llmChatEndpoint() {
        return mcpBaseUrl() + "/chat";
    }

    public static boolean isEmulator() {
        String f = lower(android.os.Build.FINGERPRINT);
        String m = lower(android.os.Build.MODEL);
        String p = lower(android.os.Build.PRODUCT);
        return f.startsWith("generic")
                || f.contains("vbox")
                || f.contains("test-keys")
                || m.contains("google_sdk")
                || m.contains("emulator")
                || m.contains("android sdk built for x86")
                || p.contains("sdk_gphone");
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US);
    }
}
