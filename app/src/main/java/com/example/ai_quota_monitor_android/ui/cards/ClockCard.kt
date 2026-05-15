package com.example.ai_quota_monitor_android.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_quota_monitor_android.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Flip-clock palette
private val FlipCardBg = Color(0xFFF5F5F0)
private val FlipDigit = Color(0xFF0F0F12)
private val FlipDigitShade = Color(0xFFCBCBC4)
private val FlipSecond = Color(0xFF6A6A72)
private val FlipDateBg = Color(0xFF2A2A30)
private val FlipDateFg = Color(0xFFE5E5E0)

@Composable
fun ClockCard(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            val msLeft = 1000L - (System.currentTimeMillis() % 1000)
            delay(msLeft)
        }
    }

    val hh = "%02d".format(now.hour)
    val mm = "%02d".format(now.minute)
    val ss = "%02d".format(now.second)
    val weekdays = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dateText = now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) +
            "  \u00b7  " + weekdays[now.dayOfWeek.value - 1]

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LocalAppColors.current.CardBg)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Time row: HH  MM  :ss
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
        ) {
            FlipCardDigit(value = hh)
            Box(modifier = Modifier.width(6.dp))
            FlipCardDigit(value = mm)
            Text(
                text = ss,
                color = FlipSecond,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 5.dp, bottom = 8.dp),
            )
        }

        // Date pill
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .background(FlipDateBg, RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFF3A3A40), RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 3.dp),
        ) {
            Text(
                text = dateText,
                color = FlipDateFg,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── Compact: smaller flip digits, horizontal (used in Layout A portrait / D portrait) ──

@Composable
fun ClockCardCompact(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000)
        }
    }
    val hh = "%02d".format(now.hour)
    val mm = "%02d".format(now.minute)
    val ss = "%02d".format(now.second)
    val weekdays = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dateText = now.format(DateTimeFormatter.ofPattern("MM/dd")) +
            "  \u00b7  " + weekdays[now.dayOfWeek.value - 1]

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LocalAppColors.current.CardBg)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
            FlipDigitMedium(hh)
            Box(Modifier.width(4.dp))
            FlipDigitMedium(mm)
            Text(
                text = ss,
                color = FlipSecond,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 3.dp, bottom = 5.dp),
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .background(FlipDateBg, RoundedCornerShape(3.dp))
                .border(1.dp, Color(0xFF3A3A40), RoundedCornerShape(3.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(dateText, color = FlipDateFg, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Strip: inline time row for embedding inside a topbar Row (Layout C) ──────

@Composable
fun ClockCardStrip(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000)
        }
    }
    val hh = "%02d".format(now.hour)
    val mm = "%02d".format(now.minute)
    val ss = "%02d".format(now.second)
    val weekdays = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dateText = now.format(DateTimeFormatter.ofPattern("MM/dd")) +
            "  \u00b7  " + weekdays[now.dayOfWeek.value - 1]

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FlipDigitStrip(hh)
        FlipDigitStrip(mm)
        Text(
            text = ":$ss",
            color = FlipSecond,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )
        Box(
            modifier = Modifier
                .background(FlipDateBg, RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(dateText, color = FlipDateFg, fontSize = 8.sp)
        }
    }
}

// ── Sidebar: vertical stack (Layout B landscape sidebar) ─────────────────────

@Composable
fun ClockCardSidebar(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000)
        }
    }
    val hh = "%02d".format(now.hour)
    val mm = "%02d".format(now.minute)
    val ss = "%02d".format(now.second)
    val weekdays = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dateText = now.format(DateTimeFormatter.ofPattern("MM/dd")) +
            "\n" + weekdays[now.dayOfWeek.value - 1]

    Column(
        modifier = modifier
            .background(LocalAppColors.current.CardBg)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlipDigitMedium(hh)
        Spacer(Modifier.height(4.dp))
        FlipDigitMedium(mm)
        Text(
            text = ":$ss",
            color = FlipSecond,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp),
        )
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .background(FlipDateBg, RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(dateText, color = FlipDateFg, fontSize = 7.sp, textAlign = TextAlign.Center)
        }
    }
}

// ── Private digit helpers ─────────────────────────────────────────────────────

/** Medium flip digit: 64×48 dp, 32 sp — for compact and sidebar. */
@Composable
private fun FlipDigitMedium(value: String) {
    Box(
        modifier = Modifier
            .width(64.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(FlipCardBg),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalDivider(color = FlipDigitShade, modifier = Modifier.align(Alignment.Center))
        Text(
            text = value,
            color = FlipDigit,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
        )
    }
}

/** Small flip digit: 36×26 dp, 18 sp — for strip topbar. */
@Composable
private fun FlipDigitStrip(value: String) {
    Box(
        modifier = Modifier
            .width(36.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(FlipCardBg),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalDivider(color = FlipDigitShade, modifier = Modifier.align(Alignment.Center))
        Text(
            text = value,
            color = FlipDigit,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FlipCardDigit(value: String) {
    Box(
        modifier = Modifier
            .width(90.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(FlipCardBg),
        contentAlignment = Alignment.Center,
    ) {
        // Mid-line seam
        HorizontalDivider(
            color = FlipDigitShade,
            modifier = Modifier.align(Alignment.Center),
        )
        Text(
            text = value,
            color = FlipDigit,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
        )
    }
}
