package com.example.catchcall.sms;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;

// 실제 SMS 발송 담당 (멀티파트 전송 대응)
public class SmsSender {
    private static final String TAG = "AutoReply";
    private final Context ctx;

    public SmsSender(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public boolean send(String number, String msg) {
        boolean hasPerm = ActivityCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "[SmsSender] SEND attempt → number=" + number
                + " hasSEND_SMS=" + hasPerm
                + " msgLen=" + (msg == null ? -1 : msg.length()));
        if (!hasPerm) {
            Log.w(TAG, "[SmsSender] BLOCK: SEND_SMS permission missing");
            return false;
        }
        if (number == null || number.trim().isEmpty() || msg == null) {
            Log.w(TAG, "[SmsSender] invalid args (number/msg)");
            return false;
        }

        try {
            SmsManager sm = SmsManager.getDefault();

            // 길이/인코딩 로그(디버그용)
            int[] calc = SmsMessage.calculateLength(msg, /*use7bitOnly=*/false);
            int segments = calc[0];
            int encoding = calc[3]; // 1=GSM7bit, 3=UCS-2(한글/이모지)
            Log.d(TAG, "[SmsSender] length → segments=" + segments
                    + " codeUnitsUsed=" + calc[1]
                    + " codeUnitsRemaining=" + calc[2]
                    + " encoding=" + encoding);

            // 멀티파트 안전 전송
            ArrayList<String> parts = sm.divideMessage(msg);
            Log.d(TAG, "[SmsSender] parts=" + parts.size());
            if (parts.size() > 1) {
                sm.sendMultipartTextMessage(number, null, parts, null, null);
                Log.d(TAG, "[SmsSender] sendMultipartTextMessage dispatched OK");
            } else {
                sm.sendTextMessage(number, null, msg, null, null);
                Log.d(TAG, "[SmsSender] sendTextMessage dispatched OK");
            }
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "[SmsSender] EX during send: " + t.getClass().getSimpleName() + " " + t.getMessage());
            return false;
        }
    }
}
