package kz.phoenix.mockgps

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class MockLocationService : Service() {

    companion object {
        const val ACTION_STOP = "kz.phoenix.mockgps.ACTION_STOP"
        private const val CHANNEL_ID = "mock_gps_channel"
        private const val NOTIFICATION_ID = 1
        private const val TICK_INTERVAL_MS = 500L
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var locationManager: LocationManager
    private lateinit var fused: FusedLocationProviderClient

    private val handler = Handler(Looper.getMainLooper())
    private var providersAdded = false
    private var tickerRunning = false

    private var currentActive = 0
    private var currentName = ""

    private val tickerRunnable = object : Runnable {
        override fun run() {
            tick()
            if (tickerRunning) {
                handler.postDelayed(this, TICK_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.prefs(this)
        locationManager = getSystemService(LocationManager::class.java)
        fused = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs.setActive(prefs, Prefs.INACTIVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val active = Prefs.activePreset(prefs)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(active?.name.orEmpty()),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
            if (active?.coordinates == null) {
                Prefs.setActive(prefs, Prefs.INACTIVE)
                stopSelf()
                return START_NOT_STICKY
            }
            if (!providersAdded) {
                providersAdded = true
                addProviders()
                runCatching { fused.setMockMode(true) }
            }
            startTicker()
        } catch (e: SecurityException) {
            handleSecurityException()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tickerRunning = false
        handler.removeCallbacks(tickerRunnable)
        if (providersAdded) {
            removeProviders()
            try {
                fused.setMockMode(false)
            } catch (_: SecurityException) {
                // Revoking the mock-location role can stop this service.
            }
        }
        super.onDestroy()
    }

    private fun startTicker() {
        if (tickerRunning) return
        tickerRunning = true
        handler.post(tickerRunnable)
    }

    private fun tick() {
        val active = Prefs.activePreset(prefs)
        val coordinates = active?.coordinates
        if (active == null || coordinates == null) {
            Prefs.setActive(prefs, Prefs.INACTIVE)
            stopSelfCleanup()
            return
        }

        try {
            feedLocation(coordinates.latitude, coordinates.longitude)
            if (active.id != currentActive || active.name != currentName) {
                currentActive = active.id
                currentName = active.name
                updateNotification(active.name)
            }
        } catch (_: SecurityException) {
            handleSecurityException()
        }
    }

    // tick handles permission revocation for both the platform and fused providers.
    @SuppressLint("MissingPermission")
    private fun feedLocation(lat: Double, lon: Double) {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtimeNanos()

        for (providerName in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            val location = Location(providerName).apply {
                latitude = lat
                longitude = lon
                altitude = 0.0
                accuracy = 3f
                speed = 0f
                bearing = 0f
                time = now
                elapsedRealtimeNanos = elapsed
                verticalAccuracyMeters = 3f
                speedAccuracyMetersPerSecond = 0f
                bearingAccuracyDegrees = 0f
            }
            locationManager.setTestProviderLocation(providerName, location)
        }

        val fusedLocation = Location("fused").apply {
            latitude = lat
            longitude = lon
            altitude = 0.0
            accuracy = 3f
            speed = 0f
            bearing = 0f
            time = now
            elapsedRealtimeNanos = elapsed
            verticalAccuracyMeters = 3f
            speedAccuracyMetersPerSecond = 0f
            bearingAccuracyDegrees = 0f
        }
        fused.setMockLocation(fusedLocation)
    }

    @Suppress("DEPRECATION")
    private fun addProviders() {
        for (providerName in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            runCatching { locationManager.removeTestProvider(providerName) }
            locationManager.addTestProvider(
                providerName,
                false, false, false, false, true, true, true,
                ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(providerName, true)
        }
    }

    private fun removeProviders() {
        for (providerName in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            runCatching { locationManager.setTestProviderEnabled(providerName, false) }
            runCatching { locationManager.removeTestProvider(providerName) }
        }
        providersAdded = false
    }

    private fun handleSecurityException() {
        Toast.makeText(
            applicationContext,
            "Приложение снято с роли mock-локации. Подмена остановлена.",
            Toast.LENGTH_LONG
        ).show()
        Prefs.setActive(prefs, Prefs.INACTIVE)
        stopSelfCleanup()
    }

    private fun stopSelfCleanup() {
        tickerRunning = false
        handler.removeCallbacks(tickerRunnable)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mock GPS",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(activePointName: String): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MockLocationService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (activePointName.isEmpty()) "Подмена активна" else "Подмена: $activePointName"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mock GPS")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .addAction(0, "Стоп", stopPendingIntent)
            .build()
    }

    private fun updateNotification(activePointName: String) {
        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) {
            manager.notify(NOTIFICATION_ID, buildNotification(activePointName))
        }
    }
}
