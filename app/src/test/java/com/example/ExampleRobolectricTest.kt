package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.CategoryEntity
import com.example.service.NotificationParser
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
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

    @Test
    fun `test month range calculation`() {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, 2026)
            set(java.util.Calendar.MONTH, java.util.Calendar.NOVEMBER)
            set(java.util.Calendar.DAY_OF_MONTH, 15)
        }
        val (start, end) = com.example.ui.fragment.HistoryFragment.calculateMonthRange(cal)

        val calCheckStart = java.util.Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(1, calCheckStart.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(0, calCheckStart.get(java.util.Calendar.HOUR_OF_DAY))

        val calCheckEnd = java.util.Calendar.getInstance().apply { timeInMillis = end }
        assertEquals(30, calCheckEnd.get(java.util.Calendar.DAY_OF_MONTH)) // Nov has 30 days
        assertEquals(23, calCheckEnd.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(59, calCheckEnd.get(java.util.Calendar.MINUTE))
    }

    @Test
    fun `test monthly database query isolation`() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.AppDatabase::class.java
        ).allowMainThreadQueries().build()

        val catDao = db.categoryDao()
        val txDao = db.transactionDao()

        // Seed default categories
        com.example.data.AppDatabase.seedDefaultCategories(catDao)
        val categories = catDao.getCategoriesList()
        val foodCat = categories.find { it.name == "Makanan & Minuman" } ?: categories.first()

        // Nov 2026 timestamp
        val calNov = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 10, 12, 0, 0)
        }
        val novTx = com.example.data.TransactionEntity(
            amount = 35000.0,
            merchant = "Kopi Kenangan",
            timestamp = calNov.timeInMillis,
            categoryId = foodCat.id,
            sourceApp = "GoPay",
            isManual = false
        )
        txDao.insertTransaction(novTx)

        // Oct 2026 timestamp
        val calOct = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 10, 12, 0, 0)
        }
        val octTx = com.example.data.TransactionEntity(
            amount = 50000.0,
            merchant = "Supermarket",
            timestamp = calOct.timeInMillis,
            categoryId = foodCat.id,
            sourceApp = "m-BCA",
            isManual = false
        )
        txDao.insertTransaction(octTx)

        // Query Nov
        val (novStart, novEnd) = com.example.ui.fragment.HistoryFragment.calculateMonthRange(calNov)
        val novList = txDao.getTransactionsByMonth(novStart, novEnd).first()
        assertEquals(1, novList.size)
        assertEquals("Kopi Kenangan", novList[0].merchant)

        // Query Oct
        val (octStart, octEnd) = com.example.ui.fragment.HistoryFragment.calculateMonthRange(calOct)
        val octList = txDao.getTransactionsByMonth(octStart, octEnd).first()
        assertEquals(1, octList.size)
        assertEquals("Supermarket", octList[0].merchant)

        // Verify Category Spending Summary By Month
        val novSummary = txDao.getCategorySpendingSummaryByMonth(novStart, novEnd).first()
        val novFoodSummary = novSummary.find { it.categoryId == foodCat.id }
        assertNotNull(novFoodSummary)
        assertEquals(35000.0, novFoodSummary!!.totalAmount, 0.01)

        // Verify Total Spending By Month
        val novTotal = txDao.getTotalSpendingByMonth(novStart, novEnd).first()
        assertEquals(35000.0, novTotal ?: 0.0, 0.01)

        val octTotal = txDao.getTotalSpendingByMonth(octStart, octEnd).first()
        assertEquals(50000.0, octTotal ?: 0.0, 0.01)

        db.close()
    }
}

