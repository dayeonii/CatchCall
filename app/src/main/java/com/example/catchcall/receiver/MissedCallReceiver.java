package com.example.catchcall.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CallLog;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.catchcall.domain.AutoReplyUseCase;

// 전화 상태 리시버
public class MissedCallReceiver extends BroadcastReceiver {
    private static boolean wasRinging = false;
    private static String lastIncoming = null;

    private static final String TAG = "AutoReply";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

        Log.d(TAG, "[MissedCallReceiver] onReceive: state=" + state
                + " extraIncoming=" + number
                + " wasRinging=" + wasRinging
                + " lastIncoming=" + lastIncoming);

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            wasRinging = true;
            lastIncoming = number;
            Log.d(TAG, "[MissedCallReceiver] RINGING: cache incoming=" + number);

        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            wasRinging = false;
            Log.d(TAG, "[MissedCallReceiver] OFFHOOK: answered; reset wasRinging");

        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            Log.d(TAG, "[MissedCallReceiver] IDLE: evaluate missed condition "
                    + "(wasRinging=" + wasRinging + ", lastIncoming=" + lastIncoming + ")");

            boolean dispatched = false;

            // 정상 경로: 직전에 RINGING 잡힘
            if (wasRinging && lastIncoming != null) {
                Log.d(TAG, "[MissedCallReceiver] MISSED detected. Dispatch to UseCase with number=" + lastIncoming);
                new AutoReplyUseCase(context).onMissedCall(lastIncoming);
                dispatched = true;
            }

            // Fallback: RINGING 놓쳤거나 번호 null
            if (!dispatched) {
                boolean hasReadCallLog = ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.READ_CALL_LOG
                ) == PackageManager.PERMISSION_GRANTED;

                if (!hasReadCallLog) {
                    Log.w(TAG, "[MissedCallReceiver] Fallback skipped: READ_CALL_LOG permission missing");
                } else {
                    Cursor c = null;
                    try {
                        c = context.getContentResolver().query(
                                CallLog.Calls.CONTENT_URI,
                                new String[]{CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE},
                                null, null,
                                CallLog.Calls.DATE + " DESC"
                        );
                        if (c != null && c.moveToFirst()) {
                            int type = c.getInt(c.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                            long when = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DATE));
                            String num = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                            long now = System.currentTimeMillis();

                            if (type == CallLog.Calls.MISSED_TYPE && now - when < 120_000L) {
                                Log.d(TAG, "[MissedCallReceiver] Fallback: recent MISSED in CallLog number=" + num
                                        + " ageMs=" + (now - when));
                                new AutoReplyUseCase(context).onMissedCall(num);
                            } else {
                                Log.d(TAG, "[MissedCallReceiver] Fallback: latest call not recent-MISSED "
                                        + "(type=" + type + ", ageMs=" + (now - when) + ")");
                            }
                        } else {
                            Log.d(TAG, "[MissedCallReceiver] Fallback: CallLog query empty/null");
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "[MissedCallReceiver] Fallback error: "
                                + t.getClass().getSimpleName() + " " + t.getMessage());
                    } finally {
                        if (c != null) c.close();
                    }
                }
            }

            wasRinging = false;
            lastIncoming = null;
            Log.d(TAG, "[MissedCallReceiver] state cleared (wasRinging=false, lastIncoming=null)");
        }
    }
}
