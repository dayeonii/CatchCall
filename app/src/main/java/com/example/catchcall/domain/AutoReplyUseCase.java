package com.example.catchcall.domain;

import android.content.Context;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import com.example.catchcall.data.SentLogRepository;
import com.example.catchcall.data.SettingsStore;
import com.example.catchcall.sms.SmsSender;

// 쿨타임 검사 + 문자 발송 요청 로직
public class AutoReplyUseCase {
    private static final String TAG = "AutoReply";

    private final SentLogRepository repo;
    private final SettingsStore settings;
    private final SmsSender sms;

    public AutoReplyUseCase(Context ctx) {
        this.repo = new SentLogRepository(ctx);
        this.settings = new SettingsStore(ctx);
        this.sms = new SmsSender(ctx);
    }

    // 부재중 알림에 대해 문자 보내기 (부재중 템플릿 사용)
    // 로직 변경 없음: 로그만 강화 + 토글 차단 추가
    public void onMissedCall(String rawNumber) {
        Log.d(TAG, "[UseCase.onMissedCall] raw=" + rawNumber);

        // 기능 OFF면 즉시 차단
        if (!settings.isFeatureEnabled()) {
            Log.d(TAG, "BLOCK: feature disabled → skip onMissedCall");
            return;
        }

        String number = normalize(rawNumber);
        Log.d(TAG, "[UseCase.onMissedCall] normalized=" + number);

        String msg = settings.getMissedTemplate();
        Log.d(TAG, "[UseCase.onMissedCall] feature=" + settings.isFeatureEnabled()
                + " tmplLen=" + (msg == null ? 0 : msg.trim().length()));

        if (msg == null) Log.d(TAG, "[UseCase.onMissedCall] template is null");
        if (msg != null && msg.trim().isEmpty()) Log.d(TAG, "[UseCase.onMissedCall] template is empty/whitespace");

        trySendWithCooldown(number, msg);
    }

    // 매너콜 알림에 대해 문자 보내기 (매너콜 템플릿 사용)
    // 로직 변경 없음: 로그만 강화 + 토글 차단 추가
    public void onMannerCall(String rawNumber) {
        Log.d(TAG, "[UseCase.onMannerCall] raw=" + rawNumber);

        // 기능 OFF면 즉시 차단
        if (!settings.isFeatureEnabled()) {
            Log.d(TAG, "BLOCK: feature disabled → skip onMannerCall");
            return;
        }

        String number = normalize(rawNumber);
        Log.d(TAG, "[UseCase.onMannerCall] normalized=" + number);

        String msg = settings.getMannerTemplate();
        Log.d(TAG, "[UseCase.onMannerCall] feature=" + settings.isFeatureEnabled()
                + " tmplLen=" + (msg == null ? 0 : msg.trim().length()));

        if (msg == null) Log.d(TAG, "[UseCase.onMannerCall] template is null");
        if (msg != null && msg.trim().isEmpty()) Log.d(TAG, "[UseCase.onMannerCall] template is empty/whitespace");

        trySendWithCooldown(number, msg);
    }

    // 쿨타임 체크 로직 (그대로) + 상세 로그만 추가
    private void trySendWithCooldown(String number, String message) {
        Log.d(TAG, "[UseCase.trySendWithCooldown] in → number=" + number);

        // 안전망: 토글 OFF면 여기서도 차단
        if (!settings.isFeatureEnabled()) {
            Log.d(TAG, "BLOCK: feature disabled → skip send");
            return;
        }

        long now = System.currentTimeMillis();
        long last = (number == null ? 0L : repo.getLastSent(number));
        long cooldownSec = settings.getCooldownSeconds();

        // 문자 길이/세그먼트 참고 로그 (발송 전 미리 계산)
        if (message != null) {
            try {
                int[] calc = SmsMessage.calculateLength(message, false);
                Log.d(TAG, "[UseCase.trySendWithCooldown] msgSegments=" + calc[0]
                        + " codeUnitsUsed=" + calc[1] + " codeUnitsRemaining=" + calc[2]
                        + " perSegmentLimit=" + calc[3]);
            } catch (Throwable t) {
                Log.d(TAG, "[UseCase.trySendWithCooldown] lengthCalc error: " + t.getMessage());
            }
        } else {
            Log.d(TAG, "[UseCase.trySendWithCooldown] message is null");
        }

        Log.d(TAG, "[UseCase.trySendWithCooldown] now=" + now
                + " last=" + last
                + " cooldownSec=" + cooldownSec);

        // 쿨타임이 0이면 검사 스킵 (원래 로직 유지)
        if (cooldownSec > 0) {
            long cooldownMs = cooldownSec * 1000L;
            long elapsed = (last == 0 ? cooldownMs : (now - last));
            long remain = (last == 0 ? 0 : Math.max(0, cooldownMs - (now - last)));

            Log.d(TAG, "[UseCase.trySendWithCooldown] elapsedMs=" + (now - last)
                    + " remainMs=" + remain);

            if (last > 0 && (now - last) < cooldownMs) {
                Log.d(TAG, "BLOCK: cooldown active → 발송 금지 (remainMs=" + remain + ")");
                return;
            }
        } else {
            Log.d(TAG, "[UseCase.trySendWithCooldown] cooldown disabled (0s)");
        }

        // 실제 발송
        boolean ok = sms.send(number, message);
        Log.d(TAG, ok ? ("SEND OK → " + number) : ("SEND FAIL → " + number));

        if (ok) {
            repo.saveLastSent(number, now);
            Log.d(TAG, "[UseCase.trySendWithCooldown] lastSent updated ts=" + now);
        }
    }

    private String normalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            Log.d(TAG, "[UseCase.normalize] raw is null/empty");
            return null;
        }
        String n = PhoneNumberUtils.normalizeNumber(raw);
        Log.d(TAG, "[UseCase.normalize] raw=" + raw + " → normalized=" + n);
        if (n == null || n.isEmpty()) Log.d(TAG, "[UseCase.normalize] normalized is null/empty");
        return (n == null || n.isEmpty()) ? null : n;
    }
}
