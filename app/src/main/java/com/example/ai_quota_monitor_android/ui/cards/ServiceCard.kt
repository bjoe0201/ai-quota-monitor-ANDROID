package com.example.ai_quota_monitor_android.ui.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_quota_monitor_android.data.model.ServiceResult
import com.example.ai_quota_monitor_android.ui.theme.AppColorSet
import com.example.ai_quota_monitor_android.ui.theme.LocalAppColors
import com.example.ai_quota_monitor_android.ui.theme.ServiceAccents
import com.example.ai_quota_monitor_android.util.formatTokens
import com.example.ai_quota_monitor_android.util.headerTint
import com.example.ai_quota_monitor_android.util.percentColor

// -- Internal row model -----------------------------------------------------------

private sealed class CardRow {
    data class Hero(
        val label: String,
        val value: String,
        val color: Color = Color.Unspecified,
        val unit: String? = null,
        val badge: String? = null,
    ) : CardRow()

    data class Bar(
        val label: String,
        val percent: Float,
        val detail: String = "",
        val color: Color? = null,
        val resetText: String? = null,
        val resetUrgent: Boolean = false,
    ) : CardRow()

    data class Kv(
        val label: String,
        val value: String = "",
        val valueColor: Color = Color.Unspecified,
    ) : CardRow()

    data class Pair(
        val leftLabel: String,
        val leftValue: String = "",
        val rightLabel: String = "",
        val rightValue: String = "",
    ) : CardRow()

    data class Divider(val label: String) : CardRow()
}

// -- Main composable --------------------------------------------------------------

@Composable
fun ServiceCard(
    serviceKey: String,
    displayName: String,
    result: ServiceResult?,
    collapsed: Boolean = false,
    onToggleCollapse: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val accent = ServiceAccents.get(serviceKey)
    val headerBg = headerTint(accent, colors.CardBg)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.CardBg),
    ) {
        CardHeader(
            serviceKey = serviceKey,
            displayName = displayName,
            accent = accent,
            headerBg = headerBg,
            collapsed = collapsed,
            statusColor = statusDotColor(result, colors),
            timestamp = result?.data?.get("updated_at")?.toString() ?: "",
            onToggle = onToggleCollapse,
        )
        AnimatedVisibility(visible = !collapsed) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (result == null) {
                    PlaceholderText("載入中...", colors.TextDim)
                } else if (!result.success) {
                    val isWaiting = result.error?.contains("等待瀏覽器") == true
                    PlaceholderText(
                        text = if (isWaiting) "等待瀏覽器資料..." else (result.error ?: "未知錯誤"),
                        color = if (isWaiting) colors.TextDim else colors.Error,
                    )
                } else {
                    val rows = formatData(serviceKey, result.data, colors)
                    RenderRows(rows)
                }
            }
        }
    }
}

// -- Header -----------------------------------------------------------------------

