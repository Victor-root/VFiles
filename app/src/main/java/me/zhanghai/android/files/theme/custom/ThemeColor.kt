package me.zhanghai.android.files.theme.custom

import androidx.annotation.ColorRes
import me.zhanghai.android.files.R

enum class ThemeColor(@param:ColorRes val resourceId: Int) {
    COLOR_PRIMARY(R.color.color_primary),
    MATERIAL_RED(R.color.material_red),
    MATERIAL_PINK(R.color.material_pink),
    MATERIAL_PURPLE(R.color.material_purple),
    MATERIAL_DEEP_PURPLE(R.color.material_deep_purple),
    MATERIAL_INDIGO(R.color.material_indigo),
    MATERIAL_BLUE(R.color.material_blue),
    MATERIAL_LIGHT_BLUE(R.color.material_light_blue),
    MATERIAL_CYAN(R.color.material_cyan),
    MATERIAL_TEAL(R.color.material_teal),
    MATERIAL_GREEN(R.color.material_green),
    MATERIAL_LIGHT_GREEN(R.color.material_light_green),
    MATERIAL_LIME(R.color.material_lime),
    MATERIAL_YELLOW(R.color.material_yellow),
    MATERIAL_AMBER(R.color.material_amber),
    MATERIAL_ORANGE(R.color.material_orange),
    MATERIAL_DEEP_ORANGE(R.color.material_deep_orange),
    MATERIAL_BROWN(R.color.material_brown),
    MATERIAL_GREY(R.color.material_grey),
    MATERIAL_BLUE_GREY(R.color.material_blue_grey),
    // Follows the system / wallpaper colors (Material You), only meaningful with Material Design 3
    // on Android 12+. Kept last so the ordinals of the real colors (which is what gets persisted)
    // stay stable, and so it is easy to hide on older versions.
    DYNAMIC(R.color.theme_color_dynamic)
}
