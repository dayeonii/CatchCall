package com.example.catchcall.sms;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;

import androidx.core.app.ActivityCompat;

// 실제 SMS 발송 담당
public class SmsSender {
    private final Context ctx;

    public SmsSender(Context ctx) { this.ctx = ctx; }

    public boolean send(String number, String msg) {
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            return false; // 권한 없으면 조용히 실패
        }
        try {
            SmsManager sm = SmsManager.getDefault();
            sm.sendTextMessage(number, null, msg, null, null);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
