package com.example.catchcall.data;

import android.content.Context;
import android.content.SharedPreferences;

// 설정값 저장 (쿨타임, 템플릿 등)
public class SettingsStore {
    private final SharedPreferences pref;

    public SettingsStore(Context ctx) {
        this.pref = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public String getTemplate() {
        String def = "지금은 통화가 어렵습니다. 확인 후 연락드릴게요. https://comwel.vercel.app";
        return pref.getString("template", def);
    }

    public void setTemplate(String v) {
        pref.edit().putString("template", v).apply();
    }

    public long getCooldownSeconds() {
        return pref.getLong("cooldown_sec", 2_592_000L); // 30일(초)
    }

    public void setCooldownSeconds(long sec) {
        pref.edit().putLong("cooldown_sec", sec).apply();
    }

    public void setDebugTestMode(boolean on) {
        setCooldownSeconds(on ? 60L : 2_592_000L);
    }
}
