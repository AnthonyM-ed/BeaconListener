package com.example.beaconlistener

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import java.nio.ByteBuffer
import java.util.UUID

data class IBeacon(
    val uuid: String,
    val major: Int,
    val minor: Int,
    val txPower: Int,
    val rssi: Int,
    val distance: Double,
    val deviceName: String?,
    val adapterName: String? // Agregado para mostrar el nombre del adapter
)

class BeaconScanner(
    private val context: Context,
    private val onBeaconDetected: (beacon: IBeacon) -> Unit
) {
    private val TAG = "BeaconScanner"
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isScanning = false

    fun startScanning() {
        if (isScanning) {
            Log.d(TAG, "Already scanning")
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            return
        }

        // Verificar permisos
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Required permissions not granted")
            return
        }

        // Log del adapter info
        logAdapterInfo()

        bluetoothLeScanner = bluetoothAdapter!!.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "bluetoothLeScanner is null")
            return
        }

        val scanFilter = ScanFilter.Builder()
            //.setManufacturerData(76, byteArrayOf(0x02, 0x15)) // Apple iBeacon prefix
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .build()

        try {
            // Permisos
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "BLUETOOTH_SCAN denied!")
                    return
                }
            }

            bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            isScanning = true
            Log.d(TAG, "Started scanning for iBeacons...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scanning: ${e.message}")
        }
    }

    fun stopScanning() {
        if (!isScanning) {
            Log.d(TAG, "Not scanning")
            return
        }

        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Required permissions not granted")
            return
        }

        try {
            // Permisos
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "BLUETOOTH_SCAN denied!")
                    return
                }
            }

            bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            Log.d(TAG, "Stopped scanning")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop scanning: ${e.message}")
        }
    }

    private fun logAdapterInfo() {
        bluetoothAdapter?.let { adapter ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "BLUETOOTH_CONNECT denied!")
                        return
                    }
                }

                val adapterName = adapter.name ?: "Unknown"
                Log.d(TAG, "Bluetooth Adapter Name: $adapterName")
                Log.d(TAG, "Bluetooth Adapter Address: ${adapter.address}")
                Log.d(TAG, "LE Max Advertising Data Length: ${adapter.leMaximumAdvertisingDataLength}")
                Log.d(TAG, "LE 2M PHY Supported: ${adapter.isLe2MPhySupported}")
                Log.d(TAG, "LE Extended Advertising Supported: ${adapter.isLeExtendedAdvertisingSupported}")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting adapter info: ${e.message}")
            }
        }
    }

    fun getAdapterName(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return "Permission denied"
                }
            }
            bluetoothAdapter?.name ?: "Unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting adapter name: ${e.message}")
            "Error"
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { parseScanResult(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { parseScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
            when (errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> Log.e(TAG, "SCAN_FAILED_ALREADY_STARTED")
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> Log.e(TAG, "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED")
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> Log.e(TAG, "SCAN_FAILED_FEATURE_UNSUPPORTED")
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> Log.e(TAG, "SCAN_FAILED_INTERNAL_ERROR")
                ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> Log.e(TAG, "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES")
                ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> Log.e(TAG, "SCAN_FAILED_SCANNING_TOO_FREQUENTLY")
            }
            isScanning = false
        }
    }

    private fun parseScanResult(result: ScanResult) {
        val scanRecord = result.scanRecord ?: return
        val manufacturerData = scanRecord.getManufacturerSpecificData(76) // Apple manufacturer ID

        if (manufacturerData != null && manufacturerData.size >= 23) {
            // Verificar que es un iBeacon (debe empezar con 0x02, 0x15)
            if (manufacturerData[0] == 0x02.toByte() && manufacturerData[1] == 0x15.toByte()) {
                try {
                    // Extraer UUID (16 bytes)
                    val uuidBytes = manufacturerData.copyOfRange(2, 18)
                    val uuidStr = formatUuidString(uuidBytes)

                    // Extraer Major (2 bytes)
                    val major = ByteBuffer.wrap(manufacturerData, 18, 2).short.toInt() and 0xFFFF

                    // Extraer Minor (2 bytes)
                    val minor = ByteBuffer.wrap(manufacturerData, 20, 2).short.toInt() and 0xFFFF

                    // Extraer TX Power (1 byte, signed)
                    val txPower = manufacturerData[22].toInt()

                    // RSSI del dispositivo
                    val rssi = result.rssi

                    // Calcular distancia aproximada
                    val distance = calculateDistance(txPower, rssi)

                    // Nombre del dispositivo (si está disponible)
                    val deviceName = scanRecord.deviceName

                    // Obtener el nombre del adapter
                    val adapterName = getAdapterName()

                    val beacon = IBeacon(
                        uuid = uuidStr,
                        major = major,
                        minor = minor,
                        txPower = txPower,
                        rssi = rssi,
                        distance = distance,
                        deviceName = deviceName,
                        adapterName = adapterName
                    )

                    Log.d(TAG, "iBeacon detected: UUID=$uuidStr, Major=$major, Minor=$minor, RSSI=$rssi, Distance=${String.format("%.2f", distance)}m, AdapterName=$adapterName")

                    onBeaconDetected(beacon)

                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing iBeacon data: ${e.message}")
                }
            }
        }
    }

    private fun formatUuidString(uuidBytes: ByteArray): String {
        val hex = uuidBytes.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    private fun calculateDistance(txPower: Int, rssi: Int): Double {
        if (rssi == 0) return -1.0

        val ratio = (txPower - rssi) / 20.0
        return if (ratio < 1.0) {
            Math.pow(ratio, 10.0)
        } else {
            val accuracy = (0.89976) * Math.pow(ratio, 7.7095) + 0.111
            accuracy
        }
    }

    fun isScanning(): Boolean = isScanning
}