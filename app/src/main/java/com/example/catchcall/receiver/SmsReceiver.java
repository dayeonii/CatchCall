package com.example.catchcall.receiver;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.catchcall.domain.AutoReplyUseCase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 매너콜(통신사 안내) 키워드 감지 전용 리시버.
 *  - 우리 앱이 방금 보낸 문자/자체 템플릿/멀티파트 중복/통신사 단축번호/자기번호 등
 *    오검출을 최대한 배제한다.
 *  - 절대 '부재중 자동문자' 경로(onMissedCall)는 호출하지 않는다.
 */
public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "AutoReply";
    private static final String PREF = "sms_guard";
    private static final long   DUP_WINDOW_MS = 60_000L; // 동일(from+body) 60초 내 중복 무시

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            Log.d(TAG, "[SmsReceiver] ignore action=" + intent.getAction());
            return;
        }

        // --- PDU → SmsMessage 배열
        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            Log.d(TAG, "[SmsReceiver] bundle == null");
            return;
        }
        SmsMessage[] pdus = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (pdus == null || pdus.length == 0) {
            Log.d(TAG, "[SmsReceiver] messages == null/empty");
            return;
        }
        Log.d(TAG, "[SmsReceiver] message count=" + pdus.length);

        // --- 멀티파트 메시지 병합: from 별로 body 이어 붙임
        Map<String, ArrayList<String>> bodiesByFrom = new HashMap<>();
        for (SmsMessage pdu : pdus) {
            String from = safeOrigin(pdu);
            String body = pdu != null ? pdu.getMessageBody() : null;
            if (TextUtils.isEmpty(from) || TextUtils.isEmpty(body)) continue;
            bodiesByFrom.computeIfAbsent(from, k -> new ArrayList<>()).add(body);
        }

        for (Map.Entry<String, ArrayList<String>> e : bodiesByFrom.entrySet()) {
            final String from = e.getKey();
            final String body = TextUtils.join("", e.getValue());

            Log.d(TAG, "[SmsReceiver] merged SMS → from=" + from + " body=" + body);

            // 0) 가드: 우리 앱이 방금 보낸 문자(자기 번호/시그니처/최근발송 캐시) 무시
            if (isFromSelf(context, from)) {
                Log.d(TAG, "[SmsReceiver] skip: from self");
                continue;
            }
            if (isRecentlySentByApp(context, from, body)) {
                Log.d(TAG, "[SmsReceiver] skip: recently sent by app");
                continue;
            }

            // 1) 통신사/시스템/특수번호 필터 (필요 시 보완)
            if (isCarrierOrSystemAddress(from)) {
                Log.d(TAG, "[SmsReceiver] pass: carrier/system address (won't trigger onMannerCall)");
                // 통신사 메시지가 '매너콜'을 알려주는 본문일 수 있으므로,
                // 여기서는 '감지'만 하고 우리 쪽 발송 로직은 호출하지 않음.
                // -> 만약 네가 이 번호들에서도 onMannerCall을 원하면 아래 continue 제거하고 isManner 처리로 진행.
                continue;
            }

            // 2) 멀티파트/재배달 등으로 동일(from+body)이 중복 도착하는 케이스 방지
            if (isDuplicate(context, from, body)) {
                Log.d(TAG, "[SmsReceiver] skip: duplicate within window");
                continue;
            }

            // 3) 본문 정규화 & 키워드 판정
            boolean isManner = isMannerKeyword(body);
            Log.d(TAG, "[SmsReceiver] isManner=" + isManner);

            if (isManner) {
                Log.d(TAG, "[SmsReceiver] MannerCall detected. Dispatch to UseCase with from=" + from);
                // ★ 매너콜 전용 경로만 호출 (부재중 자동문자 경로 절대 호출 금지)
                new AutoReplyUseCase(context).onMannerCall(from);
                markHandled(context, from, body);
            }
        }
    }

    // ===== Helpers =====

    private String safeOrigin(SmsMessage msg) {
        if (msg == null) return null;
        String from = msg.getDisplayOriginatingAddress();
        if (TextUtils.isEmpty(from)) from = msg.getOriginatingAddress();
        return from;
    }

    /** 내 번호(라인 번호)와 동일한 발신 → 무시 */
    private boolean isFromSelf(Context ctx, String from) {
        try {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_NUMBERS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            String my = tm != null ? tm.getLine1Number() : null;
            if (TextUtils.isEmpty(my) || TextUtils.isEmpty(from)) return false;
            // 단순 normalize
            String a = normalizeNumber(my), b = normalizeNumber(from);
            return !TextUtils.isEmpty(a) && a.equals(b);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 우리 앱이 방금 보낸 문자라면 무시 (UseCase에서 mark 하는 모델과 연동) */
    private boolean isRecentlySentByApp(Context ctx, String from, String body) {
        // AutoReplyUseCase에서 발송 시, 상대 번호/템플릿 해시 등을 SharedPreferences에 기록해두면 여기서 활용 가능
        SharedPreferences p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        long last = p.getLong("sent_to_" + normalizeNumber(from), 0L);
        long now  = System.currentTimeMillis();
        return (now - last) < DUP_WINDOW_MS;
    }

    /** 통신사/시스템/특수번호 대략 필터 (필요 시 현장 패턴 추가) */
    private boolean isCarrierOrSystemAddress(String from) {
        if (TextUtils.isEmpty(from)) return true;
        String f = from.trim();
        // 한국 통신사/서비스 예시(가볍게): 15xx/16xx/18xx/080/00*, VMS/voicemail 등
        if (f.matches("^(15|16|18)\\d{2,}$")) return true;
        if (f.startsWith("080")) return true;
        if (f.startsWith("00"))  return true; // 국제/서비스 번호
        String lower = f.toLowerCase(Locale.KOREA);
        return lower.contains("voicemail") || lower.contains("vms");
    }

    /** 동일(from+body) 60초 중복 무시 */
    private boolean isDuplicate(Context ctx, String from, String body) {
        SharedPreferences p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String key = "dup_" + normalizeNumber(from) + "_" + body.hashCode();
        long last = p.getLong(key, 0L);
        long now  = System.currentTimeMillis();
        boolean dup = (now - last) < DUP_WINDOW_MS;
        return dup;
    }

    private void markHandled(Context ctx, String from, String body) {
        SharedPreferences p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        p.edit()
                .putLong("dup_" + normalizeNumber(from) + "_" + body.hashCode(), System.currentTimeMillis())
                .apply();
    }

    private String normalizeNumber(String s) {
        if (s == null) return null;
        return s.replaceAll("[^0-9+]", "");
    }

    /** 텍스트 정규화 후 매너콜 키워드 판정 */
    private boolean isMannerKeyword(String body) {
        if (TextUtils.isEmpty(body)) return false;
        String norm = body.toLowerCase(Locale.KOREA).replaceAll("\\s+", "");
        // 필요 시 추가: "매너콜안내", "콜키퍼", "통화중안내", "부재중안내" 등
        return norm.contains("매너콜")
                || norm.contains("콜키퍼");
    }
}
