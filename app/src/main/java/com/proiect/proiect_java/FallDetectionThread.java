package com.proiect.proiect_java;

public class FallDetectionThread extends Thread {
    // State constants
    public static final int STATE_NORMAL = 0;
    public static final int STATE_FREE_FALL = 1;
    public static final int STATE_IMPACT = 2;
    public static final int STATE_RECOVERY = 3;
    public static final int STATE_ALERT = 4;
    public static final int STATE_EMERGENCY = 5;

    // Sensor values
    private double acceleration;
    private double minAccel = Double.MAX_VALUE;
    private double maxAccel = Double.MIN_VALUE;
    private double impactPeak = Double.MIN_VALUE;

    // Thresholds (adjusted for better detection)
    private static final double FREE_FALL_THRESHOLD = 0.8;  // m/s² (more sensitive)
    private static final double IMPACT_THRESHOLD = 12.0;    // m/s² (less strict)
    private static final double RECOVERY_THRESHOLD = 8.0;   // m/s²
    private static final double IMPACT_DURATION = 2000;      // ms
    private static final double RECOVERY_DURATION = 10000;   // ms

    // Timing
    private long stateStartTime;
    private int currentState = STATE_NORMAL;
    private double latitude;
    private double longitude;
    private final Object lock = new Object();

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            synchronized (lock) {
                processState();
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void processState() {
        switch (currentState) {
            case STATE_NORMAL: handleNormalState(); break;
            case STATE_FREE_FALL: handleFreeFallState(); break;
            case STATE_IMPACT: handleImpactState(); break;
            case STATE_RECOVERY: handleRecoveryState(); break;
            case STATE_ALERT: break; // Handled by MainActivity
            case STATE_EMERGENCY: break; // Handled by MainActivity
        }
    }

    private void handleNormalState() {
        if (acceleration < FREE_FALL_THRESHOLD) {
            if (acceleration < minAccel) {
                minAccel = acceleration;
            } else {
                currentState = STATE_FREE_FALL;
                maxAccel = Double.MIN_VALUE;
                stateStartTime = System.currentTimeMillis();
            }
        } else {
            minAccel = Double.MAX_VALUE;
        }
    }

    private void handleFreeFallState() {
        if (System.currentTimeMillis() - stateStartTime > IMPACT_DURATION) {
            currentState = STATE_NORMAL;
            minAccel = Double.MAX_VALUE;
        } else if (acceleration > maxAccel) {
            maxAccel = acceleration;
            if (maxAccel > IMPACT_THRESHOLD) {
                currentState = STATE_IMPACT;
                impactPeak = maxAccel;
                stateStartTime = System.currentTimeMillis();
            }
        }
    }

    private void handleImpactState() {
        if ((impactPeak - minAccel) > (IMPACT_THRESHOLD - FREE_FALL_THRESHOLD)) {
            currentState = STATE_RECOVERY;
            stateStartTime = System.currentTimeMillis();
        } else {
            currentState = STATE_NORMAL;
        }
    }

    private void handleRecoveryState() {
        long elapsed = System.currentTimeMillis() - stateStartTime;
        if (elapsed > RECOVERY_DURATION) {
            currentState = STATE_ALERT;
        } else if (acceleration > RECOVERY_THRESHOLD) {
            currentState = STATE_NORMAL;
        }
    }

    // Thread-safe methods
    public void updateAcceleration(double value) {
        synchronized (lock) { this.acceleration = value; }
    }

    public void updateLocation(double latitude, double longitude) {
        synchronized (lock) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    public void setState(int state) {
        synchronized (lock) {
            this.currentState = state;
            if (state == STATE_NORMAL) reset();
        }
    }

    public int getDetectionState() {
        synchronized (lock) { return currentState; }
    }

    public double getLatitude() {
        synchronized (lock) { return latitude; }
    }

    public double getLongitude() {
        synchronized (lock) { return longitude; }
    }

    public double getMinAccel() {
        synchronized (lock) { return minAccel; }
    }

    public double getMaxAccel() {
        synchronized (lock) { return maxAccel; }
    }

    public void reset() {
        synchronized (lock) {
            minAccel = Double.MAX_VALUE;
            maxAccel = Double.MIN_VALUE;
            impactPeak = Double.MIN_VALUE;
            if (currentState != STATE_EMERGENCY) {
                currentState = STATE_NORMAL;
            }
        }
    }
}