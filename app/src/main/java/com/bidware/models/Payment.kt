package com.bidware.models

import java.util.Date

data class Payment(
    val id: String = "",
    val saleId: String = "",
    val userId: String = "",
    val amount: Double = 0.0,
    val status: String = "pending", // pending, completed, failed
    val razorpayOrderId: String = "",
    val paymentMethod: String = "razorpay",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val errorMessage: String = ""
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "saleId" to saleId,
            "userId" to userId,
            "amount" to amount,
            "status" to status,
            "razorpayOrderId" to razorpayOrderId,
            "paymentMethod" to paymentMethod,
            "createdAt" to createdAt,
            "completedAt" to completedAt,
            "errorMessage" to errorMessage
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): Payment {
            return Payment(
                id = map["id"] as? String ?: "",
                saleId = map["saleId"] as? String ?: "",
                userId = map["userId"] as? String ?: "",
                amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
                status = map["status"] as? String ?: "pending",
                razorpayOrderId = map["razorpayOrderId"] as? String ?: "",
                paymentMethod = map["paymentMethod"] as? String ?: "razorpay",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                completedAt = (map["completedAt"] as? Number)?.toLong() ?: 0L,
                errorMessage = map["errorMessage"] as? String ?: ""
            )
        }
    }
} 