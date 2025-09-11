package com.example.catchcall.receiver;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CallLog;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.catchcall.domain.AutoReplyUseCase;

/**
 * 부재중 트리거 전용 리시버.
 * - 리시버에서는 쿨타임을 보지 않는다(정책은 UseCase에서만 판단).
 * - "같은 통화 1회 처리" + "주 경로 직후 짧은 억제창으로 Fallback 중복 방지"만 담당.
 */
public class MissedCallReceiver extends BroadcastReceiver {
    private static final String TAG = "AutoReply";
    private static final String PREF = "auto_reply";
    private static final long   FALLBACK_SUPPRESS_MS = 10_000L; // 주 경로 후 10초간 Fallback 억제

    // 최근 사이클 상태
    private static boolean wasRinging = false;
    private static boolean wasOffhook = false;
    private static String  lastIncoming = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;

        final String state  = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        final String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER); // Q+ 에선 null 가능
        if (state == null) return;

        Log.d(TAG, "[MissedCallReceiver] onReceive: state=" + state
                + " num=" + number
                + " wasRinging=" + wasRinging
                + " wasOffhook=" + wasOffhook);

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            wasRinging = true;
            wasOffhook = false;
            lastIncoming = number; // null일 수 있음
            Log.d(TAG, "[MissedCallReceiver] RINGING: cache incoming=" + number);

        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            wasOffhook = true;     // 통화 성사 → 부재중 아님
            Log.d(TAG, "[MissedCallReceiver] OFFHOOK");

        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            Log.d(TAG, "[MissedCallReceiver] IDLE: evaluate (wasRinging=" + wasRinging + ", wasOffhook=" + wasOffhook + ")");

            boolean handled = false;

            // 1) 주 경로: RINGING → (OFFHOOK 없이) → IDLE = 전형적 부재중
            if (wasRinging && !wasOffhook) {
                String target = pickTarget(number, lastIncoming);
                if (!isNullOrEmpty(target)) {
                    dispatchAutoReply(context, target);          // 정책은 UseCase에서 판단
                    markLastProcessedCallTsNow(context, target); // 같은 사이클 재처리 방지
                    setSuppressWindow(context, target, FALLBACK_SUPPRESS_MS); // ★ Fallback 억제창
                    handled = true;
                    Log.d(TAG, "[MissedCallReceiver] dispatched via primary → " + target);
                }
            }

            // 2) 보조 경로: RINGING이 없거나 번호를 못 받은 경우 → 콜로그 확인
            if (!handled) {
                tryFallbackWithCallLog(context);
            }

            // 3) 상태 초기화
            wasRinging = false;
            wasOffhook = false;
            lastIncoming = null;
            Log.d(TAG, "[MissedCallReceiver] state cleared");
        }
    }

    // ====== Fallback & Helper ======

    private void tryFallbackWithCallLog(Context ctx) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "[MissedCallReceiver] Fallback skipped: READ_CALL_LOG permission missing");
            return;
        }

        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE},
                    null, null,
                    CallLog.Calls.DATE + " DESC"
            );
            if (c != null && c.moveToFirst()) {
                final String num = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                final int type   = c.getInt(c.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                final long when  = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DATE));
                final long now   = System.currentTimeMillis();

                boolean recent = (now - when) < 120_000L;            // 2분 내 최신만
                boolean missed = (type == CallLog.Calls.MISSED_TYPE);
                long lastTs = prefs(ctx).getLong(lastTsKey(num), 0L);
                boolean alreadyProcessed = (when == lastTs);         // 같은 '한 통화'는 한 번만

                // ★ 주 경로 직후 억제창: 같은 번호에 대해 잠깐 Fallback 무시
                if (isSuppressed(ctx, num)) {
                    Log.d(TAG, "[MissedCallReceiver] Fallback suppressed for " + num);
                    return;
                }

                Log.d(TAG, "[MissedCallReceiver] Fallback check: recent=" + recent
                        + ", missed=" + missed
                        + ", alreadyProcessed=" + alreadyProcessed
                        + ", num=" + num + ", when=" + when);

                if (recent && missed && !isNullOrEmpty(num) && !alreadyProcessed) {
                    dispatchAutoReply(ctx, num);                     // 정책은 UseCase가 판단
                    prefs(ctx).edit().putLong(lastTsKey(num), when).apply(); // 이번 '통화' 처리 기록
                    Log.d(TAG, "[MissedCallReceiver] Fallback dispatched → " + num);
                }
            } else {
                Log.d(TAG, "[MissedCallReceiver] Fallback: empty CallLog");
            }
        } catch (Throwable t) {
            Log.w(TAG, "[MissedCallReceiver] Fallback error", t);
        } finally {
            if (c != null) c.close();
        }
    }

    // 대상 번호 선택: 비어있으면 null 취급
    private String pickTarget(String extraIncoming, String cachedIncoming) {
        if (!isNullOrEmpty(cachedIncoming)) return cachedIncoming;
        if (!isNullOrEmpty(extraIncoming))  return extraIncoming;
        return null;
    }

    private static boolean isNullOrEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    // ====== 중복/억제 유틸 ======
    private SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
    private String lastTsKey(String num)      { return "last_ts_" + num; }
    private String suppressKey(String num)    { return "suppress_until_" + num; }

    private void markLastProcessedCallTsNow(Context ctx, String num) {
        prefs(ctx).edit().putLong(lastTsKey(num), System.currentTimeMillis()).apply();
    }

    private void setSuppressWindow(Context ctx, String num, long windowMs) {
        if (isNullOrEmpty(num)) return;
        long until = System.currentTimeMillis() + windowMs;
        prefs(ctx).edit().putLong(suppressKey(num), until).apply();
    }

    private boolean isSuppressed(Context ctx, String num) {
        if (isNullOrEmpty(num)) return false;
        long until = prefs(ctx).getLong(suppressKey(num), 0L);
        return System.currentTimeMillis() < until;
    }

    /** 실제 발송 요청은 UseCase 한 곳으로만 위임 (쿨타임/정책은 거기서 판단) */
    private void dispatchAutoReply(Context ctx, String number) {
        new AutoReplyUseCase(ctx).onMissedCall(number);
        Log.d(TAG, "[MissedCallReceiver] dispatched to UseCase: " + number);
    }
}
