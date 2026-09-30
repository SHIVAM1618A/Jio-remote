package com.example.jioremote

import java.io.ByteArrayOutputStream

/**
 * JioHome BT packet framing, reverse-engineered from the JioHome APK.
 *
 * Wire frame (best-effort reconstruction — see README "Known gaps"):
 *   [2-byte length, big-endian][1-byte encFlag (0 = no encryption)][payload]
 * payload = [1-byte BTPacketType ordinal][type-specific fields]
 *
 * Transport: classic Bluetooth SPP (RFCOMM), UUID 00001101-0000-1000-8000-00805F9B34FB
 */
object BTPacketType {
    const val CONNECTION = 1
    const val CONNECTION_ACK = 2
    const val REMOTE_EVENT = 7
    const val VOLUME_UPDATE = 13
    const val SOFT_KEYBOARD = 18
    const val PAIRING_REQUEST_PIN = 57
}

/** Standard Android KeyEvent.KEYCODE_* values used by JioHome's REMOTE_EVENT packet. */
object KeyCode {
    const val HOME = 3
    const val BACK = 4
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23 // OK
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val POWER = 26
    const val MUTE = 164
}

object BTPacket {

    private fun frame(payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val len = payload.size
        out.write((len shr 8) and 0xFF)
        out.write(len and 0xFF)
        out.write(0) // encFlag = 0 -> unencrypted (DEFAULT). See README gap notes.
        out.write(payload)
        return out.toByteArray()
    }

    /** REMOTE_EVENT: type(1) + keyCode(short, 2 bytes BE) + action(int, 4 bytes BE) */
    fun remoteEvent(keyCode: Int, action: Int = 0): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(BTPacketType.REMOTE_EVENT)
        payload.write((keyCode shr 8) and 0xFF)
        payload.write(keyCode and 0xFF)
        payload.write((action shr 24) and 0xFF)
        payload.write((action shr 16) and 0xFF)
        payload.write((action shr 8) and 0xFF)
        payload.write(action and 0xFF)
        return frame(payload.toByteArray())
    }

    /** PAIRING_REQUEST_PIN: type(1) + event(int, 4 bytes BE) + text (raw UTF-8 bytes) */
    fun pairingPin(event: Int, text: String): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(BTPacketType.PAIRING_REQUEST_PIN)
        payload.write((event shr 24) and 0xFF)
        payload.write((event shr 16) and 0xFF)
        payload.write((event shr 8) and 0xFF)
        payload.write(event and 0xFF)
        payload.write(text.toByteArray(Charsets.UTF_8))
        return frame(payload.toByteArray())
    }

    /** SOFT_KEYBOARD: type(1) + text (raw UTF-8 bytes, no length prefix) */
    fun softKeyboard(text: String): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(BTPacketType.SOFT_KEYBOARD)
        payload.write(text.toByteArray(Charsets.UTF_8))
        return frame(payload.toByteArray())
    }
}
