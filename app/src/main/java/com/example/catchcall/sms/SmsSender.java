package com.example.catchcall.sms;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class SmsSender {
    private static final String TAG = "AutoReply";
    private final Context ctx;

    public SmsSender(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    public boolean send(String number, String msg) {
        boolean hasPerm = ActivityCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "[SmsSender] SEND attempt → num=" + number + " perm=" + hasPerm
                + " len=" + (msg == null? -1 : msg.length()));
        if (!hasPerm || number == null || number.trim().isEmpty() || msg == null) return false;

        try {
            SmsManager sm = getDefaultSmsManager();

            // 길이 판단
            ArrayList<String> parts = sm.divideMessage(msg);

            if (parts.size() <= 1) {
                // ▶ 짧은 건 SMS 1통
                sm.sendTextMessage(number, null, msg, null, null);
                Log.d(TAG, "[SmsSender] SMS single dispatched");
                return true;
            }

            // ▶ 장문: 가능한 경우 MMS 1통으로 전송
            if (isDefaultSmsApp()) {
                boolean ok = sendAsMmsText(number, msg);
                if (ok) {
                    Log.d(TAG, "[SmsSender] MMS dispatched (single long message)");
                    return true;
                } else {
                    Log.w(TAG, "[SmsSender] MMS failed, fallback to multipart SMS");
                }
            } else {
                Log.w(TAG, "[SmsSender] Not default SMS app → fallback to multipart SMS");
            }

            // ▶ 폴백: 멀티파트 SMS (기본앱이 아니거나 MMS 실패 시)
            sm.sendMultipartTextMessage(number, null, parts, null, null);
            Log.d(TAG, "[SmsSender] Multipart SMS dispatched (fallback)");
            return true;

        } catch (Throwable t) {
            Log.e(TAG, "[SmsSender] send error: " + t.getClass().getSimpleName() + " " + t.getMessage());
            return false;
        }
    }

    /** 듀얼심 환경에서 기본 SMS 구독으로 SmsManager 가져오기 */
    private SmsManager getDefaultSmsManager() {
        SmsManager sm = SmsManager.getDefault();

        // API 22+ (LOLLIPOP_MR1) 에서만 서브스크립션 사용
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            int subId = SubscriptionManager.getDefaultSmsSubscriptionId();
            if (isValidSubIdCompat(subId)) {
                try {
                    sm = SmsManager.getSmsManagerForSubscriptionId(subId);
                } catch (Throwable ignore) { /* fallback to default */ }
            }
        }
        return sm;
    }

    // sdk API 29 미만이면 subId != -1 으로 문자 기본앱 유효성 판단
    private boolean isValidSubIdCompat(int subId) {
        if (Build.VERSION.SDK_INT >= 29) {
            // API 29+: 공식 유효성 체크 사용
            return SubscriptionManager.isValidSubscriptionId(subId);
        } else {
            // 하위 호환: -1(무효)만 배제 (일부 기기 상수는 이미 -1)
            try {
                // 가능한 경우 상수도 함께 비교
                return subId != -1 /* INVALID_SUBSCRIPTION_ID */;
            } catch (Throwable ignore) {
                return subId != -1;
            }
        }
    }

    /** 현재 앱이 기본 SMS 앱인지 */
    private boolean isDefaultSmsApp() {
        try {
            String defPkg = android.provider.Telephony.Sms.getDefaultSmsPackage(ctx);
            boolean isDefault = ctx.getPackageName().equals(defPkg);
            Log.d(TAG, "[SmsSender] isDefaultSmsApp=" + isDefault + " defaultPkg=" + defPkg);
            return isDefault;
        } catch (Throwable t) {
            // 일부 기기에서 API 접근 불가 시 이전 방식으로 폴백(안전하게 false 처리)
            Log.w(TAG, "[SmsSender] default SMS check failed: " + t.getMessage());
            return false;
        }
    }

    /** 텍스트만 들어간 MMS 한 통으로 전송 */
    private boolean sendAsMmsText(String to, String body) {
        File f = null;
        try {
            // 텍스트를 파일로 만들어 첨부 (text/plain)
            File dir = new File(ctx.getCacheDir(), "mms");
            if (!dir.exists()) dir.mkdirs();
            f = new File(dir, "text_" + System.currentTimeMillis() + ".txt");
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(body.getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName()+".fileprovider", f);

            // 기본 메시지 앱 패키지에 URI 읽기 권한 부여
            try {
                String defPkg = android.provider.Telephony.Sms.getDefaultSmsPackage(ctx);
                if (defPkg != null) {
                    ctx.grantUriPermission(defPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            } catch (Throwable t) {
                Log.w(TAG, "[SmsSender] grantUriPermission failed: " + t.getMessage());
            }

            SmsManager sm = getDefaultSmsManager();
            Bundle overrides = null; // 캐리어 기본 설정 사용
            // locationUrl(null) → 시스템 MMSC 사용
            sm.sendMultimediaMessage(ctx, uri, null, overrides, null);
            return true;

        } catch (Throwable t) {
            Log.e(TAG, "[SmsSender] sendAsMmsText failed: " + t.getMessage());
            return false;
        } finally {
            // 파일은 캐시에 남겨두어도 무방. 즉시 삭제 원하면 아래 주석 해제
            // if (f != null) try { f.delete(); } catch (Throwable ignore) {}
        }
    }
}
