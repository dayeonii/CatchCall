package com.example.catchcall.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.example.catchcall.domain.AutoReplyUseCase;

// 전화 상태 리시버
public class MissedCallReceiver extends BroadcastReceiver {
    private static boolean wasRinging = false;
    private static String lastIncoming = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            wasRinging = true;
            lastIncoming = number;
            Log.d("AutoReply", "RINGING: " + number);
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            // 통화 연결됨 → 부재중 조건 해제
            wasRinging = false;
            Log.d("AutoReply", "OFFHOOK");
        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            Log.d("AutoReply", "IDLE");
            // 직전에 RINGING이었고 OFFHOOK 없이 IDLE → 부재중으로 간주
            if (wasRinging && lastIncoming != null) {
                new AutoReplyUseCase(context).onMissedCall(lastIncoming);
            }
            wasRinging = false;
            lastIncoming = null;
        }
    }
}