@Composable
private fun CardHeader(
    serviceKey: String,
    displayName: String,
    accent: Color,
    headerBg: Color,
    collapsed: Boolean,
    statusColor: Color,
    timestamp: String,
    onToggle: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerBg)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (collapsed) "\u25B8" else "\u25BE",
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(4.dp))
        val glyph = glyphFor(serviceKey)
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = glyph,
                color = colors.Bg,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = displayName,
            color = colors.Text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "\u25CF",
            color = statusColor,
            fontSize = 6.sp,
            modifier = Modifier.padding(end = 4.dp),
        )
        if (timestamp.isNotEmpty()) {
            Text(
                text = timestamp,
                color = colors.TextFaint,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

// -- Row renderer -----------------------------------------------------------------

@Composable
private fun RenderRows(rows: List<CardRow>) {
    val colors = LocalAppColors.current
    val lastKvIdx = rows.indexOfLast { it is CardRow.Kv || it is CardRow.Pair }
    rows.forEachIndexed { i, row ->
        val isLast = (i == lastKvIdx)
        when (row) {
            is CardRow.Hero -> HeroSection(
                label = row.label,
                value = row.value,
                valueColor = row.color,
                valueUnit = row.unit,
                badge = row.badge,
            )
            is CardRow.Bar -> BarRow(
                label = row.label,
                percent = row.percent,
                detail = row.detail,
                color = row.color,
                resetText = row.resetText,
                resetUrgent = row.resetUrgent,
            )
            is CardRow.Kv -> KvRow(
                label = row.label,
                value = row.value,
                valueColor = row.valueColor,
                showDivider = !isLast,
            )
            is CardRow.Pair -> KvPairRow(
                leftLabel = row.leftLabel,
                leftValue = row.leftValue,
                rightLabel = row.rightLabel,
                rightValue = row.rightValue,
                showDivider = !isLast,
            )
            is CardRow.Divider -> {
                HorizontalDivider(
                    color = colors.Border,
                    modifier = Modifier.padding(top = 6.dp, bottom = 3.dp),
                )
                Text(
                    text = row.label,
                    color = colors.TextDim,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PlaceholderText(text: String, color: Color) {
    Text(text = text, color = color, fontSize = 9.sp)
}

// -- Helpers ----------------------------------------------------------------------

private fun glyphFor(key: String): String = when (key) {
    "browser_openai" -> "OA"
    "browser_claude_usage" -> "C"
    "browser_claude_billing" -> "API"
    "browser_github_copilot" -> "GH"
    "browser_openrouter" -> "OR"
    "browser_chatgpt_usage" -> "GPT"
    else -> "\u00b7"
}

private fun statusDotColor(result: ServiceResult?, colors: AppColorSet): Color = when {
    result == null -> colors.TextDim
    !result.success && result.error?.contains("等待瀏覽器") == true -> colors.Warning
    !result.success -> colors.Error
    else -> colors.Success
}

private fun pctColor(pct: Float): Color = percentColor(pct)

// -- Data formatting (ported from desktop_widget/cards.py _format_data) ------------

private fun formatData(serviceKey: String, data: Map<String, Any?>, colors: AppColorSet): List<CardRow> {
    val rows = mutableListOf<CardRow>()

    data["stale_warning"]?.toString()?.let {
        rows.add(CardRow.Kv(label = it, valueColor = colors.Warning))
    }

    when (serviceKey) {
        "browser_openai" -> formatOpenAI(data, rows, colors)
        "browser_claude_usage" -> formatClaudeUsage(data, rows, colors)
        "browser_claude_billing" -> formatClaudeBilling(data, rows, colors)
        "browser_github_copilot" -> formatGitHubCopilot(data, rows, colors)
        "browser_openrouter" -> formatOpenRouter(data, rows, colors)
        "browser_chatgpt_usage" -> formatChatGptUsage(data, rows, colors)
    }

    if (rows.isEmpty() || (rows.size == 1 && rows[0] is CardRow.Kv && (rows[0] as CardRow.Kv).value.isEmpty())) {
        rows.clear()
        rows.add(CardRow.Kv(label = "無資料", valueColor = colors.TextDim))
    }
    return rows
}

private fun formatOpenAI(data: Map<String, Any?>, rows: MutableList<CardRow>, colors: AppColorSet) {
    data.num("balance_usd")?.let {
        rows.add(CardRow.Hero(label = "帳戶餘額", value = "$%.2f".format(it), color = colors.Success))
    }
    val used = data.num("credits_used_usd")
    val total = data.num("credits_total_usd")
    if (used != null && total != null && total > 0) {
        val pct = (used / total * 100).toFloat()
        rows.add(CardRow.Bar(
            label = "Credits 用量", percent = pct,
            detail = "$%.2f / $%.2f".format(used, total), color = pctColor(pct),
        ))
    }
    val monthVal = data.num("month_usage_usd")?.let { "$%.4f".format(it) } ?: ""
    val limitVal = data.num("hard_limit_usd")?.let { "$%.0f".format(it) } ?: ""
    if (monthVal.isNotEmpty() || limitVal.isNotEmpty()) {
        rows.add(CardRow.Pair("本月用量", monthVal, "月上限", limitVal))
    }
    val tierVal = data["tier"]?.toString() ?: ""
    val autoVal = if (data["auto_recharge"] == true) "已啟用" else ""
    if (tierVal.isNotEmpty() || autoVal.isNotEmpty()) {
        rows.add(CardRow.Pair("用量等級", tierVal, "自動儲值", autoVal))
    }
}

private fun formatClaudeUsage(data: Map<String, Any?>, rows: MutableList<CardRow>, colors: AppColorSet) {
    data.num("session_percent")?.toFloat()?.let { pct ->
        val reset = data["session_reset"]?.toString()?.takeIf { it.isNotEmpty() }
        rows.add(CardRow.Bar(
            label = "本次工作階段", percent = pct, color = pctColor(pct),
            resetText = reset, resetUrgent = true,
        ))
    }
    data.num("weekly_percent")?.toFloat()?.let { pct ->
        val reset = data["weekly_reset"]?.toString()?.takeIf { it.isNotEmpty() }
        rows.add(CardRow.Bar(
            label = "每週限額（全部模型）", percent = pct, color = pctColor(pct),
            resetText = reset,
        ))
    }
    data.num("fable_percent")?.toFloat()?.let { pct ->
        val reset = data["fable_reset"]?.toString()?.takeIf { it.isNotEmpty() }
        val name = data["fable_name"]?.toString()?.takeIf { it.isNotEmpty() } ?: "Fable"
        rows.add(CardRow.Bar(
            label = "$name 週限額", percent = pct, color = pctColor(pct),
            resetText = reset,
        ))
    }
    if (data["extra_enabled"] == true || data.containsKey("extra_spent") || data.containsKey("extra_balance")) {
        val pct = data.num("extra_percent")?.toFloat() ?: 0f
        val reset = data["extra_resets"]?.toString()?.takeIf { it.isNotEmpty() }
        rows.add(CardRow.Bar(
            label = "額外用量", percent = pct, color = pctColor(pct),
            resetText = reset,
        ))
        val parts = mutableListOf<String>()
        val spent = data.num("extra_spent")
        val limit = data.num("extra_limit")
        val bal = data.num("extra_balance")
        if (spent != null && limit != null) parts.add("$%.2f / $%.0f".format(spent, limit))
        else if (spent != null) parts.add("已花費 $%.2f".format(spent))
        if (bal != null) parts.add("餘額 $%.2f".format(bal))
        if (data.containsKey("auto_reload")) {
            parts.add(if (data["auto_reload"] == true) "自動儲值" else "儲值:關")
        }
        if (parts.isNotEmpty()) {
            rows.add(CardRow.Kv(label = "", value = parts.joinToString("  \u00b7  "), valueColor = colors.Green))
        }
    }
}

private fun formatClaudeBilling(data: Map<String, Any?>, rows: MutableList<CardRow>, colors: AppColorSet) {
    data.num("balance_usd")?.let {
        rows.add(CardRow.Hero(
            label = "帳戶餘額", value = "$%.2f".format(it), color = colors.Success,
            badge = data["plan"]?.toString(),
        ))
    }
    val monthVal = data.num("this_month_usd")?.let { "$%.4f".format(it) } ?: ""
    val nextVal = data["next_billing"]?.toString() ?: ""
    if (monthVal.isNotEmpty() || nextVal.isNotEmpty()) {
        rows.add(CardRow.Pair("本月用量", monthVal, "下次計費", nextVal))
    }
    val monthlyVal = data.num("monthly_usd")?.let { "$%.2f".format(it) } ?: ""
    val limitVal = data.num("spend_limit_usd")?.let { "$%.2f".format(it) } ?: ""
    if (monthlyVal.isNotEmpty() || limitVal.isNotEmpty()) {
        rows.add(CardRow.Pair("月費", monthlyVal, "消費上限", limitVal))
    }
}

private fun formatGitHubCopilot(data: Map<String, Any?>, rows: MutableList<CardRow>, colors: AppColorSet) {
    val consumed = data.num("included_consumed")
    val total = data.num("included_total")
    val pct = data.num("included_percent")?.toFloat()
    if (consumed != null && total != null) {
        val remaining = total - consumed
        val heroColor = if (pct != null) pctColor(pct) else colors.Info
        rows.add(CardRow.Hero(
            label = "PREMIUM REQUESTS 剩餘",
            value = "%.0f".format(remaining),
            unit = "/%.0f".format(total),
            color = heroColor,
            badge = data["plan"]?.toString(),
        ))
    }
    if (pct != null) {
        val detail = if (consumed != null && total != null) "%.1f / %.0f 次".format(consumed, total) else ""
        val resetDays = data.num("resets_in_days")?.toInt()
        rows.add(CardRow.Bar(
            label = "Premium Requests", percent = pct, detail = detail, color = pctColor(pct),
            resetText = if (resetDays != null) "${resetDays} 天後" else null,
            resetUrgent = (resetDays ?: 99) <= 3,
        ))
    }
    data.num("billed_usd")?.takeIf { it > 0 }?.let {
        rows.add(CardRow.Kv("已計費", "$%.2f".format(it), colors.Peach))
    }
    data["next_billing"]?.toString()?.takeIf { it.isNotEmpty() }?.let {
        rows.add(CardRow.Kv("下次計費", it, colors.Violet))
    }
    val budgetSpent = data.num("budget_spent_usd")
    val budgetLimit = data.num("budget_limit_usd")
    val budgetPct = data.num("budget_percent")?.toFloat()
    if (budgetSpent != null || budgetPct != null) {
        val pct = budgetPct ?: 0f
        val detail = if (budgetSpent != null && budgetLimit != null)
            "$%.2f / $%.2f".format(budgetSpent, budgetLimit)
        else ""
        rows.add(CardRow.Bar(
            label = "All Premium Request SKUs",
            percent = pct,
            detail = detail,
            color = pctColor(pct),
        ))
    }
}

private fun formatOpenRouter(data: Map<String, Any?>, rows: MutableList<CardRow>, colors: AppColorSet) {
    data["parse_error"]?.toString()?.let {
        rows.add(CardRow.Kv("解析失敗", it, colors.Error))
    }
    data.num("balance_usd")?.let {
        rows.add(CardRow.Hero(label = "帳戶餘額", value = "$%.2f".format(it), color = colors.Success))
    }
    val spendVal = data.num("month_spend_usd")?.let { "$%.4f".format(it) } ?: ""
    val reqVal = data.num("month_requests")?.let { "%,.0f 次".format(it) } ?: ""
    if (spendVal.isNotEmpty() || reqVal.isNotEmpty()) {
        rows.add(CardRow.Pair("本月花費", spendVal, "請求次數", reqVal))
    }
    val tokensVal = data.num("month_tokens")?.let { formatTokens(it.toLong()) } ?: ""
    val modelVal = data["top_model"]?.toString()?.take(20) ?: ""
    if (tokensVal.isNotEmpty() || modelVal.isNotEmpty()) {
        rows.add(CardRow.Pair("Tokens", tokensVal, "常用模型", modelVal))
    }
}

private fun formatChatGptUsage(data: Map<String, Any?>, rows: MutableList<CardRow>, colors: AppColorSet) {
    data.num("weekly_remaining_percent")?.toFloat()?.let { remaining ->
        val remainingColor = when {
            remaining <= 10f -> colors.Error
            remaining <= 25f -> colors.Warning
            else -> colors.Success
        }
        rows.add(CardRow.Bar(
            label = "每週剩餘額度",
            percent = remaining.coerceIn(0f, 100f),
            color = remainingColor,
        ))
    }
    data["weekly_reset"]?.toString()?.takeIf { it.isNotEmpty() }?.let {
        rows.add(CardRow.Kv("下次重設時間", it, colors.Violet))
    }
    data.num("credits")?.let {
        rows.add(CardRow.Kv("點數", "%,.0f 點".format(it), colors.Info))
    }
}

/** Safely extract a numeric value from Any? */
private fun Map<String, Any?>.num(key: String): Double? {
    val v = this[key] ?: return null
    return when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }
}
