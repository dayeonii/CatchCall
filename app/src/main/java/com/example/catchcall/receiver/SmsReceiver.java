package com.example.catchcall.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import com.example.catchcall.domain.AutoReplyUseCase;

import java.util.Locale;

// 매너콜 문자 감지
public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        for (SmsMessage msg : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
            String from = msg.getDisplayOriginatingAddress();
            String body = msg.getMessageBody();
            Log.d("AutoReply", "SMS in → from=" + from + " body=" + body);

            if (body == null) continue;

            // 널/공백/대소문자/특수문자 차이를 줄이기 위한 전처리
            String norm = body.toLowerCase(Locale.KOREA).replaceAll("\\s+", "");
            // 통신사 문구에 따라 키워드 후보 확장
            boolean isManner =
                    norm.contains("매너콜") ||
                            norm.contains("콜키퍼") ||
                            norm.contains("통화중안내") ||
                            norm.contains("부재중안내");

            if (isManner && from != null && from.length() > 0) {
                Log.d("AutoReply", "MannerCall detected from=" + from);
                new AutoReplyUseCase(context).onMannerCall(from);
            }
        }
    }
}
