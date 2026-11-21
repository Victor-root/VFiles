package me.zhanghai.android.files.theme.custom

import androidx.annotation.ColorRes
import me.zhanghai.android.files.R

/** [launcherAliasSuffix] names the activity alias showing the launcher icon in this color. */
enum class ThemeColor(@param:ColorRes val resourceId: Int, val launcherAliasSuffix: String?) {
    COLOR_PRIMARY(R.color.color_primary, "Default"),
    MATERIAL_RED(R.color.material_red, "Red"),
    MATERIAL_PINK(R.color.material_pink, "Pink"),
    MATERIAL_PURPLE(R.color.material_purple, "Purple"),
    MATERIAL_DEEP_PURPLE(R.color.material_deep_purple, "DeepPurple"),
    MATERIAL_INDIGO(R.color.material_indigo, "Indigo"),
    MATERIAL_BLUE(R.color.material_blue, "Blue"),
    MATERIAL_LIGHT_BLUE(R.color.material_light_blue, "LightBlue"),
    MATERIAL_CYAN(R.color.material_cyan, "Cyan"),
    MATERIAL_TEAL(R.color.material_teal, "Teal"),
    MATERIAL_GREEN(R.color.material_green, "Green"),
    MATERIAL_LIGHT_GREEN(R.color.material_light_green, "LightGreen"),
    MATERIAL_LIME(R.color.material_lime, "Lime"),
    MATERIAL_YELLOW(R.color.material_yellow, "Yellow"),
    MATERIAL_AMBER(R.color.material_amber, "Amber"),
    MATERIAL_ORANGE(R.color.material_orange, "Orange"),
    MATERIAL_DEEP_ORANGE(R.color.material_deep_orange, "DeepOrange"),
    MATERIAL_BROWN(R.color.material_brown, "Brown"),
    MATERIAL_GREY(R.color.material_grey, "Grey"),
    MATERIAL_BLUE_GREY(R.color.material_blue_grey, "BlueGrey"),
    // Follows the system / wallpaper colors (Material You), only meaningful with Material Design 3
    // on Android 12+. Kept last so the ordinals of the real colors (which is what gets persisted)
    // stay stable, and so it is easy to hide on older versions.
    DYNAMIC(R.color.theme_color_dynamic, null)
}
