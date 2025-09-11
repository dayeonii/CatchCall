package com.example.catchcall.data;

import android.content.Context;
import android.content.SharedPreferences;

// 설정값 저장 (쿨타임, 템플릿 등)
public class SettingsStore {
    private final SharedPreferences pref;

    public SettingsStore(Context ctx) {
        this.pref = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    // 앱 기능 자체 활성화/비활성화 토글
    public boolean isFeatureEnabled() {
        return pref.getBoolean("feature_enabled", true); // 기본은 켜짐
    }
    public void setFeatureEnabled(boolean enabled) {
        pref.edit().putBoolean("feature_enabled", enabled).apply();
    }

    // --- 기본 템플릿 ---
    private String getDefaultMissedTemplate() {
        return "[근로복지공단 퇴직연금 안내]\n" +
                "\n" +
                "안녕하세요. 근로복지공단 퇴직연금 담당 윤용현 전문관입니다.  \n" +
                "현재는 다른 민원인과 통화 중이라 전화를 바로 받지 못해 죄송합니다.  \n" +
                "\n" +
                "혹시 도움이 필요하시다면, 모바일 안내 가이드를 참고해 주세요.  \n" +
                "\uD83D\uDC49 https://comwel.vercel.app  \n" +
                "(※ 업무 처리 자체는 공단 홈페이지에서 하실 수 있으며, 위 링크는 이해를 돕기 위한 안내 자료입니다.)  \n" +
                "\n" +
                "이미 업무를 마치셨다면 ‘완료’라고 문자 회신해 주시면 큰 도움이 됩니다.  \n" +
                "\n" +
                "직접 통화를 원하신다면 조금만 기다려주세요^^  \n통화가 끝나는 대로 꼭 다시 연락드리겠습니다.  \n" +
                "항상 소중한 시간을 내어주셔서 감사드립니다.";
    }

    private String getDefaultMannerTemplate() {
        return "[근로복지공단 퇴직연금 안내]\n" +
                "\n" +
                "안녕하세요. 근로복지공단 퇴직연금 담당 윤용현 전문관입니다.  \n" +
                "현재는 다른 민원인과 통화 중이라 전화를 바로 받지 못해 죄송합니다.  \n" +
                "\n" +
                "혹시 도움이 필요하시다면, 모바일 안내 가이드를 참고해 주세요.  \n" +
                "\uD83D\uDC49 https://comwel.vercel.app  \n" +
                "(※ 업무 처리 자체는 공단 홈페이지에서 하실 수 있으며, 위 링크는 이해를 돕기 위한 안내 자료입니다.)  \n" +
                "\n" +
                "이미 업무를 마치셨다면 ‘완료’라고 문자 회신해 주시면 큰 도움이 됩니다.  \n" +
                "\n" +
                "직접 통화를 원하신다면 조금만 기다려주세요^^  \n통화가 끝나는 대로 꼭 다시 연락드리겠습니다.  \n" +
                "항상 소중한 시간을 내어주셔서 감사드립니다.";
    }

    // --- 템플릿 저장/로드 ---
    public String getMissedTemplate() {
        String v = pref.getString("template_missed", null);
        if (v == null || v.trim().isEmpty()) return getDefaultMissedTemplate();
        return v;
    }
    public void setMissedTemplate(String v) {
        if (v == null || v.trim().isEmpty()) {
            pref.edit().remove("template_missed").apply();
        } else {
            pref.edit().putString("template_missed", v).apply();
        }
    }

    public String getMannerTemplate() {
        String v = pref.getString("template_manner", null);
        if (v == null || v.trim().isEmpty()) return getDefaultMannerTemplate();
        return v;
    }
    public void setMannerTemplate(String v) {
        if (v == null || v.trim().isEmpty()) {
            pref.edit().remove("template_manner").apply();
        } else {
            pref.edit().putString("template_manner", v).apply();
        }
    }

    // --- 테스트 모드 (최우선) ---
    public boolean isTestMode() {
        return pref.getBoolean("test_mode", false);
    }
    public void setTestMode(boolean on) {
        pref.edit().putBoolean("test_mode", on).apply();
    }

    // 호환용 (기존 메서드 유지): 내부적으로 test_mode 토글로 위임
    public void setDebugTestMode(boolean on) {
        setTestMode(on);
    }

    // --- 쿨타임 ---
    // 테스트 모드일 땐 무조건 0초(최우선). 아니면 사용자 설정 값(기본 5초).
    public long getCooldownSeconds() {
        if (isTestMode()) return 0L;                // 테스트 모드 최우선
        return pref.getLong("cooldown_sec", 5L);    // 기본 5초
    }

    public void setCooldownSeconds(long sec) {
        if (sec < 0) sec = 0;
        pref.edit().putLong("cooldown_sec", sec).apply();
    }

    // (과거 실험용) 함께 추가했던 병합 윈도우 — 현재 정책에서는 미사용
    public long getCoalesceSeconds() {
        return pref.getLong("coalesce_sec", 8L);
    }
}
