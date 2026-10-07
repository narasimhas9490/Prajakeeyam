package app.prajakeeyam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.prajakeeyam.R
import app.prajakeeyam.ui.theme.Ios

/*
 * Minimal iOS-style building blocks: grey grouped background, white inset cards with hairlines,
 * frosted bars, segmented controls, one blue accent. No gradients, no decoration.
 */

val CardShape = RoundedCornerShape(16.dp)

@Composable
fun ScreenBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Ios.Background), content = content)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 0.6.dp, color = Ios.Separator)
}

/** Translucent bar with a large bold title, chevron back button and blue text actions. */
@Composable
fun FrostedTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().background(Ios.Frost).statusBarsPadding()) {
        if (onBack != null || actions != null) {
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.back), tint = Ios.Blue, modifier = Modifier.size(30.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                CompositionLocalProvider(LocalContentColor provides Ios.Blue) { actions?.invoke(this) }
            }
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = if (onBack == null && actions == null) 10.dp else 0.dp, bottom = 10.dp)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = Ios.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Ios.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Hairline()
    }
}

/** Translucent bottom bar (post button, comment box). */
@Composable
fun FrostedBottomBar(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Ios.Frost)) {
        Hairline()
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).navigationBarsPadding(), content = content)
    }
}

/** White inset card. */
@Composable
fun IosCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    var m = modifier.clip(CardShape).background(Ios.Card)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m, content = content)
}

/** One row of a grouped list: rounded on the first/last row, hairline between rows. */
@Composable
fun GroupRow(index: Int, count: Int, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val top = if (index == 0) 16.dp else 0.dp
    val bottom = if (index == count - 1) 16.dp else 0.dp
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(shape).background(Ios.Card).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 13.dp, bottom = 13.dp), verticalAlignment = Alignment.CenterVertically, content = content)
        if (index < count - 1) Hairline(Modifier.padding(start = 16.dp))
    }
}

@Composable
fun RowText(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = Ios.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Ios.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Chevron() {
    Text("›", color = Ios.Tertiary, fontSize = 26.sp, modifier = Modifier.padding(start = 8.dp))
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier.padding(start = 32.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = Ios.Secondary,
    )
}

/** iOS segmented control. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(10.dp)).background(Ios.Fill).padding(2.dp)) {
        options.forEach { (key, label) ->
            val isSelected = key == selected
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .weight(1f)
                    .then(if (isSelected) Modifier.shadow(2.dp, shape).background(Color.White, shape) else Modifier)
                    .clip(shape)
                    .clickable { onSelect(key) }
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = Ios.Label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, leading: (@Composable () -> Unit)? = null) {
    Button(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ios.Blue, contentColor = Color.White, disabledContainerColor = Ios.Blue.copy(alpha = 0.4f), disabledContentColor = Color.White),
        elevation = null,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun TintedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Ios.Blue, enabled: Boolean = true, leading: (@Composable () -> Unit)? = null) {
    Button(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = tint.copy(alpha = 0.12f), contentColor = tint, disabledContainerColor = tint.copy(alpha = 0.06f), disabledContentColor = tint.copy(alpha = 0.4f)),
        elevation = null,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Compact "Support · 12" pill used on cards; filled blue once the user supports. */
@Composable
fun SupportPill(count: Int, supported: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = if (supported) Ios.Blue else Ios.Blue.copy(alpha = 0.12f)
    val fg = if (supported) Color.White else Ios.Blue
    Row(
        modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ThumbUp, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(if (supported) R.string.supported else R.string.support) + " · $count",
            color = fg, style = MaterialTheme.typography.labelMedium,
        )
    }
}

private val avatarColors = listOf(Ios.Blue, Ios.Indigo, Ios.Teal, Ios.Green, Ios.Orange, Ios.Pink, Ios.Purple)

@Composable
fun Avatar(name: String, pictureUrl: String?, size: Dp = 36.dp) {
    if (pictureUrl != null) {
        RemoteImage(pictureUrl, Modifier.size(size).clip(CircleShape), width = 120)
    } else {
        val color = avatarColors[(name.hashCode() and 0x7fffffff) % avatarColors.size]
        Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(name.trim().take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
    }
}

@Composable
fun IosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    fill: Color = Ios.FillSoft,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder, color = Ios.Secondary) },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        leadingIcon = leading,
        trailingIcon = trailing,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = Ios.Blue,
            focusedTextColor = Ios.Label,
            unfocusedTextColor = Ios.Label,
        ),
    )
}
