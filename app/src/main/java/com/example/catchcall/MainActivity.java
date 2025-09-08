package com.example.catchcall;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
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
    private Button btnPerm, btnBattery;

    // CHANGED: 기능 on/off 토글
    private Switch switchFeature;

    private EditText etCooldownValue;
    private Spinner spCooldownUnit;
    private Button btnSaveCooldown;
    private EditText etMissedTpl, etMannerTpl;
    private Button btnSaveTemplates;

    private final ActivityResultLauncher<String[]> reqPerms =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> showPermState());

    private SettingsStore settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = new SettingsStore(this);

        // 기본 UI
        switchTest     = findViewById(R.id.switch_test);
        btnPerm        = findViewById(R.id.btn_perm);
        btnBattery     = findViewById(R.id.btn_battery);

        // CHANGED: 기능 on/off 스위치 (activity_main.xml에 추가했다고 가정)
        switchFeature  = findViewById(R.id.switch_feature);
        switchFeature.setChecked(settings.isFeatureEnabled());
        switchFeature.setOnCheckedChangeListener((btn, isChecked) -> {
            settings.setFeatureEnabled(isChecked);
            Toast.makeText(this,
                    isChecked ? "자동 회신 기능 켜짐" : "자동 회신 기능 꺼짐",
                    Toast.LENGTH_SHORT).show();
        });

        etCooldownValue = findViewById(R.id.et_cooldown_value);
        spCooldownUnit  = findViewById(R.id.sp_cooldown_unit);
        btnSaveCooldown = findViewById(R.id.btn_save_cooldown);
        etMissedTpl     = findViewById(R.id.et_missed_tpl);
        etMannerTpl     = findViewById(R.id.et_manner_tpl);
        btnSaveTemplates= findViewById(R.id.btn_save_templates);

        // 기존 테스트 모드 스위치
        switchTest.setChecked(settings.getCooldownSeconds() <= 60L);
        switchTest.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setDebugTestMode(isChecked);
            Toast.makeText(this,
                    isChecked ? "테스트 모드(쿨타임 없음)" : "실모드(30일)",
                    Toast.LENGTH_SHORT).show();
            bindCooldownToInputs(settings.getCooldownSeconds());
        });

        // 권한 요청
        btnPerm.setOnClickListener(v -> {
            List<String> need = new ArrayList<>();
            if (!granted(Manifest.permission.READ_PHONE_STATE)) need.add(Manifest.permission.READ_PHONE_STATE);
            if (!granted(Manifest.permission.SEND_SMS))         need.add(Manifest.permission.SEND_SMS);
            if (!granted(Manifest.permission.RECEIVE_SMS))      need.add(Manifest.permission.RECEIVE_SMS);
            if (!granted(Manifest.permission.READ_CALL_LOG))    need.add(Manifest.permission.READ_CALL_LOG);
            if (need.isEmpty()) {
                Toast.makeText(this, "모든 권한이 이미 허용됨", Toast.LENGTH_SHORT).show();
            } else {
                reqPerms.launch(need.toArray(new String[0]));
            }
        });

        // 배터리 최적화
        btnBattery.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable t) {
                Toast.makeText(this, "설정에서 배터리 최적화를 해제해주세요.", Toast.LENGTH_SHORT).show();
            }
        });

        // UI 초기값 세팅
        bindCooldownToInputs(settings.getCooldownSeconds());
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
                || !granted(Manifest.permission.RECEIVE_SMS);
        String msg = need ? "권한 미부여: 전화/문자/수신문자 권한 허용 필요" : "권한 정상";
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void bindCooldownToInputs(long seconds) {
        if (seconds % 86400 == 0) {
            etCooldownValue.setText(String.valueOf(seconds / 86400));
            spCooldownUnit.setSelection(2);
        } else if (seconds % 3600 == 0) {
            etCooldownValue.setText(String.valueOf(seconds / 3600));
            spCooldownUnit.setSelection(1);
        } else {
            long mins = Math.max(1, seconds / 60);
            etCooldownValue.setText(String.valueOf(mins));
            spCooldownUnit.setSelection(0);
        }
    }

    private void saveCooldown() {
        String numStr = etCooldownValue.getText().toString().trim();
        if (numStr.isEmpty()) {
            Toast.makeText(this, "쿨타임 숫자를 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        long n = Long.parseLong(numStr);
        if (n <= 0) {
            Toast.makeText(this, "1 이상의 값을 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        int unitPos = spCooldownUnit.getSelectedItemPosition();
        long seconds = (unitPos == 0) ? n * 60L : (unitPos == 1) ? n * 3600L : n * 86400L;
        settings.setCooldownSeconds(seconds);
        switchTest.setChecked(seconds <= 60L);
        Toast.makeText(this, "쿨타임 저장됨", Toast.LENGTH_SHORT).show();
    }

    private void saveTemplates() {
        String missed = etMissedTpl.getText().toString().trim();
        String manner = etMannerTpl.getText().toString().trim();

        // CHANGED: 빈 값이면 기본값 저장하지 않고 getXxxTemplate()가 알아서 기본 리턴
        settings.setMissedTemplate(missed);
        settings.setMannerTemplate(manner);

        Toast.makeText(this, "템플릿 저장 완료", Toast.LENGTH_SHORT).show();
    }
}
