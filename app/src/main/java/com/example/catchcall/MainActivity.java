package com.example.catchcall;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Telephony;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.catchcall.data.SettingsStore;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Switch switchTest;
    private Button btnPerm, btnBattery, btnDefaultSms;

    // 기능 on/off 토글
    private Switch switchFeature;

    private EditText etCooldownValue;
    private Spinner spCooldownUnit;
    private Button btnSaveCooldown;
    private EditText etMissedTpl, etMannerTpl;
    private Button btnSaveTemplates;

    private final ActivityResultLauncher<String[]> reqPerms =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> showPermState());

    private final ActivityResultLauncher<Intent> reqDefaultSmsRole =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        // 단순히 결과만 안내
                        boolean isDefault = Telephony.Sms.getDefaultSmsPackage(this) != null
                                && getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(this));
                        Toast.makeText(this,
                                isDefault ? "기본 문자앱으로 설정되었습니다." : "기본 문자앱 설정이 취소/실패했습니다.",
                                Toast.LENGTH_SHORT).show();
                    });

    private SettingsStore settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = new SettingsStore(this);

        // 기본 UI 바인딩
        switchTest     = findViewById(R.id.switch_test);
        btnPerm        = findViewById(R.id.btn_perm);
        btnBattery     = findViewById(R.id.btn_battery);
        btnDefaultSms  = findViewById(R.id.btn_default_sms);

        switchFeature  = findViewById(R.id.switch_feature);
        etCooldownValue = findViewById(R.id.et_cooldown_value);
        spCooldownUnit  = findViewById(R.id.sp_cooldown_unit);
        btnSaveCooldown = findViewById(R.id.btn_save_cooldown);
        etMissedTpl     = findViewById(R.id.et_missed_tpl);
        etMannerTpl     = findViewById(R.id.et_manner_tpl);
        btnSaveTemplates= findViewById(R.id.btn_save_templates);

        // 기능 on/off 스위치
        switchFeature.setChecked(settings.isFeatureEnabled());
        switchFeature.setOnCheckedChangeListener((btn, isChecked) -> {
            settings.setFeatureEnabled(isChecked);
            Toast.makeText(this,
                    isChecked ? "자동 회신 기능 켜짐" : "자동 회신 기능 꺼짐",
                    Toast.LENGTH_SHORT).show();
        });

        // 테스트 모드 스위치 (최우선: on이면 쿨타임 0초)
        switchTest.setChecked(settings.isTestMode());
        switchTest.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setTestMode(isChecked);
            Toast.makeText(this,
                    isChecked ? "테스트 모드: 쿨타임 0초(최우선)" : "테스트 모드 해제",
                    Toast.LENGTH_SHORT).show();
            // 표시용으로만 반영 (실제 적용은 SettingsStore.getCooldownSeconds()에서 0으로 반환)
            bindCooldownToInputs(settings.getCooldownSeconds());
        });

        // 권한 요청
        btnPerm.setOnClickListener(v -> {
            List<String> need = new ArrayList<>();
            if (!granted(Manifest.permission.READ_PHONE_STATE)) need.add(Manifest.permission.READ_PHONE_STATE);
            if (!granted(Manifest.permission.SEND_SMS))         need.add(Manifest.permission.SEND_SMS);
            if (!granted(Manifest.permission.RECEIVE_SMS))      need.add(Manifest.permission.RECEIVE_SMS);
            if (!granted(Manifest.permission.READ_CALL_LOG))    need.add(Manifest.permission.READ_CALL_LOG);
            // (선택) MMS 수신/푸시 권한도 요청하고 싶다면 아래 주석 해제
            // if (!granted(Manifest.permission.RECEIVE_MMS))      need.add(Manifest.permission.RECEIVE_MMS);
            // if (!granted(Manifest.permission.RECEIVE_WAP_PUSH)) need.add(Manifest.permission.RECEIVE_WAP_PUSH);

            if (need.isEmpty()) {
                Toast.makeText(this, "모든 권한이 이미 허용됨", Toast.LENGTH_SHORT).show();
            } else {
                reqPerms.launch(need.toArray(new String[0]));
            }
        });

        // 배터리 최적화 해제 화면
        btnBattery.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable t) {
                Toast.makeText(this, "설정에서 배터리 최적화를 해제해주세요.", Toast.LENGTH_SHORT).show();
            }
        });

        // 기본 문자앱 설정 요청
        btnDefaultSms.setOnClickListener(v -> requestDefaultSmsApp());

        // UI 초기값 세팅
        bindCooldownToInputs(settings.getCooldownSeconds()); // test mode면 0으로 표시될 수 있음
        etMissedTpl.setText(settings.getMissedTemplate());
        etMannerTpl.setText(settings.getMannerTemplate());

        btnSaveCooldown.setOnClickListener(v -> saveCooldown());
        btnSaveTemplates.setOnClickListener(v -> saveTemplates());

        showPermState();
    }

    private boolean granted(String p) {
        return ActivityCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED;
    }

    private void showPermState() {
        boolean need = !granted(Manifest.permission.READ_PHONE_STATE)
                || !granted(Manifest.permission.SEND_SMS)
                || !granted(Manifest.permission.RECEIVE_SMS)
                || !granted(Manifest.permission.READ_CALL_LOG);
        String msg = need ? "권한 미부여: 전화/문자/수신문자/통화기록 권한 허용 필요" : "권한 정상";
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void bindCooldownToInputs(long seconds) {
        // 표시용: 테스트모드면 getCooldownSeconds()가 0 반환
        if (seconds == 0) {
            etCooldownValue.setText("0");
            // 기본 단위는 분으로 표시
            spCooldownUnit.setSelection(0);
            return;
        }
        if (seconds % 86400 == 0) {
            etCooldownValue.setText(String.valueOf(seconds / 86400));
            spCooldownUnit.setSelection(2); // 일
        } else if (seconds % 3600 == 0) {
            etCooldownValue.setText(String.valueOf(seconds / 3600));
            spCooldownUnit.setSelection(1); // 시
        } else {
            long mins = Math.max(1, seconds / 60);
            etCooldownValue.setText(String.valueOf(mins));
            spCooldownUnit.setSelection(0); // 분
        }
    }

    private void saveCooldown() {
        String numStr = etCooldownValue.getText().toString().trim();
        if (numStr.isEmpty()) {
            Toast.makeText(this, "쿨타임 숫자를 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        long n = Long.parseLong(numStr);
        if (n < 0) {
            Toast.makeText(this, "0 이상의 값을 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        int unitPos = spCooldownUnit.getSelectedItemPosition();
        long seconds = (unitPos == 0) ? n * 60L : (unitPos == 1) ? n * 3600L : n * 86400L;

        // 저장: 테스트모드와 무관하게 사용자가 지정한 값을 보관
        settings.setCooldownSeconds(seconds);

        // 테스트모드 스위치를 건드리지 않는다 (테스트모드가 우선 적용)
        Toast.makeText(this,
                "쿨타임 저장됨" + (settings.isTestMode() ? " (테스트모드로 인해 현재는 0초 적용)" : ""),
                Toast.LENGTH_SHORT).show();

        // 표시용 갱신
        bindCooldownToInputs(settings.getCooldownSeconds());
    }

    private void saveTemplates() {
        String missed = etMissedTpl.getText().toString().trim();
        String manner = etMannerTpl.getText().toString().trim();

        // 빈 값이면 getXxxTemplate()에서 기본값을 리턴하도록 저장 로직 유지
        settings.setMissedTemplate(missed);
        settings.setMannerTemplate(manner);

        Toast.makeText(this, "템플릿 저장 완료", Toast.LENGTH_SHORT).show();
    }

    private void requestDefaultSmsApp() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager rm = getSystemService(RoleManager.class);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS)) {
                    Intent i = rm.createRequestRoleIntent(RoleManager.ROLE_SMS);
                    reqDefaultSmsRole.launch(i);
                    return;
                }
            }
            // 하위 버전 방식
            Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
            reqDefaultSmsRole.launch(intent);
        } catch (Throwable t) {
            Toast.makeText(this, "기본 문자앱 요청 실패: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
