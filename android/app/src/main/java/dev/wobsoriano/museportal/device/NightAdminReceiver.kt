package dev.wobsoriano.museportal.device

import android.app.admin.DeviceAdminReceiver

/**
 * The device admin whose only power is force-lock — it lets the night schedule turn the
 * display truly off. It asks for nothing else and does nothing on its own events.
 */
class NightAdminReceiver : DeviceAdminReceiver()
