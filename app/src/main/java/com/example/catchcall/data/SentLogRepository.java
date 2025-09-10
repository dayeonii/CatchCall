package com.example.catchcall.data;

import android.content.Context;
import android.content.SharedPreferences;

// 발송 로그 저장 및 조회
public class SentLogRepository {
    private final SharedPreferences pref;

    public SentLogRepository(Context ctx) {
        this.pref = ctx.getSharedPreferences("sent_log", Context.MODE_PRIVATE);
    }

    // ===== [기존 any 타입 키(과거 호환)] =====
    private String key(String num) { return "sent_" + num; }

    public Long getLastSentAt(String num) {
        long v = pref.getLong(key(num), -1L);
        return (v <= 0) ? null : v;
    }

    public void setLastSentAt(String num, long ts) {
        pref.edit().putLong(key(num), ts).apply();
    }

    public long getLastSent(String num) {
        Long v = getLastSentAt(num);
        return v == null ? 0L : v;
    }

    public void saveLastSent(String num, long ts) {
        setLastSentAt(num, ts);
    }

    // ===== [타입별 저장(MISSED/MANNER) — 신규] =====
    private String typeKey(String number, String type) {
        // type: "MISSED" / "MANNER"
        return "last_" + type + "_" + number;
    }

    /** 번호+타입별 마지막 발송 시각 (없으면 0) */
    public long getLastSent(String number, String type) {
        return pref.getLong(typeKey(number, type), 0L);
    }

    /** 번호+타입별 마지막 발송 시각 저장 */
    public void saveLastSent(String number, String type, long when) {
        pref.edit().putLong(typeKey(number, type), when).apply();
    }

    // ===== [선택: 트리거 흔적 저장(이전 실험용) — 필요 없으면 미사용] =====
    public long getLastTrigger(String number, String type) {
        return pref.getLong("last_trigger_" + type + "_" + number, 0L);
    }

    public void markTrigger(String number, String type, long when) {
        pref.edit().putLong("last_trigger_" + type + "_" + number, when).apply();
    }
}
