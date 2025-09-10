package com.example.catchcall.domain;

import android.content.Context;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsMessage;
import android.util.Log;

import com.example.catchcall.data.SentLogRepository;
import com.example.catchcall.data.SettingsStore;
import com.example.catchcall.sms.SmsSender;

// 발송 조건/쿨타임 + 문자 발송 요청 로직
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

    // 부재중 알림
    public void onMissedCall(String rawNumber) {
        if (!settings.isFeatureEnabled()) {
            Log.d(TAG, "BLOCK: feature disabled");
            return;
        }
        String number = normalize(rawNumber);
        if (number == null) return;

        String msg = settings.getMissedTemplate();
        trySendWithPolicy(number, msg, "MISSED");
    }

    // 매너콜 알림
    public void onMannerCall(String rawNumber) {
        if (!settings.isFeatureEnabled()) {
            Log.d(TAG, "BLOCK: feature disabled");
            return;
        }
        String number = normalize(rawNumber);
        if (number == null) return;

        String msg = settings.getMannerTemplate();
        trySendWithPolicy(number, msg, "MANNER");
    }

    /**
     * 네가 정의한 발송 정책(표)을 그대로 구현:
     * - (A) 두 타입 모두 보낸 적 없음 → 보내기
     * - (B) 한쪽만 보낸 적 있음 → 보내기
     * - (C) 둘 다 보낸 적 있음 + 쿨타임 안 지남 → 금지
     * - (D) 둘 다 보낸 적 있음 + 쿨타임 지남 → 보내기
     *
     * 쿨타임은 "둘 다 보낸 이력"이 있을 때만 적용한다.
     * (기존 any-type 쿨타임 우선 차단 로직 제거)
     *
     * 장문 여부는 SmsSender에서 MMS 우선으로 처리(실패 시 multipart SMS fallback).
     */
    private void trySendWithPolicy(String number, String message, String type) {
        if (message == null || message.trim().isEmpty()) {
            Log.d(TAG, "[trySend] message is null/empty, skip");
            return;
        }

        long now = System.currentTimeMillis();

        // 길이/인코딩 참고 로그 (디버깅 용)
        try {
            int[] calc = SmsMessage.calculateLength(message, false);
            Log.d(TAG, "[trySend] lenCheck seg=" + calc[0]
                    + " codeUnitsUsed=" + calc[1]
                    + " codeUnitsRemaining=" + calc[2]
                    + " encoding=" + calc[3]); // 1=GSM7bit, 3=UCS-2
        } catch (Throwable t) {
            Log.w(TAG, "[trySend] lengthCalc error: " + t.getMessage());
        }

        // 타입별 이력 조회
        long lastM = repo.getLastSent(number, "MISSED");
        long lastN = repo.getLastSent(number, "MANNER");
        boolean hasM = lastM > 0;
        boolean hasN = lastN > 0;

        long cooldownSec = settings.getCooldownSeconds(); // 테스트모드 우선권은 SettingsStore 쪽에서 보장하도록
        long cooldownMs = Math.max(0, cooldownSec) * 1000L;

        Log.d(TAG, "[trySend] num=" + number + " type=" + type
                + " hasM=" + hasM + "(" + lastM + ")"
                + " hasN=" + hasN + "(" + lastN + ")"
                + " cooldownSec=" + cooldownSec);

        boolean shouldBlock;
        if (!hasM && !hasN) {
            // 최초 발송
            shouldBlock = false;
        } else if (hasM ^ hasN) {
            // 한쪽만 보낸 적 있음 → 허용
            shouldBlock = false;
        } else {
            // 둘 다 보낸 적 있음 → 쿨타임 체크: "가장 최근 발송 시각" 기준
            long lastBoth = Math.max(lastM, lastN);
            long remain = cooldownMs - (now - lastBoth);
            shouldBlock = (cooldownMs > 0 && remain > 0);
            if (shouldBlock) {
                Log.d(TAG, "BLOCK: both-types sent before, cooldown active; remainMs=" + remain);
            }
        }

        if (shouldBlock) return;

        // 실제 발송 (장문이면 SmsSender가 MMS 1통 시도, 실패 시 multipart SMS fallback)
        boolean ok = sms.send(number, message);
        Log.d(TAG, ok ? ("SEND OK → " + number) : ("SEND FAIL → " + number));
        if (ok) {
            // 타입별 이력 갱신
            repo.saveLastSent(number, type, now);
            // (과거 호환) any 기록도 갱신해두면 다른 곳에서 참조해도 안전
            repo.saveLastSent(number, now);
        }
    }

    private String normalize(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String n = PhoneNumberUtils.normalizeNumber(raw);
        return (n == null || n.isEmpty()) ? null : n;
    }
}
