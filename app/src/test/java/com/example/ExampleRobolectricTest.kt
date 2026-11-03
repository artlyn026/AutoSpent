package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.CategoryEntity
import com.example.service.NotificationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AutoSpent", appName)
    }

    @Test
    fun `test GoPay notification parsing`() {
        val text = "Kamu sudah bayar Rp28.000 ke Kopi Kenangan pakai GoPay."
        val transaction = NotificationParser.parseByPackage(NotificationParser.PKG_GOPAY, "GoPay", text)

        assertNotNull(transaction)
        assertEquals(28000.0, transaction!!.amount, 0.01)
        assertEquals("Kopi Kenangan", transaction.merchant)
        assertEquals("GoPay", transaction.sourceApp)
        assertEquals(CategoryEntity.UNCATEGORIZED_ID, transaction.categoryId)
    }

    @Test
    fun `test ShopeePay to DANA transfer attribution`() {
        // Prevents misattribution: Must be attributed to ShopeePay, not DANA
        val text = "Transfer ke DANA sebesar Rp50.000 berhasil."
        val transaction = NotificationParser.parseByPackage(NotificationParser.PKG_SHOPEEPAY, "ShopeePay", text)

        assertNotNull(transaction)
        assertEquals(50000.0, transaction!!.amount, 0.01)
        assertEquals("ShopeePay", transaction.sourceApp)
        assertEquals("DANA", transaction.merchant)
        assertEquals(CategoryEntity.UNCATEGORIZED_ID, transaction.categoryId)
    }

    @Test
    fun `test SeaBank transfer out parsing`() {
        val text = "Kamu berhasil transfer Rp75.000 ke ShopeePay (08123456789)."
        val transaction = NotificationParser.parseByPackage(NotificationParser.PKG_SEABANK, "SeaBank", text)

        assertNotNull(transaction)
        assertEquals(75000.0, transaction!!.amount, 0.01)
        assertEquals("SeaBank", transaction.sourceApp)
        assertEquals("ShopeePay", transaction.merchant)
    }

    @Test
    fun `test BRImo QRIS payment parsing`() {
        val text = "Pembayaran QRIS Rp25.000 di Indomaret berhasil."
        val transaction = NotificationParser.parseByPackage(NotificationParser.PKG_BRIMO, "BRImo", text)

        assertNotNull(transaction)
        assertEquals(25000.0, transaction!!.amount, 0.01)
        assertEquals("BRImo", transaction.sourceApp)
        assertEquals("Indomaret", transaction.merchant)
    }

    @Test
    fun `test BCA QRIS payment parsing`() {
        val text = "Transaksi QRIS sebesar Rp45.000 di Chatime berhasil."
        val transaction = NotificationParser.parseByPackage(NotificationParser.PKG_BCA, "m-BCA", text)

        assertNotNull(transaction)
        assertEquals(45000.0, transaction!!.amount, 0.01)
        assertEquals("m-BCA", transaction.sourceApp)
        assertEquals("Chatime", transaction.merchant)
    }

    @Test
    fun `test promos and incoming transfers are ignored`() {
        // GoPay promo
        val promoText = "Diskon 50% jajan GoFood hari ini! Yuk pesan sekarang."
        assertNull(NotificationParser.parseByPackage(NotificationParser.PKG_GOPAY, "GoPay", promoText))

        // Incoming top-up
        val topUpText = "Top up saldo GoPay Rp100.000 berhasil"
        assertNull(NotificationParser.parseByPackage(NotificationParser.PKG_GOPAY, "GoPay", topUpText))

        // SeaBank interest income
        val interestText = "Bunga harianmu Rp1.250 sudah cair!"
        assertNull(NotificationParser.parseByPackage(NotificationParser.PKG_SEABANK, "SeaBank", interestText))

        // Shopee flash sale
        val flashSaleText = "Flash Sale 12.12 dimulai! Klaim gratis ongkir sekarang!"
        assertNull(NotificationParser.parseByPackage(NotificationParser.PKG_SHOPEEPAY, "ShopeePay", flashSaleText))
    }

    @Test
    fun `test TransactionNotificationListener state check and ensureServiceConnected`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // When permission is not granted, ensureServiceConnected returns false safely
        val result = com.example.service.TransactionNotificationListener.ensureServiceConnected(context)
        assertEquals(false, result)
        assertEquals(false, com.example.service.TransactionNotificationListener.isListenerActive(context))
    }

    @Test
    fun `test KeepAliveService start and ReviveWorker scheduling`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            androidx.work.WorkManager.initialize(
                context,
                androidx.work.Configuration.Builder().build()
            )
        } catch (_: Exception) {}
        com.example.service.KeepAliveService.start(context)
        com.example.service.ReviveWorker.schedule(context)
    }
}
