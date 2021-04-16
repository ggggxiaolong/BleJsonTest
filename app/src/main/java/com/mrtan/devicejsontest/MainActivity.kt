package com.mrtan.devicejsontest

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothAdapter.getDefaultAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelUuid
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.mrtan.devicejsontest.service.GattWithState
import com.mrtan.devicejsontest.service.JsonData
import com.mrtan.devicejsontest.service.hex2ByteArray
import com.mrtan.devicejsontest.service.toHex
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import halo.android.permission.HaloPermission
import timber.log.Timber
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList


@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {
    private val bluetoothAdapter: BluetoothAdapter = getDefaultAdapter()
    private val gattStateMap = ConcurrentHashMap<String, GattWithState>()
    private val lock = Any()
    private val SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val P_SERVICE_UUID = ParcelUuid.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    private val WRITE_CHARACATER_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val NOTIFY_CHARACATER_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val NOTIFY_DESCRIPT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val infoText: TextView by lazy { findViewById(R.id.info) }
    private val finger = "04145e012dcab5e843ab01f829bab6e581c8fff826a2b80103bbbff892b2b6e943b985f8baa2b671c339f617c612b80482ba45f8d6aab5e683c805e00a1bb70044ba47e02673b4df82f943f8aa33b5e743f805e0a6ebb56dc348781719acb608078845e746acb7818537340071b4b81843a9bfee7accb4e204f8c5e77954b70f8587c5efc56cb5ea05f7c9e7cd0cb6098676c1d74d73b670844978177e33b6e943b947e800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000031445143145332440442a12a6481286243095415258334261517455632342a9145794a5502000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000003562".hex2ByteArray()
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            val device = result.device
            val uuids = result.scanRecord?.serviceUuids ?: emptyList()
            Timber.i("scan uuids: ${uuids.toString()}")
            if (uuids.contains(P_SERVICE_UUID)) {
                Timber.i("found device %s:%s", device.name, device.address)
                Timber.i("is connect: %s", bluetoothAdapter.bondedDevices.contains(device))
                runOnUiThread {
                    displayMac = device.address
                    infoText.text = displayMac
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { Timber.i("onBatchScanResults ${it.device.name}, ${it.device.address}") }
        }
    }

    private var connectDeviceMac = ""
    private var displayMac = ""
    private var deviceMtu = 20

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Timber.plant(Timber.DebugTree())
        HaloPermission.with(this, Manifest.permission.ACCESS_FINE_LOCATION)
            .setRationaleRender("为了扫描蓝牙，请允许权限")
            .setSettingRender("为了扫描蓝牙，请在设置中打开位置权限")
            .run()
        findViewById<Button>(R.id.scan).setOnClickListener {
            if (checkP()) {
                scan()
            }
        }
        findViewById<Button>(R.id.connect).setOnClickListener {
            if (checkP() && displayMac.isNotEmpty()) {
                connect(displayMac)
            }
        }
        findViewById<Button>(R.id.current).setOnClickListener {
            if (checkP()) {
                if (connectDeviceMac.isNotEmpty()) {
                    sendData(connectDeviceMac, byteArrayOf(0x00, 0x01, 0x02, 0x03))
                } else {
                    shotToast("还没建立连接")
                }
            }
        }
        findViewById<Button>(R.id.mtu).setOnClickListener {
            if (checkP()) {
                if (connectDeviceMac.isNotEmpty()) {
                    gattStateMap[connectDeviceMac]?.gatt?.requestMtu(512)
                } else {
                    shotToast("还没建立连接")
                }
            }
        }
        findViewById<Button>(R.id.json).setOnClickListener {
            if (checkP()) {
                if (connectDeviceMac.isNotEmpty()) {

                } else {
                    shotToast("还没建立连接")
                }
            }
        }
    }

    private fun checkP(): Boolean {
        val isPermit = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!isPermit) {
            HaloPermission.with(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                .setRationaleRender("为了扫描蓝牙，请允许权限")
                .setSettingRender("为了扫描蓝牙，请在设置中打开位置权限")
                .run()
        }
        return isPermit
    }

    private fun scan() {
        val scanSetting = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val filters: ArrayList<ScanFilter> = ArrayList()
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
            Timber.w("cancel a scan")
        }
        Timber.i("start scan")
        bluetoothAdapter.bluetoothLeScanner.startScan(
            filters,
            scanSetting,
            scanCallback
        )
    }

    private fun stopScan() {
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private fun connect(address: String) {
        Timber.i("method %s", "connect")
        stopScan()
        if (gattStateMap.containsKey(address) && gattStateMap[address]?.isDisconnected() != true) return
        synchronized(lock) {
            if (gattStateMap.containsKey(address) && !gattStateMap[address]!!.isActive()) {
                val state = gattStateMap[address]!!
                if (state.isDisconnected()) {
                    // 使用旧的 连接
                    Timber.w("bug start reconnect")
                    gattStateMap[address]?.connect()
                    return
                }
            } else {
                gattStateMap.remove(address)
                Timber.w("bug start connect")
                val device = bluetoothAdapter.getRemoteDevice(address)
                val gatt = device.connectGatt(applicationContext, false, gattCallback) ?: return
                val old = gattStateMap.put(address, GattWithState.connecting(gatt))
                if (old != null) {
                    Timber.e("connect has old connect bug create new")
                    old.close()
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        // 连接状态修改
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            val address = gatt.device.address
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // 建立连接, 搜索服务
                    Timber.i("connected device: $address")
                    gattStateMap[address]?.connected()
                    if (gattStateMap[address]?.gatt != gatt) {
                        Timber.w("double gatt")
                        gatt.disconnect()
                        gatt.close()
                        return
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    // 断开连接
                    Timber.i("disconnect device: $address")
                    gattStateMap[address]?.close()
                    gattStateMap.remove(address)
                    connectDeviceMac = ""
                    deviceMtu = 20
                }
            }
        }

        /**
         * 发现服务
         */
        override fun onServicesDiscovered(
            gatt: BluetoothGatt,
            status: Int
        ) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    val sb = StringBuilder()
                    gatt.services.forEach {
                        sb.append("\nfound service UUID: ${it.uuid} \n")
                        sb.append("characteristics: (${it.characteristics.size})\n")
                        val cs = it.characteristics
                        cs.forEach { ch -> sb.append(" -- ${ch.uuid}\n") }
                    }
                    Timber.i(sb.toString())
                    // 获取 character receive ,获取 descriptor, 订阅 notification
                    val lockService = gatt.getService(SERVICE_UUID)
                    if (lockService != null) {
                        val characteristic = lockService.getCharacteristic(NOTIFY_CHARACATER_UUID)
                        val notifier = characteristic?.getDescriptor(NOTIFY_DESCRIPT_UUID)
                        if (notifier != null) {
                            gatt.setCharacteristicNotification(
                                lockService.getCharacteristic(NOTIFY_CHARACATER_UUID), true
                            )
                            notifier.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(notifier)
                            Timber.i("set notification success")
                        }
                    }
                }
                BluetoothGatt.GATT_FAILURE -> {
                    Timber.i("device connect fail: ${gatt.device.address}")
                    gattStateMap[gatt.device.address]?.close()
                    gattStateMap.remove(gatt.device.address)
                }
            }
        }

        // read callback
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == NOTIFY_CHARACATER_UUID) {
                dealResponse(characteristic.value, gatt.device, "read")
            }
        }

        // write callback
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == NOTIFY_CHARACATER_UUID) {
                dealResponse(characteristic.value, gatt.device, "write")
            }
        }

        // change callback
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == NOTIFY_CHARACATER_UUID) {
                dealResponse(characteristic.value, gatt.device, "change")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //todo可以发送数据了
                connectDeviceMac = gatt.device.address
                runOnUiThread {
                    infoText.text = "$connectDeviceMac 已连接"
                }
                shotToast("$connectDeviceMac 已连接")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            shotToast("${gatt?.device?.address} 协商MTU: $mtu")
            deviceMtu = mtu
        }
    }

    private fun shotToast(msg: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dealResponse(
        data: ByteArray,
        device: BluetoothDevice,
        tag: String
    ) {
        Timber.i("receive from device %s tag:%s, data:%s", device.address, tag, data.toHex())
        Timber.tag("device_log_receive").i(data.toHex())
        //todo
    }

    private fun sendData(address: String, data: ByteArray) {
//      Timber.i("_bridgeCallback sendData")
        val result = kotlin.runCatching {
            Timber.tag("device_log_send").i("%s, %s", Thread.currentThread().name, data.toHex())
            val gatt = gattStateMap[address]?.gatt ?: return
            val lockService = gatt.getService(SERVICE_UUID)
            if (lockService != null) {
                val writer = lockService.getCharacteristic(WRITE_CHARACATER_UUID)
                if (writer != null) {
                    writer.value = data
                    writer.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    gatt.writeCharacteristic(writer)
                }
            }
        }
        if (result.isFailure) {
            Timber.e("address: %s, data: %s", address, data.toHex())
        }
    }
}