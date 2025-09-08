package com.example.catchcall.data;

import android.content.Context;
import android.content.SharedPreferences;

// 발송 로그 저장 및 조회
public class SentLogRepository {
    private final SharedPreferences pref;

    public SentLogRepository(Context ctx) {
        this.pref = ctx.getSharedPreferences("sent_log", Context.MODE_PRIVATE);
    }

    private String key(String num) { return "sent_" + num; }

    public Long getLastSentAt(String num) {
        long v = pref.getLong(key(num), -1L);
        return (v <= 0) ? null : v;
    }

    public void setLastSentAt(String num, long ts) {
        pref.edit().putLong(key(num), ts).apply();
    }

    // 수정
    public long getLastSent(String num) {
        Long v = getLastSentAt(num);
        return v == null ? 0L : v;
    }

    public void saveLastSent(String num, long ts) {
        setLastSentAt(num, ts);
    }
}
