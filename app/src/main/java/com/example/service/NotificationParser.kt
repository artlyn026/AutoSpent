package com.example.service

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.CategoryEntity
import com.example.data.TransactionEntity
import java.util.regex.Pattern

/**
 * Production-ready NotificationParser for financial tracking apps.
 *
 * Solves two critical parsing pitfalls:
 * 1. Misattribution: Strictly checks `sbn.packageName` FIRST before applying any regex,
 *    preventing transactions from being misattributed (e.g. transfer from ShopeePay to DANA
 *    is accurately attributed to ShopeePay as sourceApp with DANA as destination).
 * 2. Formats: Applies dedicated regex rules for each e-wallet & mobile banking app:
 *    - GoPay (com.gojek.app)
 *    - OVO (ovo.id)
 *    - ShopeePay (com.shopee.id)
 *    - DANA (id.dana)
 *    - BCA Mobile (com.bca / id.co.bca)
 *    - BRImo (id.co.bri.brimo)
 *    - SeaBank (com.bke.seabank)
 * 3. Filters: Strictly ignores incoming transfers, top-ups, interest credits, and marketing promos.
 */
object NotificationParser {

    private const val TAG = "NotificationParser"

    // Supported App Packages
    const val PKG_GOPAY = "com.gojek.app"
    const val PKG_OVO = "ovo.id"
    const val PKG_SHOPEEPAY = "com.shopee.id"
    const val PKG_DANA = "id.dana"
    const val PKG_BCA = "com.bca"
    const val PKG_BCA_ALT = "id.co.bca"
    const val PKG_BRIMO = "id.co.bri.brimo"
    const val PKG_SEABANK = "com.bke.seabank"
    const val PKG_SIMULATOR = "com.example" // Internal test simulator

    /**
     * Parses a StatusBarNotification (SBN) safely.
     * Returns a valid TransactionEntity if it's a confirmed outgoing expense.
     * Returns null if incoming transfer, promo, or unsupported package.
     */
    fun parse(sbn: StatusBarNotification?): TransactionEntity? {
        if (sbn == null) return null

        val packageName = sbn.packageName ?: return null
        val extras = sbn.notification?.extras ?: return null

        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

        // Use the richest text available
        val content = if (bigText.isNotBlank()) "$title $bigText".trim() else "$title $text".trim()

        return parseByPackage(packageName, title, content)
    }

    /**
     * Package-based routing parser. Ensures package isolation so no cross-wallet misattribution occurs.
     */
    fun parseByPackage(packageName: String, title: String, content: String): TransactionEntity? {
        if (content.isBlank()) return null
        Log.d(TAG, "Parsing package: $packageName | Content: $content")

        return when (packageName) {
            PKG_GOPAY -> parseGoPay(content)
            PKG_OVO -> parseOvo(content)
            PKG_SHOPEEPAY -> parseShopeePay(content)
            PKG_DANA -> parseDana(content)
            PKG_BCA, PKG_BCA_ALT -> parseBca(content)
            PKG_BRIMO -> parseBriMo(content)
            PKG_SEABANK -> parseSeaBank(content)
            PKG_SIMULATOR -> parseSimulator(title, content)
            else -> null // Ignore notifications from all other unrelated apps
        }
    }

