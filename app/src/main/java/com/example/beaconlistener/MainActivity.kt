package com.example.beaconlistener

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat

class MainActivity : ComponentActivity() {
    private lateinit var beaconScanner: BeaconScanner
    private var beaconState = mutableStateOf("Esperando...")
    private var isScanning = mutableStateOf(false)
    private var permissionsGranted = mutableStateOf(false)
    private var adapterInfo = mutableStateOf("Adapter: No detectado")

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        permissionsGranted.value = allGranted

        if (allGranted) {
            Log.d("MainActivity", "All permissions granted")
            // Update adapter info when permissions are granted
            updateAdapterInfo()
            // Automatically start scanning once permissions are granted
            beaconScanner.startScanning()
            isScanning.value = true
        } else {
            Log.e("MainActivity", "Not all permissions granted")
            beaconState.value = "Permisos requeridos no otorgados"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        beaconScanner = BeaconScanner(this) { beacon ->
            runOnUiThread {
                Log.d("MainActivity", "Beacon detectado: ${beacon.uuid}, Major: ${beacon.major}, Minor: ${beacon.minor}, Distancia: ${String.format("%.2f", beacon.distance)}m, Adapter: ${beacon.adapterName}")
                if(beacon.deviceName == "JJ"){
                    beaconState.value = "UUID: ${beacon.uuid}\nMajor: ${beacon.major}, Minor: ${beacon.minor}\nDistancia: ${String.format("%.2f", beacon.distance)}m\nRSSI: ${beacon.rssi} dBm\nAdapter: ${beacon.adapterName ?: "Unknown"}\nDevice: ${beacon.deviceName ?: "Unknown"}"
                }
            }
        }

        // Check and request permissions
        checkAndRequestPermissions()

        setContent {
            BeaconScannerScreen(
                beaconText = beaconState.value,
                adapterInfo = adapterInfo.value,
                isScanning = isScanning.value,
                permissionsGranted = permissionsGranted.value,
                onStartScan = {
                    if (permissionsGranted.value) {
                        beaconScanner.startScanning()
                        isScanning.value = true
                        beaconState.value = "Escaneando beacons..."
                    } else {
                        checkAndRequestPermissions()
                    }
                },
                onStopScan = {
                    beaconScanner.stopScanning()
                    isScanning.value = false
                    beaconState.value = "Escaneo detenido"
                },
                onRequestPermissions = { checkAndRequestPermissions() }
            )
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        val permissionsToRequest = permissions.filter { permission ->
            ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            permissionsGranted.value = true
            updateAdapterInfo()
            Log.d("MainActivity", "All permissions already granted")
        } else {
            Log.d("MainActivity", "Requesting permissions: ${permissionsToRequest.joinToString()}")
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun updateAdapterInfo() {
        val adapterName = beaconScanner.getAdapterName()
        adapterInfo.value = "Adapter: ${adapterName ?: "Unknown"}"
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isScanning.value) {
            beaconScanner.stopScanning()
        }
    }
}

@Composable
fun BeaconScannerScreen(
    beaconText: String,
    adapterInfo: String,
    isScanning: Boolean,
    permissionsGranted: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Beacon Scanner")
        Spacer(modifier = Modifier.height(16.dp))

        // Mostrar información del adapter
        Text(text = adapterInfo)
        Spacer(modifier = Modifier.height(8.dp))

        Text(text = beaconText)
        Spacer(modifier = Modifier.height(24.dp))

        if (!permissionsGranted) {
            Button(onClick = onRequestPermissions) {
                Text(text = "Solicitar Permisos")
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onStartScan,
                    enabled = !isScanning
                ) {
                    Text(text = if (isScanning) "Escaneando..." else "Iniciar Escaneo")
                }

                Button(
                    onClick = onStopScan,
                    enabled = isScanning
                ) {
                    Text(text = "Detener Escaneo")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Estado: ${if (permissionsGranted) "Permisos OK" else "Permisos requeridos"}",
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}