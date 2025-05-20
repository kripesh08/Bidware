package com.bidware.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.bidware.models.Sale
import com.bidware.models.Payment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.Random

object FirebaseUtils {

    private const val TAG = "FirebaseUtils"
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun getSalesBySellerId(userId: String?): List<Sale> {
        if (userId == null) return emptyList()
        return try {
            val querySnapshot = db.collection("sales")
                .whereEqualTo("sellerId", userId)
                .get()
                .await()
            querySnapshot.documents.mapNotNull { document ->
                Sale.fromDocument(document)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user sales", e)
            emptyList()
        }
    }
    
    suspend fun getWishlistByUserId(userId: String?): List<Sale> {
        if (userId == null) return emptyList()
        return try {
            val userDoc = db.collection("users").document(userId).get().await()
            val wishlistIds = getStringListSafely(userDoc.get("wishlist"))
            if (wishlistIds.isEmpty()) return emptyList()

            val querySnapshot = db.collection("sales")
                .whereIn("id", wishlistIds)
                .get()
                .await()
            querySnapshot.documents.mapNotNull { document ->
                Sale.fromDocument(document)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting wishlist sales", e)
            emptyList()
        }
    }

    suspend fun getUserPurchases(userId: String?): List<Sale> {
        if (userId == null) return emptyList()
        return try {
            // Get only completed sales where user is the current highest bidder
            val querySnapshot = db.collection("sales")
                .whereEqualTo("currentBidder", userId)
                .whereEqualTo("status", "completed")
                .get()
                .await()
            
            querySnapshot.documents.mapNotNull { document ->
                Sale.fromDocument(document)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user purchases", e)
            emptyList()
        }
    }

    private fun getStringListSafely(value: Any?): List<String> {
        return when (value) {
            is List<*> -> value.filterIsInstance<String>()
            else -> emptyList()
        }
    }

    private fun getMutableStringListSafely(value: Any?): MutableList<String> {
        return when (value) {
            is List<*> -> value.filterIsInstance<String>().toMutableList()
            else -> mutableListOf()
        }
    }

    suspend fun getAllActiveSales(): List<Sale> {
        return try {
            val currentUserId = getCurrentUserId() ?: return emptyList()
            
            // Get only in_auction sales, excluding the current user's sales
            val querySnapshot = db.collection("sales")
                .whereEqualTo("status", "in_auction")
                .whereNotEqualTo("sellerId", currentUserId)
                .get()
                .await()
            
            querySnapshot.documents.mapNotNull { document ->
                Sale.fromDocument(document)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all active sales", e)
            emptyList()
        }
    }

    suspend fun getSaleById(saleId: String): Sale? {
        return try {
            val documentSnapshot = db.collection("sales").document(saleId).get().await()
            if (documentSnapshot.exists()) {
                Sale.fromDocument(documentSnapshot)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sale by ID", e)
            null
        }
    }

    suspend fun addSale(sale: Sale) = try {
        db.collection("sales").add(sale.toMap())
    } catch (e: Exception) {
        Log.e(TAG, "Error adding sale", e)
        throw e // Re-throw to be handled by caller
    }

    suspend fun updateSale(sale: Sale): Boolean {
        return try {
            if (sale.id.isNotEmpty()) {
                // If seller details are missing, fetch them
                if (sale.sellerName.isEmpty() || sale.sellerPhone.isEmpty() || sale.sellerEmail.isEmpty()) {
                    val sellerDoc = db.collection("users").document(sale.sellerId).get().await()
                    val sellerData = sellerDoc.data
                    
                    if (sellerData != null) {
                        val updatedSale = sale.copy(
                            sellerName = sale.sellerName.ifEmpty { sellerData["fullName"] as? String ?: "" },
                            sellerPhone = sale.sellerPhone.ifEmpty { sellerData["mobile"] as? String ?: "" },
                            sellerEmail = sale.sellerEmail.ifEmpty { sellerData["email"] as? String ?: "" },
                            sellerAadhar = sale.sellerAadhar.ifEmpty { sellerData["aadhar"] as? String ?: "" }
                        )
                        db.collection("sales").document(sale.id).set(updatedSale.toMap()).await()
                    } else {
                        db.collection("sales").document(sale.id).set(sale.toMap()).await()
                    }
                } else {
                    db.collection("sales").document(sale.id).set(sale.toMap()).await()
                }
                true
            } else {
                Log.e(TAG, "Cannot update sale with empty ID")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating sale", e)
            false
        }
    }

    suspend fun decodeBase64(base64String: String): Bitmap? {
        try {
            Log.d(TAG, "Decoding base64 string of length: ${base64String.length}")
            
            // Clean the base64 string if it contains format prefix
            val cleanBase64 = if (base64String.contains(",")) {
                base64String.substring(base64String.indexOf(",") + 1)
            } else {
                base64String
            }
            
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            Log.d(TAG, "Successfully decoded ${decodedBytes.size} bytes")
            
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from byte array")
            }
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding Base64 string", e)
            return null
        }
    }

    suspend fun updateSaleStatus(sale: Sale, status: String): Boolean {
        return try {
            if (sale.id.isNotEmpty()) {
                db.collection("sales").document(sale.id).update("status", status).await()
                true
            } else {
                Log.e(TAG, "Cannot update sale status with empty ID")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating sale status", e)
            false
        }
    }

    fun calculateServiceFee(amount: Double): Double {
        // Fixed service fee of ₹700
        return 700.0
    }

    // Payment related functions
    suspend fun createPayment(saleId: String, userId: String, amount: Double): Payment {
        val payment = Payment(
            id = UUID.randomUUID().toString(),
            saleId = saleId,
            userId = userId,
            amount = amount,
            paymentMethod = "razorpay"
        )
        
        try {
            db.collection("payments").document(payment.id).set(payment.toMap()).await()
            return payment
        } catch (e: Exception) {
            Log.e(TAG, "Error creating payment", e)
            throw e
        }
    }

    suspend fun updatePaymentStatus(paymentId: String, status: String, razorpayOrderId: String = "", errorMessage: String = "") {
        try {
            val updates = mutableMapOf<String, Any>(
                "status" to status,
                "completedAt" to System.currentTimeMillis()
            )
            
            if (razorpayOrderId.isNotEmpty()) {
                updates["razorpayOrderId"] = razorpayOrderId
            }
            
            if (errorMessage.isNotEmpty()) {
                updates["errorMessage"] = errorMessage
            }
            
            db.collection("payments").document(paymentId).update(updates).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating payment status", e)
            throw e
        }
    }

    suspend fun getPayment(paymentId: String): Payment? {
        return try {
            val document = db.collection("payments").document(paymentId).get().await()
            if (document.exists()) {
                Payment.fromMap(document.data!!)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting payment", e)
            null
        }
    }

    suspend fun getPaymentsBySaleId(saleId: String): List<Payment> {
        return try {
            val snapshot = db.collection("payments")
                .whereEqualTo("saleId", saleId)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                Payment.fromMap(doc.data!!)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting payments by sale ID", e)
            emptyList()
        }
    }

    suspend fun verifyPayment(paymentId: String): Boolean {
        val payment = getPayment(paymentId) ?: return false
        return payment.status == "completed"
    }

    // Add this new method to update just the Razorpay payment ID
    suspend fun updatePaymentRazorpayId(paymentId: String, razorpayId: String) {
        val paymentRef = db.collection("payments").document(paymentId)
        
        val updates = hashMapOf<String, Any>(
            "razorpayOrderId" to razorpayId
        )
        
        paymentRef.update(updates).await()
    }

    suspend fun getAllSales(): List<Sale> {
        return try {
            val querySnapshot = db.collection("sales")
                .get()
                .await()
            querySnapshot.documents.mapNotNull { document ->
                Sale.fromDocument(document)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all sales", e)
            emptyList()
        }
    }

    suspend fun uploadImage(imageBytes: ByteArray): String {
        return try {
            // Convert bytes to base64
            val base64String = Base64.encodeToString(imageBytes, Base64.DEFAULT)
            "data:image/jpeg;base64,$base64String"
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding image to base64", e)
            throw e
        }
    }

    suspend fun uploadDocument(documentBytes: ByteArray, type: String): String {
        return try {
            // Convert bytes to base64
            val base64String = Base64.encodeToString(documentBytes, Base64.DEFAULT)
            "data:application/pdf;base64,$base64String"
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding document to base64", e)
            throw e
        }
    }

    suspend fun createSale(sale: Sale): String {
        return try {
            // Get seller details from users collection
            val userId = getCurrentUserId() ?: throw Exception("User not authenticated")
            val userDoc = db.collection("users").document(userId).get().await()
            val userData = userDoc.data
            
            // Extract seller information
            val sellerName = userData?.get("fullName") as? String ?: ""
            val sellerPhone = userData?.get("mobile") as? String ?: ""
            val sellerEmail = userData?.get("email") as? String ?: ""
            val sellerAadhar = userData?.get("aadhar") as? String ?: ""
            
            // Create a new document with auto-generated ID
            val docRef = db.collection("sales").document()
            
            // Update the sale object with the new ID and seller details
            val saleWithDetails = sale.copy(
                id = docRef.id,
                sellerName = sellerName,
                sellerPhone = sellerPhone,
                sellerEmail = sellerEmail,
                sellerAadhar = sellerAadhar
            )
            
            // Set the document data
            docRef.set(saleWithDetails.toMap()).await()
            
            // Return the new document ID
            docRef.id
        } catch (e: Exception) {
            Log.e(TAG, "Error creating sale", e)
            throw e
        }
    }

    suspend fun getSaleWithPayments(saleId: String): Pair<Sale?, List<Payment>> {
        return try {
            // Get sale details
            val sale = getSaleById(saleId)
            
            // Get all payments for this sale
            val payments = if (sale != null) {
                val snapshot = db.collection("payments")
                    .whereEqualTo("saleId", saleId)
                    .get()
                    .await()
                
                snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { Payment.fromMap(it) }
                }.sortedByDescending { it.createdAt } // Sort by most recent first
            } else {
                emptyList()
            }
            
            Pair(sale, payments)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sale with payments", e)
            Pair(null, emptyList())
        }
    }

    suspend fun getPaymentHistory(userId: String): List<Triple<Sale, Payment, String>> {
        return try {
            // Get all payments made by this user
            val paymentsSnapshot = db.collection("payments")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            val paymentsList = mutableListOf<Triple<Sale, Payment, String>>()
            
            for (paymentDoc in paymentsSnapshot.documents) {
                val payment = paymentDoc.data?.let { Payment.fromMap(it) } ?: continue
                val sale = getSaleById(payment.saleId) ?: continue
                
                // Determine payment type (buyer service fee or seller service fee)
                val paymentType = if (sale.sellerId == userId) "Seller Service Fee" else "Buyer Service Fee"
                
                paymentsList.add(Triple(sale, payment, paymentType))
            }
            
            // Sort by most recent first
            paymentsList.sortedByDescending { it.second.createdAt }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting payment history", e)
            emptyList()
        }
    }

    suspend fun deleteSale(saleId: String): Boolean {
        return try {
            if (saleId.isNotEmpty()) {
                // Delete the sale document
                db.collection("sales").document(saleId).delete().await()
                
                // Also delete any associated payments
                val paymentsSnapshot = db.collection("payments")
                    .whereEqualTo("saleId", saleId)
                    .get()
                    .await()
                
                for (paymentDoc in paymentsSnapshot.documents) {
                    paymentDoc.reference.delete().await()
                }
                
                true
            } else {
                Log.e(TAG, "Cannot delete sale with empty ID")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting sale", e)
            false
        }
    }

    suspend fun updateSaleWithTransaction(sale: Sale): Boolean {
        return try {
            val db = FirebaseFirestore.getInstance()
            val saleRef = db.collection("sales").document(sale.id)
            
            db.runTransaction { transaction ->
                val snapshot = transaction.get(saleRef)
                val currentBid = snapshot.getDouble("currentBid") ?: 0.0
                val currentBidTimestamp = snapshot.getLong("currentBidTimestamp") ?: 0L
                val currentBidRandom = when (val value = snapshot.data?.get("currentBidRandom")) {
                    is Number -> value.toLong()
                    else -> 0L
                }
                val newBidTimestamp = System.currentTimeMillis()
                val newBidRandom = Random().nextLong() // Generate random number for tiebreaker
                
                // Update if:
                // 1. New bid is higher than current bid, OR
                // 2. Bids are equal but new bid has earlier timestamp, OR
                // 3. Bids are equal and timestamps are equal but new bid has lower random number
                if (sale.currentBid > currentBid || 
                    (sale.currentBid == currentBid && newBidTimestamp < currentBidTimestamp) ||
                    (sale.currentBid == currentBid && newBidTimestamp == currentBidTimestamp && 
                     newBidRandom < currentBidRandom)) {
                    
                    // Add timestamp and random number to the update
                    val updateData = sale.toMap().toMutableMap()
                    updateData["currentBidTimestamp"] = newBidTimestamp
                    updateData["currentBidRandom"] = newBidRandom
                    
                    transaction.set(saleRef, updateData)
                    true
                } else {
                    false
                }
            }.await()
            
            true
        } catch (e: Exception) {
            Log.e("FirebaseUtils", "Error updating sale with transaction", e)
            false
        }
    }
} 