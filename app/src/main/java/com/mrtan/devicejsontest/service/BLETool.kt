@file:JvmName("BleTool")

package com.mrtan.devicejsontest.service

import org.apache.commons.codec.binary.Hex

fun Array<Byte>.toHex(): String {
  return String(Hex.encodeHex(toByteArray()))
}

fun ByteArray.toHex(): String {
  try {
    return String(Hex.encodeHex(this))
  } catch (t: Throwable) {
    t.printStackTrace()
  }
  return ""
}

fun String.hex2ByteArray(): ByteArray {
  return Hex.decodeHex(this.toCharArray())
}