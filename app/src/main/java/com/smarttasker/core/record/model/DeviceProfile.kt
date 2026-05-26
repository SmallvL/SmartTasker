package com.smarttasker.core.record.model

data class DeviceProfile(
    val screenWidth: Int,
    val screenHeight: Int,
    val densityDpi: Int,
    val androidVersion: Int,
    val manufacturer: String,
    val model: String,
    val navigationMode: String = "gesture", // gesture / 3button / 2button
    val statusBarHeightPx: Int = 0,
    val navBarHeightPx: Int = 0,
    val rotation: Int = 0 // 0, 90, 180, 270
) {
    val densityFactor: Float get() = densityDpi / 160f
    fun dpToPx(dp: Int): Int = (dp * densityFactor).toInt()
    fun normalizeX(x: Int): Float = x.toFloat() / screenWidth
    fun normalizeY(y: Int): Float = y.toFloat() / screenHeight
    fun denormalizeX(nx: Float): Int = (nx * screenWidth).toInt()
    fun denormalizeY(ny: Float): Int = (ny * screenHeight).toInt()
}
