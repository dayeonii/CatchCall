package com.example.catchcall;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
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

    private final ActivityResultLauncher<String[]> reqPerms =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> showPermState());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        switchTest = findViewById(R.id.switch_test);
        btnPerm = findViewById(R.id.btn_perm);
        btnBattery = findViewById(R.id.btn_battery);

        SettingsStore settings = new SettingsStore(this);
        switchTest.setChecked(settings.getCooldownSeconds() <= 60L);

        switchTest.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setDebugTestMode(isChecked);
            Toast.makeText(this,
                    isChecked ? "테스트 모드(쿨다운 60초)" : "실모드(30일)",
                    Toast.LENGTH_SHORT).show();
        });

        btnPerm.setOnClickListener(v -> {
            // 부족한 권한만 추려서 요청
            List<String> need = new ArrayList<>();
            if (!granted(Manifest.permission.READ_PHONE_STATE)) need.add(Manifest.permission.READ_PHONE_STATE);
            if (!granted(Manifest.permission.SEND_SMS))       need.add(Manifest.permission.SEND_SMS);
            if (!granted(Manifest.permission.RECEIVE_SMS))    need.add(Manifest.permission.RECEIVE_SMS);
            if (!granted(Manifest.permission.READ_CALL_LOG))  need.add(Manifest.permission.READ_CALL_LOG); // 선택

            if (need.isEmpty()) {
                Toast.makeText(this, "모든 권한이 이미 허용됨", Toast.LENGTH_SHORT).show();
            } else {
                reqPerms.launch(need.toArray(new String[0]));
            }
        });

        btnBattery.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable t) {
                Toast.makeText(this, "설정에서 배터리 최적화를 해제해주세요.", Toast.LENGTH_SHORT).show();
            }
        });

        showPermState();
    }

    private boolean granted(String p) {
        return ActivityCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED;
    }

    private void showPermState() {
        boolean need = !granted(Manifest.permission.READ_PHONE_STATE)
                || !granted(Manifest.permission.SEND_SMS)
                || !granted(Manifest.permission.RECEIVE_SMS);  // ★ 추가 체크

        String msg = need ? "권한 미부여: 전화/문자/수신문자 권한 허용 필요"
                : "권한 정상";
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