    // =========================================================================
    // 1. GOPAY (com.gojek.app)
    // =========================================================================
    /**
     * Real-world GoPay notification patterns:
     * - "Kamu sudah bayar Rp28.000 ke Kopi Kenangan pakai GoPay."
     * - "Kamu telah membayar Rp35.000 di Alfamart"
     * - "Transfer saldo GoPay Rp50.000 ke BCA (1234567890) berhasil"
     * - "Pembayaran Rp45.000 untuk pesanan GoFood berhasil"
     * - "Pembayaran Rp16.000 untuk perjalanan GoRide selesai"
     */
    private fun parseGoPay(content: String): TransactionEntity? {
        val lower = content.lowercase()

        // Filter out incoming transfers and promotional banners
        if (isGenericPromoOrIncoming(lower)) return null
        if (lower.contains("top up") || lower.contains("menerima transfer") || lower.contains("kamu menerima")) {
            return null
        }

        // Must be an outgoing payment / expense
        val isExpense = lower.contains("sudah bayar") || lower.contains("telah membayar") ||
                lower.contains("bayar") || lower.contains("transfer saldo") ||
                lower.contains("pembayaran") || lower.contains("pesanan")

        if (!isExpense) return null

        val amount = extractAmount(content) ?: return null

        // Extract merchant or recipient
        val merchant = when {
            lower.contains("gofood") -> "GoFood"
            lower.contains("goride") -> "GoRide"
            lower.contains("gocar") -> "GoCar"
            else -> {
        val pattern = Pattern.compile(
            "\\b(?:di|ke)\\b\\s+([A-Za-z0-9\\s&'.-]{2,30}?)(?:\\s+(?:pakai|dengan|menggunakan|berhasil|selesai)|[.,!]|$)",
            Pattern.CASE_INSENSITIVE
        )
                val matcher = pattern.matcher(content)
                if (matcher.find()) {
                    matcher.group(1)?.trim() ?: "GoPay Merchant"
                } else {
                    "GoPay Merchant"
                }
            }
        }

        return buildExpense(amount, cleanMerchant(merchant), "GoPay", content)
    }

