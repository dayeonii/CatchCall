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
    private static final String TAG = "AutoReply";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            Log.d(TAG, "[SmsReceiver] ignore action=" + intent.getAction());
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            Log.d(TAG, "[SmsReceiver] bundle == null");
            return;
        }

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        Log.d(TAG, "[SmsReceiver] message count=" + (messages != null ? messages.length : 0));

        if (messages == null) return;

        for (SmsMessage msg : messages) {
            String from = msg.getDisplayOriginatingAddress();
            String body = msg.getMessageBody();
            Log.d(TAG, "[SmsReceiver] SMS in → from=" + from + " body=" + body);

            if (body == null) {
                Log.d(TAG, "[SmsReceiver] skip because body==null");
                continue;
            }

            // 널/공백/대소문자/특수문자 차이를 줄이기 위한 전처리
            String norm = body.toLowerCase(Locale.KOREA).replaceAll("\\s+", "");
            Log.d(TAG, "[SmsReceiver] norm=" + norm);

            // 통신사 문구에 따라 키워드 후보 확장
            boolean isManner =
                    norm.contains("매너콜") ||
                            norm.contains("콜키퍼") ||
                            norm.contains("통화중안내") ||
                            norm.contains("부재중안내");

            Log.d(TAG, "[SmsReceiver] isManner=" + isManner);

            if (isManner && from != null && from.length() > 0) {
                Log.d(TAG, "[SmsReceiver] MannerCall detected. Dispatch to UseCase with from=" + from);
                new AutoReplyUseCase(context).onMannerCall(from);
            } else if (isManner) {
                Log.d(TAG, "[SmsReceiver] MannerCall keyword matched but from number is invalid: " + from);
            }
        }
    }
}
