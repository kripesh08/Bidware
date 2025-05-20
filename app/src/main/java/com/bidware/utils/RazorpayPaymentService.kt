package com.bidware.utils

import android.app.Activity
import android.content.Context
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import android.util.Log

class RazorpayPaymentService(private val context: Context) {
    private val checkout = Checkout()
    private val TAG = "RazorpayPaymentService"
    
    init {
        // Initialize Razorpay
        Checkout.preload(context)
        
        // Use TEST KEY for development
        checkout.setKeyID("rzp_test_1DP5mmOlF5G5ag")
        // When ready to go live, use:
        // checkout.setKeyID("rzp_live_sDhKrwnDJFxD1h")
    }

    suspend fun startPayment(
        activity: Activity,
        amount: Double,
        saleId: String,
        userName: String,
        userEmail: String,
        userPhone: String
    ): String = withContext(Dispatchers.Main) {
        val orderId = UUID.randomUUID().toString()
        
        try {
            // Ensure minimum amount is 100 INR (in paise = 10000)
            val paymentAmount = if (amount * 100 < 10000) 10000 else (amount * 100).toInt()
            
            val options = JSONObject().apply {
                put("name", "Bidware")
                put("description", "Service fee for sale: $saleId")
                put("image", "https://s3.amazonaws.com/rzp-mobile/images/rzp.png") // Default image
                put("currency", "INR")
                put("amount", paymentAmount)
                
                // Order ID is optional for testing
                // put("order_id", orderId)
                
                put("prefill", JSONObject().apply {
                    put("name", userName.ifEmpty { "Test User" })
                    put("email", userEmail.ifEmpty { "test@example.com" })
                    put("contact", userPhone.ifEmpty { "9999999999" })
                })
                
                // Set theme color
                put("theme", JSONObject().apply {
                    put("color", "#FF4081")
                })
                
                // Add test card info for easier testing
                put("notes", JSONObject().apply {
                    put("address", "Test Address")
                    put("merchant_order_id", orderId)
                })
                
                // For testing, you can use test cards with any CVV and future expiry date:
                // Card: 4111 1111 1111 1111
                // Name: Any name
                // CVV: Any 3 digits
                // Expiry: Any future date
            }
            
            Log.d(TAG, "Starting Razorpay payment with options: $options")
            
            checkout.open(activity, options)
            
            orderId
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Razorpay payment", e)
            throw e
        }
    }
} 