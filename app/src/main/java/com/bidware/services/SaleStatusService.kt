package com.bidware.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.bidware.utils.FirebaseUtils
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class SaleStatusService : Service() {
    private val TAG = "SaleStatusService"
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startStatusCheck()
        }
        return START_STICKY
    }

    private fun startStatusCheck() {
        serviceScope.launch {
            while (isRunning) {
                try {
                    checkAndUpdateSaleStatuses()
                    delay(TimeUnit.SECONDS.toMillis(30)) // Check every 30 seconds
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking sale statuses", e)
                    delay(TimeUnit.SECONDS.toMillis(5)) // Wait 5 seconds before retrying on error
                }
            }
        }
    }

    private suspend fun checkAndUpdateSaleStatuses() {
        try {
            val sales = FirebaseUtils.getAllSales()
            val currentTime = System.currentTimeMillis()
            var changesCount = 0
            
            Log.d(TAG, "Checking status of ${sales.size} sales at ${java.util.Date(currentTime)}")
            
            for (sale in sales) {
                val startTimeMs = sale.startDate.seconds * 1000
                val endTimeMs = sale.endDate.seconds * 1000
                
                when (sale.status) {
                    "waiting_for_start" -> {
                        if (currentTime >= startTimeMs) {
                            Log.d(TAG, "Updating sale ${sale.id} from waiting_for_start to in_auction")
                            FirebaseUtils.updateSale(sale.copy(status = "in_auction"))
                            changesCount++
                        }
                    }
                    "in_auction" -> {
                        if (currentTime >= endTimeMs) {
                            Log.d(TAG, "Updating sale ${sale.id} from in_auction to completed")
                            FirebaseUtils.updateSale(sale.copy(status = "completed"))
                            changesCount++
                        }
                    }
                    "approved" -> {
                        if (currentTime >= startTimeMs) {
                            Log.d(TAG, "Updating sale ${sale.id} from approved to rejected (payment not made)")
                            FirebaseUtils.updateSale(
                                sale.copy(
                                    status = "rejected",
                                    rejectionComments = "Sale was automatically rejected due to non-payment before auction start time."
                                )
                            )
                            changesCount++
                        }
                    }
                }
            }
            
            if (changesCount > 0) {
                Log.d(TAG, "Updated status of $changesCount sales")
            } else {
                Log.d(TAG, "No status updates needed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkAndUpdateSaleStatuses", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }
} 