package com.example.torchvol

import android.accessibilityservice.AccessibilityService
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class TorchService : AccessibilityService() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var cm: CameraManager
    private lateinit var am: AudioManager
    private var camId: String? = null
    private var on = false
    private var downAt = 0L
    private var held = false
    private var pendingTap = false
    private val lowerTask = Runnable { pendingTap = false; lower() }

    override fun onServiceConnected() {
        cm = getSystemService(CameraManager::class.java)
        am = getSystemService(AudioManager::class.java)
        camId = cm.cameraIdList.firstOrNull {
            val c = cm.getCameraCharacteristics(it)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
        cm.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) {
                if (id == camId) on = enabled
            }
        }, h)
    }

    private fun lower() = am.adjustSuggestedStreamVolume(
        AudioManager.ADJUST_LOWER, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)

    private fun toggle() {
        camId?.let { try { cm.setTorchMode(it, !on) } catch (_: Exception) {} }
    }

    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (e.action == KeyEvent.ACTION_DOWN) {
            if (e.repeatCount == 0) { held = false; downAt = e.eventTime }
            else { held = true; lower() }
            return true
        }
        if (held || e.eventTime - downAt > 400) {
            if (!held) lower()
            return true
        }
        if (pendingTap) { h.removeCallbacks(lowerTask); pendingTap = false; toggle() }
        else { pendingTap = true; h.postDelayed(lowerTask, 350) }
        return true
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}