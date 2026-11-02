package dev.wobsoriano.museportal.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.isSpecified
import dev.wobsoriano.museportal.MuseState
import dev.wobsoriano.museportal.ask.AskState
import dev.wobsoriano.museportal.avatar.AvatarSet
import dev.wobsoriano.muse.voice.CallPhase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** What the turn has to say: the question as heard and the answer so far, or why it failed. [live]: captions of a call, newest words last. */
data class TurnText(val heard: String, val answer: String, val failed: Boolean, val complete: Boolean = true, val live: Boolean = false)

val AskState.turnText: TurnText?
  get() =
      when (this) {
        is AskState.Heard -> TurnText(heard, "", false, complete = false)
        is AskState.Answered -> TurnText(heard, answer, false, complete).takeIf { heard.isNotEmpty() || answer.isNotEmpty() }
        is AskState.Failed -> TurnText("", reason, true)
        // Captions only while they are current: Muse's words as it says them, and what it heard while it thinks.
        is AskState.Calling ->
            when (phase) {
              CallPhase.Talking -> TurnText(heard, reply, false, live = true).takeIf { reply.isNotEmpty() }
              CallPhase.Thinking -> TurnText(heard, "", false).takeIf { heard.isNotEmpty() }
              else -> null
            }
        else -> null
      }

// The hub's bronze-and-gold dressing (Boss's design, approved 2026-11-02).
private val BronzeTop = Color(0xFF3F2A10)
private val BronzeBottom = Color(0xFF1E1306)
private val BronzeGlow = Color(0xFF8A6428)
private val GoldLine = Color(0xFFF0C66E)
private val WarmWhite = Color(0xFFFFF6E6)

/**
 * The hub home screen: a clock and the framed character on the left third, a status panel on
 * the right two-thirds. The panel shows Muse's standing status (display.set_status) at rest,
 * and the turn's captions while a question, an answer or a call is live. A pushed card still
 * takes the whole screen, clean. The controls live at the bottom of the left column, so no
 * button ever sits over the clock or the panel.
 */
@Composable
fun HomeScreen(
    state: MuseState,
    avatar: AvatarSet?,
    onTap: () -> Unit,
    onEndCall: () -> Unit,
    onSettings: () -> Unit,
    onDismissImage: () -> Unit = {},
    onReplayEntry: (Long) -> Unit = {},
    onToggleMute: () -> Unit = {},
    onStartCall: () -> Unit = {},
) {
  var showReplays by remember { mutableStateOf(false) }
  Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BronzeTop, BronzeBottom)))) {
    Box(
        Modifier.fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(BronzeGlow.copy(alpha = 0.32f), Color.Transparent),
                    center = Offset(240f, 120f),
                    radius = 1100f)))
    val pushed = state.shownImage
    if (pushed != null) {
      // An image Muse pushed covers the room screen until tapped away or replaced.
      Box(Modifier.fillMaxSize().background(Color.Black).clickable { onDismissImage() }, contentAlignment = Alignment.Center) {
        Image(pushed.asImageBitmap(), contentDescription = "Pushed image", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
      }
    } else {
      HubContent(
          state,
          avatar,
          onTap,
          onSettings,
          onReplay = { showReplays = true },
          onToggleMute = onToggleMute,
          onCallToggle = { if (state.ask is AskState.Calling) onEndCall() else onStartCall() })
    }
  }
  if (showReplays && state.shownImage == null) {
    ReplayDialog(state, onDismiss = { showReplays = false }, onReplayEntry = { id ->
      showReplays = false
      onReplayEntry(id)
    })
  }
}

@Composable
private fun HubContent(
    state: MuseState,
    avatar: AvatarSet?,
    onTap: () -> Unit,
    onSettings: () -> Unit,
    onReplay: () -> Unit,
    onToggleMute: () -> Unit,
    onCallToggle: () -> Unit,
) {
  Row(Modifier.fillMaxSize().padding(36.dp)) {
    Column(Modifier.weight(0.28f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
      ClockBlock()
      Spacer(Modifier.height(22.dp))
      AvatarFrame(state, avatar, onTap)
      Spacer(Modifier.height(18.dp))
      DeviceStatus(state)
      val left = state.recordingLeftS
      if (left != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Recording… ${left / 60}:${(left % 60).toString().padStart(2, '0')} left · tap to send",
            fontFamily = HubFont,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = Palette.Warn)
        Spacer(Modifier.height(8.dp))
        CapLine(state.recordingFraction ?: 0f)
      }
      Spacer(Modifier.weight(1f))
      ControlsRow(state, onSettings, onReplay, onToggleMute, onCallToggle)
    }
    Spacer(Modifier.width(30.dp))
    StatusPanel(state, Modifier.weight(0.72f).fillMaxHeight())
  }
}

