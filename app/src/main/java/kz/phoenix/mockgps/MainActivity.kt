package kz.phoenix.mockgps

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    private var active by mutableIntStateOf(0)
    private var name1 by mutableStateOf("")
    private var name2 by mutableStateOf("")
    private var coords1 by mutableStateOf("")
    private var coords2 by mutableStateOf("")
    private var lat1Raw by mutableStateOf("")
    private var lon1Raw by mutableStateOf("")
    private var lat2Raw by mutableStateOf("")
    private var lon2Raw by mutableStateOf("")
    private var point1Valid by mutableStateOf(false)
    private var point2Valid by mutableStateOf(false)

    private var hasLocationPermission by mutableStateOf(false)
    private var isMockApp by mutableStateOf(false)

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshFromPrefs()
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermissionStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.prefs(this)
        refreshFromPrefs()
        refreshPermissionStatus()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    MainScreen(
                        active = active,
                        name1 = name1,
                        name2 = name2,
                        coords1 = coords1,
                        coords2 = coords2,
                        lat1Raw = lat1Raw,
                        lon1Raw = lon1Raw,
                        lat2Raw = lat2Raw,
                        lon2Raw = lon2Raw,
                        point1Valid = point1Valid,
                        point2Valid = point2Valid,
                        hasLocationPermission = hasLocationPermission,
                        isMockApp = isMockApp,
                        onRequestPermission = { requestPermissions() },
                        onOpenMockSettings = { openMockLocationSettings() },
                        onToggle = { n, checked -> onToggle(n, checked) },
                        onSavePoint = { n, name, lat, lon -> savePoint(n, name, lat, lon) },
                        onResume = { refreshPermissionStatus() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onDestroy()
    }

    private fun refreshFromPrefs() {
        active = Prefs.active(prefs)
        name1 = Prefs.name(prefs, 1)
        name2 = Prefs.name(prefs, 2)
        coords1 = Prefs.coordsText(prefs, 1)
        coords2 = Prefs.coordsText(prefs, 2)
        lat1Raw = Prefs.latString(prefs, 1)
        lon1Raw = Prefs.lonString(prefs, 1)
        lat2Raw = Prefs.latString(prefs, 2)
        lon2Raw = Prefs.lonString(prefs, 2)
        point1Valid = Prefs.isPointValid(prefs, 1)
        point2Valid = Prefs.isPointValid(prefs, 2)
    }

    private fun refreshPermissionStatus() {
        hasLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        isMockApp = checkMockLocationApp()
    }

    private fun checkMockLocationApp(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun openMockLocationSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    }

    private fun onToggle(n: Int, checked: Boolean) {
        if (checked) {
            Prefs.setActive(prefs, n)
            ContextCompat.startForegroundService(this, Intent(this, MockLocationService::class.java))
        } else {
            if (active == n) {
                Prefs.setActive(prefs, 0)
                stopService(Intent(this, MockLocationService::class.java))
            }
        }
    }

    private fun savePoint(n: Int, name: String, lat: String, lon: String) {
        Prefs.savePoint(prefs, n, name, lat, lon)
    }
}

@Composable
private fun MainScreen(
    active: Int,
    name1: String,
    name2: String,
    coords1: String,
    coords2: String,
    lat1Raw: String,
    lon1Raw: String,
    lat2Raw: String,
    lon2Raw: String,
    point1Valid: Boolean,
    point2Valid: Boolean,
    hasLocationPermission: Boolean,
    isMockApp: Boolean,
    onRequestPermission: () -> Unit,
    onOpenMockSettings: () -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    onSavePoint: (Int, String, String, String) -> Unit,
    onResume: () -> Unit
) {
    LifecycleResumeEffect(Unit) {
        onResume()
        onPauseOrDispose { }
    }

    var dialogPoint by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (!hasLocationPermission) {
            Text("Нет разрешения на геолокацию")
            Button(onClick = onRequestPermission, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)) {
                Text("Выдать разрешение")
            }
        }

        if (!isMockApp) {
            Text("Выберите это приложение как mock-приложение в настройках разработчика")
            Button(onClick = onOpenMockSettings, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)) {
                Text("Открыть настройки разработчика")
            }
        }

        val switch1Enabled = active == 1 || (point1Valid && hasLocationPermission && isMockApp)
        val switch2Enabled = active == 2 || (point2Valid && hasLocationPermission && isMockApp)

        ListItem(
            headlineContent = { Text(name1) },
            supportingContent = { Text(coords1) },
            trailingContent = {
                Switch(
                    checked = active == 1,
                    onCheckedChange = { checked -> onToggle(1, checked) },
                    enabled = switch1Enabled
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { dialogPoint = 1 }
        )

        ListItem(
            headlineContent = { Text(name2) },
            supportingContent = { Text(coords2) },
            trailingContent = {
                Switch(
                    checked = active == 2,
                    onCheckedChange = { checked -> onToggle(2, checked) },
                    enabled = switch2Enabled
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { dialogPoint = 2 }
        )
    }

    val pointIndex = dialogPoint
    if (pointIndex != null) {
        val currentName = if (pointIndex == 1) name1 else name2
        val currentLat = if (pointIndex == 1) lat1Raw else lat2Raw
        val currentLon = if (pointIndex == 1) lon1Raw else lon2Raw
        PointDialog(
            initialName = currentName,
            initialLat = currentLat,
            initialLon = currentLon,
            onDismiss = { dialogPoint = null },
            onSave = { name, lat, lon ->
                onSavePoint(pointIndex, name, lat, lon)
                dialogPoint = null
            }
        )
    }
}
