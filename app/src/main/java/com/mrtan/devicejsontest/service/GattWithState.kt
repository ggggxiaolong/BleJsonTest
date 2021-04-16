package com.mrtan.devicejsontest.service

import android.bluetooth.BluetoothGatt

class GattWithState(
  private var state: Int,
  val gatt: BluetoothGatt
) {
  fun isConnected() = state == STATE_CONNECTED
  fun isConnecting() = state == STATE_CONNECTING
  fun isDisconnected() = state == STATE_DISCONNECTED
  fun isActive() = state != STATE_DEAD

  fun connect() {
    if (state == STATE_DEAD) return
    state = STATE_CONNECTING
    gatt.connect()
  }

  fun disconnect() {
    if (state == STATE_DEAD) return
    state = STATE_DISCONNECTED
    try {
      gatt.disconnect()
    } catch (t: Throwable){
      state = STATE_DEAD
    }
  }

  fun disconnected() {
    if (state == STATE_DEAD) return
    state = STATE_DISCONNECTED
  }

  fun close() {
    disconnect()
    gatt.close()
  }

  fun connected() {
    if (state == STATE_DEAD) return
    state = STATE_CONNECTED
  }

  companion object {
    private const val STATE_CONNECTING = 1
    private const val STATE_CONNECTED = 2
    private const val STATE_DISCONNECTED = 3
    private const val STATE_DEAD = 4

    fun connecting(gatt: BluetoothGatt) = GattWithState(STATE_CONNECTING, gatt)
    fun connected(gatt: BluetoothGatt) = GattWithState(STATE_CONNECTED, gatt)
    fun disconnected(gatt: BluetoothGatt) = GattWithState(STATE_DISCONNECTED, gatt)
  }
}