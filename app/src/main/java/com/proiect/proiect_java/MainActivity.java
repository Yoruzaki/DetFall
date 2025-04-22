package com.proiect.proiect_java;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.proiect.proiect_java.databinding.ActivityMainBinding;

import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private ActivityMainBinding binding;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private FallDetectionThread fallDetectionThread;

    // Graph variables
    private LineGraphSeries<DataPoint> accelerationSeries;
    private static final int MAX_GRAPH_POINTS = 100;
    private int graphPointIndex = 0;

    // Popup and countdown
    private PopupWindow popupWindow;
    private boolean isPopupShowing = false;
    private CountDownTimer countDownTimer;

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize fall detection thread
        fallDetectionThread = new FallDetectionThread();
        fallDetectionThread.start();

        // Initialize sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Initialize graph
        initializeGraph();

        // Button listeners
        binding.button.setOnClickListener(v -> resetFallDetection());
        binding.testFallButton.setOnClickListener(v -> simulateFall());
        binding.calibrateButton.setOnClickListener(v -> startCalibration());

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        setupLocationUpdates();

        // Check permissions
        checkPermissions();
    }

    private void initializeGraph() {
        GraphView graph = binding.idGraphView;
        accelerationSeries = new LineGraphSeries<>();
        graph.addSeries(accelerationSeries);

        // Configure graph viewport
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(MAX_GRAPH_POINTS);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(0);
        graph.getViewport().setMaxY(20);

        graph.getGridLabelRenderer().setHorizontalAxisTitle("Time");
        graph.getGridLabelRenderer().setVerticalAxisTitle("Acceleration (m/s²)");
    }

    private void simulateFall() {
        new Thread(() -> {
            // Free fall
            fallDetectionThread.updateAcceleration(0.5); // Simulate free fall
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // Impact
            fallDetectionThread.updateAcceleration(15.0);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // After impact
            fallDetectionThread.updateAcceleration(2.0);
        }).start();
    }

    private void startCalibration() {
        Toast.makeText(this, "Calibration started. Please move normally for 10 seconds.", Toast.LENGTH_LONG).show();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        } else {
            startLocationUpdates();
        }
    }

    private void setupLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    fallDetectionThread.updateLocation(location.getLatitude(), location.getLongitude());
                    updateLocationUI(location.getLatitude(), location.getLongitude());
                }
            }
        };
    }

    private void updateLocationUI(double latitude, double longitude) {
        runOnUiThread(() -> {
            binding.locationTextView.setText(String.format(Locale.getDefault(),
                    "Location: %.4f, %.4f", latitude, longitude));
        });
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(
                    LocationRequest.create(),
                    locationCallback,
                    null
            );
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            double magnitude = Math.sqrt(
                    Math.pow(event.values[0], 2) +
                            Math.pow(event.values[1], 2) +
                            Math.pow(event.values[2], 2));

            fallDetectionThread.updateAcceleration(magnitude);
            updateUI(magnitude);
        }
    }

    private void updateUI(double acceleration) {
        runOnUiThread(() -> {
            binding.textView.setText(String.format(Locale.getDefault(), "Acceleration: %.2f m/s²", acceleration));
            binding.stateTextView.setText("State: " + getStateName(fallDetectionThread.getDetectionState()));

            accelerationSeries.appendData(new DataPoint(graphPointIndex++, acceleration), true, MAX_GRAPH_POINTS);

            handleStateChange(fallDetectionThread.getDetectionState());
        });
    }

    private String getStateName(int state) {
        switch (state) {
            case FallDetectionThread.STATE_NORMAL: return "Normal";
            case FallDetectionThread.STATE_FREE_FALL: return "Free Fall";
            case FallDetectionThread.STATE_IMPACT: return "Impact";
            case FallDetectionThread.STATE_RECOVERY: return "Recovery";
            case FallDetectionThread.STATE_ALERT: return "Alert";
            case FallDetectionThread.STATE_EMERGENCY: return "Emergency";
            default: return "Unknown";
        }
    }

    private void handleStateChange(int detectionState) {
        if (detectionState == FallDetectionThread.STATE_FREE_FALL && !isPopupShowing) {
            showFallPopup();
        } else if (detectionState == FallDetectionThread.STATE_EMERGENCY) {
            sendEmergencyAlert();
            resetFallDetection();
        }
    }

    private void showFallPopup() {
        runOnUiThread(() -> {
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            View popupView = inflater.inflate(R.layout.popup_window, null);

            popupWindow = new PopupWindow(popupView,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    true);

            TextView countdownText = popupView.findViewById(R.id.countdownText);
            TextView messageText = popupView.findViewById(R.id.messageText);
            messageText.setText("Possible fall detected! Are you okay?");

            popupWindow.showAtLocation(binding.getRoot(), Gravity.CENTER, 0, 0);

            Button dismissButton = popupView.findViewById(R.id.button2);
            dismissButton.setOnClickListener(v -> {
                cancelEmergencyAlert();
                fallDetectionThread.setState(FallDetectionThread.STATE_NORMAL);
            });

            startCountdown(countdownText);
            isPopupShowing = true;
        });
    }

    private void startCountdown(TextView countdownText) {
        countDownTimer = new CountDownTimer(5000, 1000) {
            public void onTick(long millisUntilFinished) {
                countdownText.setText("Emergency alert in: " + millisUntilFinished / 1000);
            }

            public void onFinish() {
                if (isPopupShowing) {
                    fallDetectionThread.setState(FallDetectionThread.STATE_EMERGENCY);
                }
            }
        }.start();
    }

    private void cancelEmergencyAlert() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        if (popupWindow != null) {
            popupWindow.dismiss();
        }
        isPopupShowing = false;
        Toast.makeText(this, "Thank you for confirming you're okay", Toast.LENGTH_SHORT).show();
    }

    private void sendEmergencyAlert() {
        runOnUiThread(() -> {
            if (popupWindow != null) {
                popupWindow.dismiss();
            }
            isPopupShowing = false;

            String message = String.format(Locale.getDefault(),
                    "Emergency alert sent! Location: %.4f, %.4f",
                    fallDetectionThread.getLatitude(),
                    fallDetectionThread.getLongitude());

            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            resetFallDetection();
        });
    }

    private void resetFallDetection() {
        fallDetectionThread.reset();
        graphPointIndex = 0;
        accelerationSeries.resetData(new DataPoint[0]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        } else {
            Toast.makeText(this, "Location permission required for emergency alerts", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        if (locationCallback != null) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        fallDetectionThread.interrupt();
    }
}