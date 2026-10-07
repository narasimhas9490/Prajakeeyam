package app.prajakeeyam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddRoad
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Elderly
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Plumbing
import androidx.compose.material.icons.outlined.RiceBowl
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.prajakeeyam.R
import app.prajakeeyam.ui.theme.Ios

/** Icon + colour + label for each problem category. */
data class CategoryStyle(val icon: ImageVector, val color: Color, val labelRes: Int)

val CATEGORIES = listOf("road", "water", "electricity", "drainage", "health", "school", "ration", "pension", "garbage", "other")

private val styles: Map<String, CategoryStyle> = mapOf(
    "road" to CategoryStyle(Icons.Outlined.AddRoad, Color(0xFF636366), R.string.cat_road),
    "water" to CategoryStyle(Icons.Outlined.WaterDrop, Ios.Blue, R.string.cat_water),
    "electricity" to CategoryStyle(Icons.Outlined.Bolt, Ios.Orange, R.string.cat_electricity),
    "drainage" to CategoryStyle(Icons.Outlined.Plumbing, Ios.Teal, R.string.cat_drainage),
    "health" to CategoryStyle(Icons.Outlined.LocalHospital, Ios.Red, R.string.cat_health),
    "school" to CategoryStyle(Icons.Outlined.School, Ios.Indigo, R.string.cat_school),
    "ration" to CategoryStyle(Icons.Outlined.RiceBowl, Color(0xFFA2845E), R.string.cat_ration),
    "pension" to CategoryStyle(Icons.Outlined.Elderly, Ios.Purple, R.string.cat_pension),
    "garbage" to CategoryStyle(Icons.Outlined.Delete, Ios.Green, R.string.cat_garbage),
    "other" to CategoryStyle(Icons.Outlined.MoreHoriz, Ios.Secondary, R.string.cat_other),
)

fun categoryStyle(category: String): CategoryStyle = styles[category] ?: styles.getValue("other")
fun categoryLabelRes(category: String): Int = categoryStyle(category).labelRes

/** Coloured rounded square with a white icon (iOS Settings style). */
@Composable
fun IconTile(icon: ImageVector, color: Color, size: Dp = 30.dp, corner: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(corner)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

@Composable
fun CategoryTile(category: String, size: Dp = 30.dp, modifier: Modifier = Modifier) {
    val s = categoryStyle(category)
    IconTile(s.icon, s.color, size = size, corner = size * 0.27f, modifier = modifier)
}
