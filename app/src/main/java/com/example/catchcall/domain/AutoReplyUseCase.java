package com.example.catchcall.domain;

import android.content.Context;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.example.catchcall.data.SentLogRepository;
import com.example.catchcall.data.SettingsStore;
import com.example.catchcall.sms.SmsSender;

// 쿨타임 검사 + 문자 발송 요청 로직
public class AutoReplyUseCase {
    private final SentLogRepository repo;
    private final SettingsStore settings;
    private final SmsSender sms;

    public AutoReplyUseCase(Context ctx) {
        this.repo = new SentLogRepository(ctx);
        this.settings = new SettingsStore(ctx);
        this.sms = new SmsSender(ctx);
    }

    public void onMissedCall(String rawNumber) {
        if (rawNumber == null || rawNumber.isEmpty()) return;
        String number = PhoneNumberUtils.normalizeNumber(rawNumber);
        if (number == null || number.isEmpty()) return;

        long now = System.currentTimeMillis();
        Long last = repo.getLastSentAt(number);
        if (last == null) last = 0L;

        long cooldownMs = settings.getCooldownSeconds() * 1000L;
        Log.d("AutoReply", "missed=" + number + " last=" + last + " now=" + now + " cd=" + cooldownMs);

        if (now - last > cooldownMs) {
            String msg = settings.getTemplate();
            boolean ok = sms.send(number, msg);
            if (ok) {
                repo.setLastSentAt(number, now);
                Log.d("AutoReply", "SMS sent → " + number);
            } else {
                Log.w("AutoReply", "SMS send failed → " + number);
            }
        } else {
            Log.d("AutoReply", "cooldown → skip " + number);
        }
    }

    // (확장) 통화중 착신 시에도 동일 로직 사용 가능
    public void onMannerCall(String rawNumber) { onMissedCall(rawNumber); }
}
