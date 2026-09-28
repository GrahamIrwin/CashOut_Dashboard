package com.cashoutdashboard.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun rememberBitmap(path: String?, maxEdge: Int): Bitmap? {
    val state by produceState<Bitmap?>(null, path, maxEdge) {
        value = if (path == null || !File(path).exists()) null else withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
    return state
}

@Composable
fun PhotoThumb(path: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val bmp = rememberBitmap(path, 600)
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = "Cashout photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (path != null) {
            CircularProgressIndicator(Modifier.size(24.dp))
        }
    }
}

/** Full-screen photo with pinch-to-zoom, for checking numbers against the slip. */
@Composable
fun PhotoViewer(path: String, onDismiss: () -> Unit) {
    val bmp = rememberBitmap(path, 2400)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                },
        ) {
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(), contentDescription = "Cashout photo",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
        }
    }
}

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

/** Icon on a soft neutral rounded square; the one icon treatment used across lists and cards. */
@Composable
fun IconBadge(icon: ImageVector, size: Dp = 36.dp, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.55f), tint = tint)
    }
}

/** A titled group of [ListRow]s on one card, separated by inset hairlines. */
@Composable
fun ListGroup(title: String? = null, modifier: Modifier = Modifier, rows: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) { Column(content = rows) }
    }
}

@Composable
fun ListDivider() = HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)

/** One settings-style row: icon badge, title and optional subtitle, then a trailing control or chevron. */
@Composable
fun ListRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = if (onClick != null) {
        { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline) }
    } else null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint = tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * Text field bound to a nullable number. Keeps its own text so half-typed values like "12." survive
 * recomposition; only pushes parsed values up. With [blankWhenZero], 0 shows as an empty field.
 */
@Composable
fun NumberField(
    label: String,
    value: Double?,
    onValue: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    integer: Boolean = false,
    prefix: String? = if (integer) null else "$",
    isError: Boolean = false,
    supportingText: String? = null,
    blankWhenZero: Boolean = false,
) {
    fun fmt(v: Double?) = when {
        v == null -> ""
        blankWhenZero && v == 0.0 -> ""
        integer -> v.toLong().toString()
        else -> "%.2f".format(v)
    }
    var text by remember { mutableStateOf(fmt(value)) }
    // Resync only when the value changed from outside (a fix was applied, a different draft loaded).
    val parsed = text.replace(",", "").toDoubleOrNull() ?: if (blankWhenZero) 0.0 else null
    val same = if (parsed == null || value == null) parsed == value else kotlin.math.abs(parsed - value) < 0.001
    if (!same) text = fmt(value)
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            val cleaned = t.filter { it.isDigit() || (!integer && (it == '.' || it == '-')) }
            text = cleaned
            onValue(cleaned.toDoubleOrNull())
        },
        label = { Text(label, maxLines = 1) },
        prefix = prefix?.let { { Text(it) } },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal),
        modifier = modifier,
    )
}

fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
fun Long.pickerMillisToDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = (initial ?: LocalDate.now()).toPickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(it.pickerMillisToDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state) }
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** Dialog for setting (or clearing) a tip goal. Shared by Settings and the dashboard goal card. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GoalDialog(
    amount: Double?,
    period: com.cashoutdashboard.app.data.GoalPeriod,
    onDismiss: () -> Unit,
    onSave: (Double?, com.cashoutdashboard.app.data.GoalPeriod) -> Unit,
) {
    var value by remember { mutableStateOf(amount) }
    var p by remember { mutableStateOf(period) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tip goal") },
        text = {
            Column {
                NumberField("Goal amount", value, { value = it }, Modifier.fillMaxWidth())
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    com.cashoutdashboard.app.data.GoalPeriod.entries.forEach { gp ->
                        SoftChip(selected = gp == p, onClick = { p = gp }, label = { Text(gp.label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value?.takeIf { it > 0 }, p); onDismiss() }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (amount != null) TextButton(onClick = { onSave(null, p); onDismiss() }) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Borderless choice chip: soft gray at rest, brand tint when selected. Used for every filter/choice row. */
@Composable
fun SoftChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        leadingIcon = leadingIcon,
        shape = RoundedCornerShape(50),
        border = null,
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            containerColor = colors.surfaceContainerHigh,
            labelColor = colors.onSurfaceVariant,
            selectedContainerColor = colors.primaryContainer,
            selectedLabelColor = colors.onPrimaryContainer,
            selectedLeadingIconColor = colors.onPrimaryContainer,
        ),
    )
}
