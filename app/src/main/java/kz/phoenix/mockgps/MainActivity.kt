package kz.phoenix.mockgps

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat

class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var locationManager: LocationManager

    private var presets by mutableStateOf(emptyList<LocationPreset>())
    private var active by mutableStateOf<LocationPreset?>(null)
    private var devicePosition by mutableStateOf<Coordinates?>(null)
    private var lastLocationTime = 0L
    private var hasFinePermission by mutableStateOf(false)
    private var isMockApp by mutableStateOf(false)

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshFromPrefs()
    }
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onLocation(location)
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    // ComponentActivity handles results directly, without FragmentActivity.
    @SuppressLint("InvalidFragmentVersionForActivityResult")
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshLocationAccess()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = Prefs.prefs(this)
        locationManager = getSystemService(LocationManager::class.java)
        refreshFromPrefs()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        setContent {
            MockGpsTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        MainScreen(
                            presets = presets,
                            active = active,
                            position = active?.coordinates ?: devicePosition,
                            searchOrigin = devicePosition,
                            hasFinePermission = hasFinePermission,
                            isMockApp = isMockApp,
                            onRequestPermission = ::requestLocationPermission,
                            onOpenMockSettings = ::openMockLocationSettings,
                            onToggle = { id, checked ->
                                if (checked) startMocking { Prefs.setActive(prefs, id) }
                                else stopMocking()
                            },
                            onSave = { Prefs.savePreset(prefs, it) },
                            onDelete = { id ->
                                val wasActive = active?.id == id
                                Prefs.deletePreset(prefs, id)
                                if (wasActive) stopService(Intent(this@MainActivity, MockLocationService::class.java))
                            },
                            onTemporary = { coordinates ->
                                startMocking { Prefs.activateTemporary(prefs, coordinates) }
                            },
                            onStop = ::stopMocking
                        )
                    }
                }
            }
        }

    }

    override fun onResume() {
        super.onResume()
        refreshLocationAccess()
    }

    override fun onPause() {
        locationManager.removeUpdates(locationListener)
        super.onPause()
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onDestroy()
    }

    private fun refreshFromPrefs() {
        presets = Prefs.presets(prefs)
        active = Prefs.activePreset(prefs)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun refreshLocationAccess() {
        hasFinePermission = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        isMockApp = checkMockLocationApp()
        if (active != null && (!hasFinePermission || !isMockApp)) stopMocking()

        locationManager.removeUpdates(locationListener)
        if (!hasFinePermission && !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            devicePosition = null
            lastLocationTime = 0L
            return
        }

        val providers = if (hasFinePermission) {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        } else {
            listOf(LocationManager.NETWORK_PROVIDER)
        }
        for (provider in providers) {
            if (!locationManager.isProviderEnabled(provider)) continue
            try {
                locationManager.getLastKnownLocation(provider)?.let(::onLocation)
                locationManager.requestLocationUpdates(provider, 2_000L, 5f, locationListener)
            } catch (_: IllegalArgumentException) {
                // A provider may be unavailable on this device.
            } catch (_: SecurityException) {
                // Permission may have changed since the check above.
            }
        }
    }

    private fun onLocation(location: Location) {
        if (LocationCompat.isMock(location) || location.elapsedRealtimeNanos < lastLocationTime) return
        lastLocationTime = location.elapsedRealtimeNanos
        devicePosition = Coordinates(location.latitude, location.longitude)
    }

    private fun checkMockLocationApp(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestLocationPermission() {
        prefs.edit().putBoolean("location_permission_requested", true).apply()
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun openMockLocationSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    }

    private fun startMocking(activate: () -> Unit): Boolean {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || !checkMockLocationApp()) {
            refreshLocationAccess()
            return false
        }
        activate()
        return try {
            ContextCompat.startForegroundService(this, Intent(this, MockLocationService::class.java))
            true
        } catch (_: SecurityException) {
            onMockStartFailed()
        } catch (_: IllegalStateException) {
            onMockStartFailed()
        }
    }

    private fun onMockStartFailed(): Boolean {
        stopMocking()
        Toast.makeText(this, "Не удалось включить подмену", Toast.LENGTH_SHORT).show()
        return false
    }

    private fun stopMocking() {
        Prefs.setActive(prefs, Prefs.INACTIVE)
        stopService(Intent(this, MockLocationService::class.java))
    }
}