/** The wall clock, centered over the portrait: big time, the weekday larger, then month and day. */
@Composable
private fun ClockBlock() {
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      now = System.currentTimeMillis()
      delay(1_000)
    }
  }
  val time = remember(now) { SimpleDateFormat("h:mm", Locale.US).format(Date(now)) }
  val day = remember(now) { SimpleDateFormat("EEEE", Locale.US).format(Date(now)) }
  val date = remember(now) { SimpleDateFormat("MMMM d", Locale.US).format(Date(now)) }
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(time, fontFamily = HubFont, fontWeight = FontWeight.Black, fontSize = 74.sp, color = Color.White)
    Text(day, fontFamily = HubFont, fontWeight = FontWeight.Black, fontSize = 36.sp, color = WarmWhite)
    Text(date, fontFamily = HubFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = WarmWhite.copy(alpha = 0.8f))
  }
}

/** The character in its gold-trimmed frame; tapping it asks, as ever. */
@Composable
private fun AvatarFrame(state: MuseState, avatar: AvatarSet?, onTap: () -> Unit) {
  val dim by animateFloatAsState(if (state.mood == Mood.Offline) 0.45f else 1f, tween(600), label = "offline")
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val side = maxWidth
    Box(
        Modifier.size(side)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(3.dp, GoldLine.copy(alpha = 0.8f), RoundedCornerShape(26.dp))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center) {
          Box(Modifier.alpha(dim)) { Character(state.mood, state.level, avatar, side) }
        }
  }
}

private val PrinterBlue = Color(0xFF60A5FA)

/**
 * Live device status under the portrait: the frame's own connection, then the printer.
 * The printer dot is softened state, not live link state (the PM230 only holds a link
 * while talked to): blue when the last check-in reached it, red only when a check-in
 * actually failed, dim until the first check-in has run.
 */
@Composable
private fun DeviceStatus(state: MuseState) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
    val online = state.online
    val dot by animateColorAsState(if (online) Palette.Good else Palette.Bad, label = "muse dot")
    StatusRow(dot, "Muse Frame · ${state.connectionLabel}", if (online) WarmWhite.copy(alpha = 0.8f) else Palette.Bad)
    val (pDot, pText, pColor) =
        when {
          !state.printerPaired -> Triple(WarmWhite.copy(alpha = 0.35f), "Printer Not Paired", WarmWhite.copy(alpha = 0.55f))
          state.printerReachable == true -> Triple(PrinterBlue, "Printer Connected · PM230", WarmWhite.copy(alpha = 0.8f))
          state.printerReachable == false -> Triple(Palette.Bad, "Printer Unreachable · PM230", Palette.Bad)
          else -> Triple(PrinterBlue.copy(alpha = 0.45f), "Printer Paired · PM230", WarmWhite.copy(alpha = 0.65f))
        }
    StatusRow(pDot, pText, pColor)
  }
}

