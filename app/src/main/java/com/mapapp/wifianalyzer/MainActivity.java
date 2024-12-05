package com.mapapp.wifianalyzer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private WifiManager wifiManager;  // Wi-Fi 정보를 가져오기 위한 WifiManager
    private TextView filePathTextView;  // 파일 경로를 표시할 TextView
    private EditText intervalInput;  // 간격 입력 필드
    private RecyclerView wifiRecyclerView;  // RecyclerView
    private WifiAdapter wifiAdapter;  // WifiAdapter
    private List<String> wifiList = new ArrayList<>();  // Wi-Fi 정보를 저장할 리스트
    private Uri directoryUri;  // 선택한 디렉토리의 URI
    private static final int REQUEST_CODE_OPEN_DIRECTORY = 1;  // 디렉토리 선택을 위한 요청 코드
    private Handler handler = new Handler();  // 데이터 저장을 위한 Handler
    private int interval = 3000;  // 기본값은 3초
    private boolean isRunning = false;  // 주기적 실행 상태 체크
    private long startTime;  // 스캔 시작 시간
    private TextView scanIntervalTextView;  // UI에 스캔 간격을 표시할 TextView

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // WifiManager 초기화
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // UI 요소 초기화
        filePathTextView = findViewById(R.id.filePathTextView);
        intervalInput = findViewById(R.id.intervalInput);
        scanIntervalTextView = findViewById(R.id.scanIntervalTextView);  // 스캔 간격을 표시할 TextView 추가

        // RecyclerView 설정
        wifiRecyclerView = findViewById(R.id.wifiRecyclerView);
        wifiRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        wifiAdapter = new WifiAdapter(wifiList);
        wifiRecyclerView.setAdapter(wifiAdapter);

        Button setStoragePathButton = findViewById(R.id.setStoragePathButton);
        Button startButton = findViewById(R.id.startButton);
        Button stopButton = findViewById(R.id.stopButton);

        // 저장 경로 설정 버튼 클릭 시 디렉토리 선택 화면으로 이동
        setStoragePathButton.setOnClickListener(v -> openDirectorySelector());

        // Start 버튼 클릭 시 Wi-Fi 데이터 저장을 시작
        startButton.setOnClickListener(v -> {
            if (!isRunning) {
                try {
                    interval = Integer.parseInt(intervalInput.getText().toString());
                } catch (NumberFormatException e) {
                    interval = 3000;  // 유효하지 않은 입력 시 기본값 3초로 설정
                }
                startPeriodicSaving();
            }
        });

        // Stop 버튼 클릭 시 주기적 실행을 멈춤
        stopButton.setOnClickListener(v -> stopPeriodicSaving());

        // Wi-Fi 스캔 결과를 수신할 브로드캐스트 리시버 등록
        IntentFilter intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiScanReceiver, intentFilter);
    }

    // Wi-Fi 스캔 결과를 수신하는 브로드캐스트 리시버
    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                long endTime = System.currentTimeMillis();  // 스캔 완료 시간 기록
                long scanDuration = endTime - startTime;    // 스캔 시간 계산

                // 스캔 간격을 UI에 표시
                scanIntervalTextView.setText("Wi-Fi 스캔 간격: " + scanDuration + "ms");

                // 스캔 결과 처리
                saveWiFiDataToSelectedDirectory();
            }
        }
    };

    // 디렉토리 선택 화면을 여는 메서드
    private void openDirectorySelector() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CODE_OPEN_DIRECTORY);
    }

    // 디렉토리 선택 후 결과 처리
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OPEN_DIRECTORY && resultCode == RESULT_OK) {
            if (data != null) {
                directoryUri = data.getData();
                getContentResolver().takePersistableUriPermission(directoryUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                filePathTextView.setText("Selected Directory: " + directoryUri.getPath());
            }
        }
    }

    // 주기적으로 Wi-Fi 데이터를 저장하는 메서드
    private void startPeriodicSaving() {
        isRunning = true;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    startWiFiScan();  // 스캔 시작
                    handler.postDelayed(this, interval);  // 지정된 간격마다 실행
                }
            }
        }, interval);
    }

    // Wi-Fi 스캔 시작 메서드
    private void startWiFiScan() {
        startTime = System.currentTimeMillis();  // 스캔 시작 시간 기록
        wifiManager.startScan();  // Wi-Fi 스캔 요청
    }

    // Wi-Fi 데이터를 선택한 디렉토리에 저장하는 메서드
    private void saveWiFiDataToSelectedDirectory() {
        if (directoryUri == null) {
            Toast.makeText(this, "Please select a directory first", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 현재 접근 가능한 모든 Wi-Fi 네트워크 목록 가져오기
            List<ScanResult> wifiScanResults = wifiManager.getScanResults();

            // 데이터가 없을 경우
            if (wifiScanResults == null || wifiScanResults.isEmpty()) {
                Toast.makeText(this, "No Wi-Fi networks found", Toast.LENGTH_SHORT).show();
                return;
            }

            wifiList.clear();  // Wi-Fi 목록 초기화
            StringBuilder wifiData = new StringBuilder();

            // 첫 줄에 각 열의 설명을 MATLAB 스타일 주석으로 추가
            wifiData.append("% SSID, BSSID, RSSI (dBm), Frequency (MHz), Capabilities, Channel Width (MHz), Center Frequency 0 (MHz), Center Frequency 1 (MHz), Timestamp\n");

            for (ScanResult wifi : wifiScanResults) {
                String wifiInfo = wifi.SSID + "," + wifi.BSSID + "," + wifi.level + " dBm" + "," + wifi.frequency + " MHz" + "," + wifi.capabilities + "," + wifi.channelWidth + " MHz" + "," + wifi.centerFreq0 + " MHz" + "," + wifi.centerFreq1 + " MHz" + "," + wifi.timestamp;
                wifiList.add(wifiInfo);
                wifiData.append(wifiInfo).append("\n");
            }

            // RecyclerView에 업데이트된 Wi-Fi 데이터 반영
            wifiAdapter.updateWiFiList(wifiList);

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "wifidata_" + timeStamp + ".txt";

            // 선택한 디렉토리에 파일 저장
            DocumentFile pickedDir = DocumentFile.fromTreeUri(this, directoryUri);
            DocumentFile newFile = pickedDir.createFile("text/plain", fileName);

            // OutputStream을 통해 파일에 데이터를 씀
            OutputStreamWriter writer = new OutputStreamWriter(getContentResolver().openOutputStream(newFile.getUri()));
            writer.write(wifiData.toString());
            writer.close();

            Toast.makeText(this, "Wi-Fi data saved to: " + newFile.getUri().getPath(), Toast.LENGTH_SHORT).show();
            filePathTextView.setText("File saved at: " + newFile.getUri().getPath());
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save Wi-Fi data", Toast.LENGTH_SHORT).show();
        }
    }

    // 주기적 실행을 중지하는 메서드
    private void stopPeriodicSaving() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);  // 모든 핸들러 콜백 제거
        Toast.makeText(this, "Stopped saving Wi-Fi data", Toast.LENGTH_SHORT).show();
    }
}