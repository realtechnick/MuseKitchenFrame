package dev.wobsoriano.museportal.device

import android.content.Context
import dev.wobsoriano.muse.voice.Duplex
import dev.wobsoriano.museportal.setup.SetupProgress

/** The user's settings, in app-private preferences. The token never leaves this file except into the core's config. */
class Settings(context: Context) {
  private val prefs = context.getSharedPreferences("muse", Context.MODE_PRIVATE)

  var name: String
    get() = prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME
    set(value) = prefs.edit().putString(KEY_NAME, value.trim()).apply()

  /** Whether the user has chosen a name, rather than living with the default. */
  val named: Boolean
    get() = !prefs.getString(KEY_NAME, null).isNullOrBlank()

  var sdkToken: String
    get() = prefs.getString(KEY_TOKEN, null).orEmpty()
    set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

  /** The "Hey Muse" switch. */
  var wakeEnabled: Boolean
    get() = prefs.getBoolean(KEY_WAKE, false)
    set(value) = prefs.edit().putBoolean(KEY_WAKE, value).apply()

  /** Listen for a reply after every answer, not only after a question. */
  var keepListening: Boolean
    get() = prefs.getBoolean(KEY_KEEP_LISTENING, false)
    set(value) = prefs.edit().putBoolean(KEY_KEEP_LISTENING, value).apply()

  /** Announcement audio and spoken replies stay silent while this is on; cards still show. */
  var muted: Boolean
    get() = prefs.getBoolean(KEY_MUTED, false)
    set(value) = prefs.edit().putBoolean(KEY_MUTED, value).apply()

  /** The hub home screen's status header, set from Muse by display.set_status. */
  var hubHeader: String
    get() = prefs.getString(KEY_HUB_HEADER, null).orEmpty()
    set(value) = prefs.edit().putString(KEY_HUB_HEADER, value).apply()

  /** The hub home screen's status body, set from Muse by display.set_status. */
  var hubBody: String
    get() = prefs.getString(KEY_HUB_BODY, null).orEmpty()
    set(value) = prefs.edit().putString(KEY_HUB_BODY, value).apply()

  /** The recipe card on the hub panel (display.set_recipe), as the JSON it arrived in; empty shows the status. */
  var hubRecipeJson: String
    get() = prefs.getString(KEY_HUB_RECIPE, null).orEmpty()
    set(value) = prefs.edit().putString(KEY_HUB_RECIPE, value).apply()

  /** The frame sleeps on a schedule: screen off late at night, back on in the morning. */
  var nightEnabled: Boolean
    get() = prefs.getBoolean(KEY_NIGHT_ENABLED, false)
    set(value) = prefs.edit().putBoolean(KEY_NIGHT_ENABLED, value).apply()

  /** Minute of day the screen goes off (default 23:59). */
  var nightStartMin: Int
    get() = prefs.getInt(KEY_NIGHT_START, 23 * 60 + 59)
    set(value) = prefs.edit().putInt(KEY_NIGHT_START, value).apply()

  /** Minute of day the screen comes back (default 8:00). */
  var nightEndMin: Int
    get() = prefs.getInt(KEY_NIGHT_END, 8 * 60)
    set(value) = prefs.edit().putInt(KEY_NIGHT_END, value).apply()

  /** The frame is in its night window right now (persisted so a restart stays dark). */
  var nightAsleep: Boolean
    get() = prefs.getBoolean(KEY_NIGHT_ASLEEP, false)
    set(value) = prefs.edit().putBoolean(KEY_NIGHT_ASLEEP, value).apply()

  /** "Hey Muse" starts a realtime call with Muse instead of a voice note. Experimental. */
  var conversationMode: Boolean
    get() = prefs.getBoolean(KEY_CONVERSATION, false)
    set(value) = prefs.edit().putBoolean(KEY_CONVERSATION, value).apply()

  /** Half until a talk-over test shows the user's voice survives Muse's on the Portal. */
  var duplex: Duplex
    get() = prefs.getString(KEY_DUPLEX, null)?.let { s -> Duplex.entries.find { it.name == s } } ?: Duplex.Half
    set(value) = prefs.edit().putString(KEY_DUPLEX, value.name).apply()

  var setup: SetupProgress
    get() = prefs.getString(KEY_SETUP, null)?.let { s -> SetupProgress.entries.find { it.name == s } } ?: SetupProgress.NotStarted
    set(value) = prefs.edit().putString(KEY_SETUP, value.name).apply()

  /** The chosen TTS voice, or null for the engine's default. */
  var voice: String?
    get() = prefs.getString(KEY_VOICE, null)
    set(value) = prefs.edit().putString(KEY_VOICE, value).apply()

  var speechRate: Float
    get() = prefs.getFloat(KEY_RATE, 1f)
    set(value) = prefs.edit().putFloat(KEY_RATE, value).apply()

  private companion object {
    const val DEFAULT_NAME = "Portal"
    const val KEY_NAME = "name"
    const val KEY_TOKEN = "sdkToken"
    const val KEY_WAKE = "wakeEnabled"
    const val KEY_KEEP_LISTENING = "keepListening"
    const val KEY_MUTED = "muted"
    const val KEY_HUB_HEADER = "hubHeader"
    const val KEY_HUB_BODY = "hubBody"
    const val KEY_HUB_RECIPE = "hubRecipe"
    const val KEY_NIGHT_ENABLED = "nightEnabled"
    const val KEY_NIGHT_START = "nightStartMin"
    const val KEY_NIGHT_END = "nightEndMin"
    const val KEY_NIGHT_ASLEEP = "nightAsleep"
    const val KEY_CONVERSATION = "conversationMode"
    const val KEY_DUPLEX = "duplex"
    const val KEY_SETUP = "setup"
    const val KEY_VOICE = "voice"
    const val KEY_RATE = "speechRate"
  }
}
