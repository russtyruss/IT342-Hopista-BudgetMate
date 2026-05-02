package budgetmate.data.network

import android.os.Build
import budgetmate.BuildConfig

object NetworkEndpointResolver {

    val apiBaseUrl: String
        get() = if (isEmulator()) BuildConfig.API_BASE_URL_EMULATOR else BuildConfig.API_BASE_URL_DEVICE

    val wsBaseUrl: String
        get() = if (isEmulator()) BuildConfig.WS_BASE_URL_EMULATOR else BuildConfig.WS_BASE_URL_DEVICE

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val hardware = Build.HARDWARE.lowercase()

        return fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            fingerprint.contains("emulator") ||
            model.contains("google_sdk") ||
            model.contains("emulator") ||
            model.contains("android sdk built for x86") ||
            manufacturer.contains("genymotion") ||
            (brand.startsWith("generic") && device.startsWith("generic")) ||
            product == "google_sdk" ||
            product.contains("sdk_gphone") ||
            product.contains("emulator") ||
            hardware.contains("ranchu") ||
            hardware.contains("goldfish")
    }
}