@Composable
private fun StatusRow(dot: Color, text: String, textColor: Color) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
    Spacer(Modifier.width(10.dp))
    Text(text, fontFamily = HubFont, fontSize = 17.sp, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** The right-hand panel: captions while a turn is live, the standing status otherwise. */
@Composable
private fun StatusPanel(state: MuseState, modifier: Modifier) {
  Box(
      modifier
          .clip(RoundedCornerShape(30.dp))
          .background(Color.Black.copy(alpha = 0.28f))
          .border(1.5.dp, GoldLine.copy(alpha = 0.55f), RoundedCornerShape(30.dp))
          .padding(horizontal = 40.dp, vertical = 34.dp)) {
    val turn = state.ask.turnText
    val inTurn =
        turn != null || state.ask is AskState.Calling || state.ask is AskState.Recording || state.ask is AskState.Sending
    if (inTurn) PanelCaptions(state, turn) else StatusContent(state)
  }
}

@Composable
private fun StatusContent(state: MuseState) {
  Column(Modifier.fillMaxSize().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
    if (state.hubHeader.isNotBlank()) {
      Text(
          state.hubHeader.uppercase(),
          fontFamily = HubFont,
          fontWeight = FontWeight.Black,
          fontSize = 34.sp,
          letterSpacing = 5.sp,
          color = Color.White,
          textAlign = TextAlign.Center)
      Spacer(Modifier.height(14.dp))
      Box(Modifier.width(130.dp).height(3.dp).background(GoldLine))
      Spacer(Modifier.height(22.dp))
    }
    Text(
        state.hubBody.ifBlank { defaultStatusBody(state) },
        fontFamily = HubFont,
        fontSize = 26.sp,
        lineHeight = 39.sp,
        color = WarmWhite,
        textAlign = TextAlign.Center)
  }
}

private fun defaultStatusBody(state: MuseState): String =
    if (!state.online) {
      "Not connected right now.\nCheck the Wi-Fi, or open Settings with the gear below."
    } else {
      "All quiet in the kitchen.\n\nSay \u201CHey Muse\u201D, tap my portrait,\nor tap the green phone to talk."
    }

/** The turn's words in the panel: the question as heard, the phase line, and the answer's current page. */
@Composable
private fun PanelCaptions(state: MuseState, turn: TurnText?) {
  Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    if (turn != null && turn.heard.isNotEmpty()) {
      Text(
          "\u201C${turn.heard}\u201D",
          fontFamily = HubFont,
          fontSize = 21.sp,
          color = WarmWhite.copy(alpha = 0.65f),
          textAlign = TextAlign.Center,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.height(14.dp))
    }
    if (state.statusLine.isNotEmpty() && (turn == null || turn.answer.isEmpty())) {
      Text(state.statusLine, fontFamily = HubFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Color.White, textAlign = TextAlign.Center)
    }
    if (turn != null && turn.live) {
      RollingText(turn.answer, 7, TextStyle(fontFamily = HubFont, fontSize = 30.sp, lineHeight = 39.sp), Color.White, TextAlign.Center)
    } else if (turn != null && turn.answer.isNotEmpty()) {
      val size = 30.sp
      val color = if (turn.failed) Palette.Bad else Color.White
      val range = state.spokenRange?.takeIf { !turn.complete || it.first in turn.answer.indices }
      if (!turn.complete || range != null) {
        // In progress: show the page holding the spoken word (page one while the answer is
        // still streaming in), with the word being spoken lit up when the engine reports one.
        val page = captionPage(turn.answer, focus = range?.first ?: 0, highlight = range, charsPerPage = PANEL_PAGE_CHARS)
        val annotated =
            buildAnnotatedString {
              append(page.text)
              page.highlight?.let {
                addStyle(
                    SpanStyle(color = GoldLine, fontWeight = FontWeight.Bold),
                    it.first.coerceIn(0, page.text.length),
                    minOf(it.last + 1, page.text.length))
              }
            }
        Text(
            annotated,
            fontFamily = HubFont,
            fontSize = size,
            lineHeight = size * 1.3f,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 11,
            overflow = TextOverflow.Ellipsis)
      } else {
        Text(
            turn.answer,
            fontFamily = HubFont,
            fontSize = size,
            lineHeight = size * 1.3f,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 11,
            overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

/** The controls, centered at the bottom of the left column: nothing up top to collide with the clock or the panel. */
@Composable
private fun ControlsRow(state: MuseState, onSettings: () -> Unit, onReplay: () -> Unit, onToggleMute: () -> Unit, onCallToggle: () -> Unit) {
  Row(
      Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
    // Green handset at rest, red CallEnd while a call runs; shown only when a call could start or end.
    val calling = state.ask is AskState.Calling
    val callResting = state.ask.let { it is AskState.Idle || (it is AskState.Answered && it.complete) || it is AskState.Failed }
    if (state.online && (calling || callResting)) {
      Box(
          Modifier.size(58.dp).clip(CircleShape).background(if (calling) Palette.Bad else Palette.Good).clickable { onCallToggle() },
          contentAlignment = Alignment.Center) {
            Icon(
                if (calling) Icons.Filled.CallEnd else Icons.Filled.Phone,
                contentDescription = if (calling) "End call" else "Start voice call",
                tint = Color.White,
                modifier = Modifier.size(30.dp))
          }
    }
    ChipButton(onClick = onReplay) {
      Icon(Icons.Filled.Replay, contentDescription = "Replay an announcement", tint = WarmWhite, modifier = Modifier.size(28.dp))
    }
    ChipButton(onClick = onToggleMute) {
      Icon(
          if (state.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
          contentDescription = if (state.muted) "Unmute" else "Mute",
          tint = WarmWhite,
          modifier = Modifier.size(28.dp))
    }
    ChipButton(onClick = onSettings) {
      Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = WarmWhite, modifier = Modifier.size(28.dp))
    }
  }
}

@Composable
private fun ChipButton(onClick: () -> Unit, content: @Composable () -> Unit) {
  Box(
      Modifier.size(58.dp)
          .clip(CircleShape)
          .background(Color.Black.copy(alpha = 0.30f))
          .border(1.dp, GoldLine.copy(alpha = 0.45f), CircleShape)
          .clickable { onClick() },
      contentAlignment = Alignment.Center) {
        content()
      }
}

/** The last few announcements, newest first; tapping one shows and plays it again. */
@Composable
private fun ReplayDialog(state: MuseState, onDismiss: () -> Unit, onReplayEntry: (Long) -> Unit) {
  val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.US) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Recent announcements") },
      text = {
        if (state.announcements.isEmpty()) {
          Text("No announcements yet.")
        } else {
          Column {
            state.announcements.forEach { a ->
              val kind = when {
                a.hasCard && a.hasAudio -> "Card + audio"
                a.hasCard -> "Card"
                else -> "Audio"
              }
              Row(
                  Modifier.fillMaxWidth().clickable { onReplayEntry(a.id) }.padding(vertical = 14.dp),
                  verticalAlignment = Alignment.CenterVertically) {
                    Text("${timeFormat.format(Date(a.atMs))} · $kind", style = MaterialTheme.typography.bodyLarge)
                  }
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

/** Chars of answer per caption page in the hub panel: about eleven lines at panel size. */
private const val PANEL_PAGE_CHARS = 560

/** One page of a long answer, with the spoken word's span inside it when there is one. */
private data class CaptionPage(val text: String, val highlight: IntRange?)

/**
 * Pages a long answer on word breaks, each page up to [charsPerPage] chars, and returns the page
 * holding [focus] (a char index). While the answer streams in, focus sits at 0: the first page
 * fills and holds instead of the text racing past. While it is spoken, focus is the spoken word,
 * so a page holds until the voice reaches its last word and then flips to the next page whole —
 * no sliding, no half-scrolled lines.
 */
private fun captionPage(text: String, focus: Int, highlight: IntRange?, charsPerPage: Int): CaptionPage {
  if (text.isEmpty()) return CaptionPage(text, null)
  if (text.length <= charsPerPage) {
    val hl =
        highlight?.let {
          val s = it.first.coerceIn(0, text.length - 1)
          val e = it.last.coerceIn(0, text.length - 1)
          if (e < s) null else s..e
        }
    return CaptionPage(text, hl)
  }
  val f = focus.coerceIn(0, text.length)
  var pageStart = 0
  while (true) {
    var pageEnd = minOf(text.length, pageStart + charsPerPage)
    if (pageEnd < text.length) {
      val breakAt = text.lastIndexOf(' ', pageEnd - 1)
      if (breakAt > pageStart) pageEnd = breakAt
    }
    if (f in pageStart..pageEnd || pageEnd == text.length) {
      val pageLength = pageEnd - pageStart
      val hl =
          highlight?.let {
            val s = it.first - pageStart
            val e = it.last - pageStart
            if (e < 0 || s > pageLength - 1) null
            else s.coerceIn(0, pageLength - 1)..e.coerceIn(0, pageLength - 1)
          }
      return CaptionPage(text.substring(pageStart, pageEnd), hl)
    }
    pageStart = pageEnd + 1 // step over the break space
  }
}

/** The recording time left as a line: full and green at the start, shrinking to the center and reddening. */
@Composable
private fun CapLine(fraction: Float) {
  val shown by animateFloatAsState(fraction, tween(160, easing = LinearEasing), label = "cap")
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Box(
        Modifier.fillMaxWidth(shown.coerceIn(0f, 1f))
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(lerp(Palette.Bad, Palette.Good, shown)))
  }
}

/**
 * Live captions: at most [lines] lines, the newest at the bottom, each new line pushing the oldest out
 * the top. The text keeps its start, so the lines already shown never rewrap as words arrive.
 */
@Composable
fun RollingText(text: String, lines: Int, style: TextStyle, color: Color, textAlign: TextAlign? = null) {
  // Every line exactly this tall, so the box holds whole lines only.
  val line = if (style.lineHeight.isSpecified) style.lineHeight else style.fontSize * 1.3f
  val exact = style.copy(lineHeight = line, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None), platformStyle = PlatformTextStyle(includeFontPadding = false))
  val height = with(LocalDensity.current) { line.toDp() } * lines
  Box(Modifier.fillMaxWidth().heightIn(max = height).clipToBounds(), contentAlignment = Alignment.BottomCenter) {
    Text(text, style = exact, color = color, textAlign = textAlign, modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.Bottom, unbounded = true))
  }
}