    // =========================================================================
    // 2. OVO (ovo.id)
    // =========================================================================
    /**
     * Real-world OVO notification patterns:
     * - "Pembayaran ke Fore Coffee sebesar Rp18.000 menggunakan OVO Cash berhasil."
     * - "Kamu telah melakukan pembayaran sebesar Rp25.000 di Grab"
     * - "Transfer Rp50.000 ke Bank Mandiri berhasil"
     * - "Berhasil bayar Rp32.000 di Kopi Janji Jiwa"
     */
    private fun parseOvo(content: String): TransactionEntity? {
        val lower = content.lowercase()

        if (isGenericPromoOrIncoming(lower)) return null
        if (lower.contains("top up") || lower.contains("ovo points") || lower.contains("transfer dari")) {
            return null
        }

        val isExpense = lower.contains("pembayaran") || lower.contains("bayar") ||
                lower.contains("transfer") || lower.contains("sebesar")

        if (!isExpense) return null

        val amount = extractAmount(content) ?: return null

        val pattern = Pattern.compile(
            "\\b(?:pembayaran ke|ke|di)\\b\\s+([A-Za-z0-9\\s&'.-]{2,30}?)(?:\\s+(?:sebesar|menggunakan|pakai|berhasil)|[.,!]|$)",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(content)

        val merchant = if (matcher.find()) {
            matcher.group(1)?.trim() ?: "OVO Merchant"
        } else {
            "OVO Merchant"
        }

        return buildExpense(amount, cleanMerchant(merchant), "OVO", content)
    }

    // =========================================================================
    // 3. SHOPEEPAY (com.shopee.id)
    // =========================================================================
    /**
     * Real-world ShopeePay notification patterns:
     * - "Pembayaran sebesar Rp42.500 di Alfamart telah berhasil."
     * - "Kamu telah membayar Rp22.000 ke Chatime menggunakan ShopeePay."
     * - "Transfer ke DANA sebesar Rp50.000 berhasil."
     * - "Transfer saldo sebesar Rp100.000 ke BCA (1234567890) berhasil."
     * - "Pesanan selesai. Pembayaran ShopeePay Rp65.000 berhasil."
     */
    private fun parseShopeePay(content: String): TransactionEntity? {
        val lower = content.lowercase()

        // Filter out Shopee e-commerce shopping promotions, flash sales, and top-ups
        if (isGenericPromoOrIncoming(lower)) return null
        if (lower.contains("flash sale") || lower.contains("gratis ongkir") ||
            lower.contains("koin shopee") || lower.contains("klaim voucher") ||
            lower.contains("top up") || lower.contains("masuk ke shopeepay") || lower.contains("dana dari")
        ) {
            return null
        }

        // Must be a ShopeePay payment or transfer out
        val isExpense = lower.contains("shopeepay") || lower.contains("pembayaran") ||
                lower.contains("telah membayar") || lower.contains("transfer ke") ||
                lower.contains("transfer saldo")

        if (!isExpense) return null

        val amount = extractAmount(content) ?: return null

        // Distinct extraction for ShopeePay transfer or merchant
        val pattern = Pattern.compile(
            "\\b(?:di|ke)\\b\\s+([A-Za-z0-9\\s&'.-]{2,30}?)(?:\\s+(?:sebesar|telah|menggunakan|berhasil)|[.,!]|$)",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(content)

        val merchant = if (matcher.find()) {
            matcher.group(1)?.trim() ?: "ShopeePay Merchant"
        } else {
            "ShopeePay Merchant"
        }

        return buildExpense(amount, cleanMerchant(merchant), "ShopeePay", content)
    }

    // =========================================================================
    // 4. DANA (id.dana)
    // =========================================================================
    /**
     * Real-world DANA notification patterns:
     * - "Pembayaran Rp35.000 di Kopi Kenangan berhasil!"
     * - "Kamu berhasil kirim uang Rp50.000 ke ShopeePay (08123456789)"
     * - "Berhasil bayar Rp15.000 pakai QRIS DANA di Warung Madura"
     * - "Kirim uang ke BCA (1234567890) sebesar Rp100.000 berhasil"
     */
    private fun parseDana(content: String): TransactionEntity? {
        val lower = content.lowercase()

        if (isGenericPromoOrIncoming(lower)) return null
        if (lower.contains("dana kaget") || lower.contains("top up") ||
            lower.contains("kamu menerima") || lower.contains("menerima uang") ||
            lower.contains("buka amplop")
        ) {
            return null
        }

        val isExpense = lower.contains("pembayaran") || lower.contains("bayar") ||
                lower.contains("kirim uang") || lower.contains("berhasil kirim") ||
                lower.contains("qris")

        if (!isExpense) return null

        val amount = extractAmount(content) ?: return null

        val pattern = Pattern.compile(
            "\\b(?:di|ke)\\b\\s+([A-Za-z0-9\\s&'.-]{2,30}?)(?:\\s+(?:berhasil|pakai|sebesar|\\()|[.,!]|$)",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(content)

        val merchant = if (matcher.find()) {
            matcher.group(1)?.trim() ?: "DANA Merchant"
        } else {
            "DANA Merchant"
        }

        return buildExpense(amount, cleanMerchant(merchant), "DANA", content)
    }

    // =========================================================================
    // 5. BCA MOBILE (com.bca / id.co.bca)
    // =========================================================================
    /**
     * Real-world BCA notification patterns:
     * - "Transaksi QRIS sebesar Rp45.000 di Chatime berhasil."
     * - "m-Transfer ke rek 1234567890 AN BUDI sebesar Rp100.000 berhasil."
     * - "m-Transfer ke BCA 0987654321 Rp50.000 Berhasil"
     * - "Pembayaran ke PLN Prabayar Rp150.000 Berhasil"
     */
    private fun parseBca(content: String): TransactionEntity? {
        val lower = content.lowercase()

        if (isGenericPromoOrIncoming(lower)) return null
        if (lower.contains("dana masuk") || lower.contains("transfer masuk") ||
            lower.contains("cr ") || lower.contains("kredit")
        ) {
            return null
        }

        val isExpense = lower.contains("transaksi qris") || lower.contains("m-transfer") ||
                lower.contains("transfer ke") || lower.contains("pembayaran") ||
                lower.contains("debit")

        if (!isExpense) return null

        val amount = extractAmount(content) ?: return null

        val pattern = Pattern.compile(
            "\\b(?:di|ke|a\\.n\\.|an|rek)\\b\\s+([A-Za-z0-9\\s&'.-]{2,30}?)(?:\\s+(?:sebesar|berhasil|rp)|[.,!]|$)",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(content)

        val merchant = if (matcher.find()) {
            matcher.group(1)?.trim() ?: "BCA Merchant"
        } else {
            "BCA Merchant"
        }

        return buildExpense(amount, cleanMerchant(merchant), "m-BCA", content)
    }

    // =========================================================================
    // 6. BRIMO (id.co.bri.brimo)
    // =========================================================================
    /**
     * Real-world BRImo notification patterns:
     * - "Pembayaran QRIS Rp25.000 di Indomaret berhasil."
     * - "Transfer ke Bank BCA rek 1234567890 an AHMAD sebesar Rp150.000 berhasil."
     * - "Beli Pulsa/Paket Data Rp50.000 Berhasil"
     * - "Pembayaran BRIVA Rp85.000 ke Tokopedia Berhasil"
     * - "Transaksi Debit Rp70.000 di SUPERINDO berhasil"
     */
    private fun parseBriMo(content: String): TransactionEntity? {
        val lower = content.lowercase()

        if (isGenericPromoOrIncoming(lower)) return null
        if (lower.contains("transfer masuk") || lower.contains("dana masuk") ||
            lower.contains("cr ") || lower.contains("kredit")
        ) {
            return null
        }

        val isExpense = lower.contains("pembayaran") || lower.contains("transfer ke") ||
                lower.contains("beli") || lower.contains("briva") ||
                lower.contains("transaksi debit") || lower.contains("qris")

        if (!isExpense) return null

        val amount = extractAmount(content) ?: return null

        val merchant = when {
            lower.contains("pulsa") || lower.contains("paket data") -> "Pulsa / Paket Data"
            lower.contains("listrik") || lower.contains("pln") -> "PLN Listrik"
            else -> {
                val pattern = Pattern.compile(
                    "\\b(?:di|ke|a\\.n\\.|an|pada)\\b\\s+([A-Za-z0-9\\s&'.-]{2,30}?)(?:\\s+(?:sebesar|berhasil|rp)|[.,!]|$)",
                    Pattern.CASE_INSENSITIVE
                )
                val matcher = pattern.matcher(content)
                if (matcher.find()) {
                    matcher.group(1)?.trim() ?: "BRImo Merchant"
                } else {
                    "BRImo Merchant"
                }
            }
        }

        return buildExpense(amount, cleanMerchant(merchant), "BRImo", content)
    }

    // =========================================================================
    // 7. SEABANK (com.bke.seabank)
    // =========================================================================
    /**
     * Real-world SeaBank notification patterns:
     * - "Kamu berhasil transfer Rp75.000 ke ShopeePay (08123456789)."
     * - "Transfer Rp100.000 ke BCA AN SITI berhasil."
     * - "Pembayaran QRIS sebesar Rp32.000 di Mixue Sukses."
     * - "Pembayaran Virtual Account Rp60.000 ke Shopee Berhasil."
     */
    private fun parseSeaBank(content: String): TransactionEntity? {
        val lower = content.lowercase()

        if (isGenericPromoOrIncoming(lower)) return null
        if (lower.contains("transfer masuk") || lower.contains("kamu menerima") ||
            lower.contains("bunga harian") || lower.contains("deposito")
        ) {
            return null
        }

        val isExpense = lower.contains("transfer") || lower.contains("pembayaran qris") ||
                lower.contains("virtual account") || lower.contains("sukses") || lower.contains("berhasil")

        if (!isExpense) return null

        val amount = extractAmount(content) ?: return null

        val pattern = Pattern.compile(
            "\\b(?:ke|di)\\b\\s+([A-Za-z0-9\\s&'.-]{2,30}?)(?:\\s+(?:sebesar|sukses|berhasil|\\()|[.,!]|$)",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(content)

        val merchant = if (matcher.find()) {
            matcher.group(1)?.trim() ?: "SeaBank Merchant"
        } else {
            "SeaBank Merchant"
        }

        return buildExpense(amount, cleanMerchant(merchant), "SeaBank", content)
    }

    // =========================================================================
    // SIMULATOR PARSER (For in-app testing)
    // =========================================================================
    private fun parseSimulator(title: String, content: String): TransactionEntity? {
        val amount = extractAmount(content) ?: 25000.0
        val merchant = when {
            content.contains("Kopi", ignoreCase = true) -> "Kopi Janji Jiwa"
            content.contains("Alfamart", ignoreCase = true) -> "Alfamart"
            content.contains("Chatime", ignoreCase = true) -> "Chatime"
            content.contains("Mie Gacoan", ignoreCase = true) -> "Mie Gacoan"
            content.contains("Solaria", ignoreCase = true) -> "Solaria"
            else -> "Finzy Simulator Merchant"
        }
        val source = if (title.isNotBlank()) title else "Finzy Simulator"
        return buildExpense(amount, merchant, source, content)
    }

    // =========================================================================
    // HELPER FUNCTIONS
    // =========================================================================

    /**
     * Extracts numerical amount from currency formats (e.g., Rp25.000, Rp 50,000, IDR 100.000).
     */
    fun extractAmount(text: String): Double? {
        val pattern = Pattern.compile(
            "(?:rp|idr|sebesar)[\\s.]*([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]{4,})",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(text)

        return if (matcher.find()) {
            val raw = matcher.group(1) ?: return null
            val cleaned = raw.replace(".", "").replace(",", "")
            cleaned.toDoubleOrNull()?.takeIf { it > 0.0 }
        } else {
            // Secondary fallback for simple unformatted integers (e.g. "Rp50000")
            val simplePattern = Pattern.compile("(?:rp|idr)[\\s.]*([0-9]+)", Pattern.CASE_INSENSITIVE)
            val simpleMatcher = simplePattern.matcher(text)
            if (simpleMatcher.find()) {
                simpleMatcher.group(1)?.toDoubleOrNull()?.takeIf { it > 0.0 }
            } else null
        }
    }

    /**
     * Cleans extraneous characters from parsed merchant name.
     */
    private fun cleanMerchant(raw: String): String {
        return raw.split("(", ")", "sebesar", "telah", "menggunakan", "pakai", "\n")
            .firstOrNull()?.trim()
            ?.takeIf { it.isNotBlank() } ?: "E-Wallet Merchant"
    }

    /**
     * Generic blacklist to filter out marketing promos and incoming notifications.
     */
    private fun isGenericPromoOrIncoming(lower: String): Boolean {
        return lower.contains("promo") || lower.contains("diskon") ||
                lower.contains("cashback") || lower.contains("voucher") ||
                lower.contains("bunga") || lower.contains("pinjaman") ||
                lower.contains("selamat!") || lower.contains("dapatkan")
    }

    /**
     * Builds standard TransactionEntity assigned to CategoryEntity.UNCATEGORIZED_ID (1L).
     */
    private fun buildExpense(
        amount: Double,
        merchant: String,
        sourceApp: String,
        rawContent: String
    ): TransactionEntity {
        return TransactionEntity(
            amount = amount,
            merchant = merchant,
            timestamp = System.currentTimeMillis(),
            categoryId = CategoryEntity.UNCATEGORIZED_ID,
            isManual = false,
            sourceApp = sourceApp,
            note = "Terdeteksi dari $sourceApp: \"$rawContent\""
        )
    }

    // =========================================================================
    // ANDROID LIFECYCLE BUG & REBIND HELPER EXPLANATION
    // =========================================================================
    /**
     * Explanation & Fix for Android NotificationListenerService dev lifecycle bug:
     *
     * 1. THE PROBLEM:
     * When an app with NotificationListenerService is updated, recompiled by Android Studio / ADB,
     * or when its process is terminated, Android's internal `NotificationManagerService` often loses
     * the active IBinder connection to the service. Even though the system settings still show
     * "Notification Access: Allowed", `onListenerConnected()` is never invoked again until the device
     * reboots or the user manually toggles the permission off and on.
     *
     * 2. THE SOLUTION:
     * A) API 24+ `requestRebind()`:
     *    Call `NotificationListenerService.requestRebind(ComponentName)` from the service itself.
     * B) Component Toggle Trick (Works universally):
     *    Toggle the service component disabled and then immediately enabled using `PackageManager`.
     *    This notifies Android's NotificationManagerService to immediately re-bind the listener!
     */
    fun rebindNotificationListener(context: Context) {
        try {
            val componentName = ComponentName(context, TransactionNotificationListener::class.java)

            // Solution A: On Android 7.0 (API 24)+, requestRebind can be called directly
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                NotificationListenerService.requestRebind(componentName)
                Log.d(TAG, "NotificationListenerService.requestRebind() requested successfully.")
            }

            // Solution B: Component toggle trick to force system re-bind
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.d(TAG, "Component re-toggle executed successfully to fix Android listener binder bug.")
        } catch (e: Exception) {
            Log.e(TAG, "Error rebinding NotificationListener: ${e.message}", e)
        }
    }
}
