package com.bidware.models

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Exclude
import kotlinx.parcelize.Parcelize
import java.text.NumberFormat
import java.util.*

@Parcelize
data class Sale(
    @get:Exclude val id: String = "",
    val sellerId: String = "",
    val title: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val status: String = "pending", // pending, approved, waiting_for_start, in_auction, sold, rejected, completed
    val createdAt: Timestamp = Timestamp.now(),
    
    // Vehicle specific details
    val brand: String = "",
    val model: String = "",
    val year: Int = 0,
    val kilometers: Int = 0,
    val fuelType: String = "",
    val vehicleNumber: String = "",
    val location: String = "",
    
    // Auction details
    val startDate: Timestamp = Timestamp.now(),
    val endDate: Timestamp = Timestamp.now(),
    val highestBid: Double = 0.0,
    val highestBidderId: String = "",
    
    // Media
    val imageUrl: String = "",
    val rcBookUrl: String = "",
    val insuranceUrl: String = "",
    val rejectionComments: String = "",
    val currentBid: Double = 0.0,
    val currentBidder: String = "",
    val currentBidTimestamp: Long = 0L, // Timestamp when the current bid was placed
    val currentBidRandom: Long = 0L, // Random number for tiebreaking simultaneous bids
    
    // Payments and contacts
    val buyerPaid: Boolean = false,
    val sellerPhone: String = "",
    val sellerEmail: String = "",
    val buyerPhone: String = "",
    val buyerEmail: String = "",
    val sellerName: String = "",
    val sellerAadhar: String = "",
    val buyerName: String = "",
    val buyerAadhar: String = ""
) : Parcelable {

    fun toMap(): Map<String, Any> {
        return mapOf(
            "sellerId" to sellerId,
            "title" to title,
            "description" to description,
            "price" to price,
            "status" to status,
            "createdAt" to createdAt,
            "brand" to brand,
            "model" to model,
            "year" to year,
            "kilometers" to kilometers,
            "fuelType" to fuelType,
            "vehicleNumber" to vehicleNumber,
            "location" to location,
            "startDate" to startDate,
            "endDate" to endDate,
            "highestBid" to highestBid,
            "highestBidderId" to highestBidderId,
            "imageUrl" to imageUrl,
            "rcBookUrl" to rcBookUrl,
            "insuranceUrl" to insuranceUrl,
            "rejectionComments" to rejectionComments,
            "currentBid" to currentBid,
            "currentBidder" to currentBidder,
            "currentBidTimestamp" to currentBidTimestamp,
            "currentBidRandom" to currentBidRandom,
            "buyerPaid" to buyerPaid,
            "sellerPhone" to sellerPhone,
            "sellerEmail" to sellerEmail,
            "buyerPhone" to buyerPhone,
            "buyerEmail" to buyerEmail,
            "sellerName" to sellerName,
            "sellerAadhar" to sellerAadhar,
            "buyerName" to buyerName,
            "buyerAadhar" to buyerAadhar
        )
    }

    companion object {
        fun fromDocument(document: DocumentSnapshot): Sale? {
            return try {
                val data = document.data ?: return null
                
                // Get timestamp values or create default timestamps
                val createdAt = data["createdAt"] as? Timestamp ?: Timestamp.now()
                val startDate = data["startDate"] as? Timestamp 
                    ?: (data["startDate"] as? Long)?.let { Timestamp(Date(it)) }
                    ?: Timestamp.now()
                val endDate = data["endDate"] as? Timestamp 
                    ?: (data["endDate"] as? Long)?.let { Timestamp(Date(it)) } 
                    ?: Timestamp.now()
                
                Sale(
                    id = document.id,
                    sellerId = data["sellerId"] as? String ?: "",
                    title = data["title"] as? String ?: "",
                    description = data["description"] as? String ?: "",
                    price = (data["price"] as? Number)?.toDouble() ?: 0.0,
                    status = data["status"] as? String ?: "pending",
                    createdAt = createdAt,
                    brand = data["brand"] as? String ?: "",
                    model = data["model"] as? String ?: "",
                    year = (data["year"] as? Number)?.toInt() ?: 0,
                    kilometers = (data["kilometers"] as? Number)?.toInt() ?: 0,
                    fuelType = data["fuelType"] as? String ?: "",
                    vehicleNumber = data["vehicleNumber"] as? String ?: "",
                    location = data["location"] as? String ?: "",
                    startDate = startDate,
                    endDate = endDate,
                    highestBid = (data["highestBid"] as? Number)?.toDouble() ?: 0.0,
                    highestBidderId = data["highestBidderId"] as? String ?: "",
                    imageUrl = data["imageUrl"] as? String ?: "",
                    rcBookUrl = data["rcBookUrl"] as? String ?: "",
                    insuranceUrl = data["insuranceUrl"] as? String ?: "",
                    rejectionComments = data["rejectionComments"] as? String ?: "",
                    currentBid = (data["currentBid"] as? Number)?.toDouble() ?: 0.0,
                    currentBidder = data["currentBidder"] as? String ?: "",
                    currentBidTimestamp = (data["currentBidTimestamp"] as? Number)?.toLong() ?: 0L,
                    currentBidRandom = (data["currentBidRandom"] as? Number)?.toLong() ?: 0L,
                    buyerPaid = data["buyerPaid"] as? Boolean ?: false,
                    sellerPhone = data["sellerPhone"] as? String ?: "",
                    sellerEmail = data["sellerEmail"] as? String ?: "",
                    buyerPhone = data["buyerPhone"] as? String ?: "",
                    buyerEmail = data["buyerEmail"] as? String ?: "",
                    sellerName = data["sellerName"] as? String ?: "",
                    sellerAadhar = data["sellerAadhar"] as? String ?: "",
                    buyerName = data["buyerName"] as? String ?: "",
                    buyerAadhar = data["buyerAadhar"] as? String ?: ""
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        
        fun fromMap(data: Map<String, Any>, id: String = ""): Sale {
            return Sale(
                id = id,
                sellerId = data["sellerId"] as? String ?: "",
                title = data["title"] as? String ?: "",
                description = data["description"] as? String ?: "",
                price = (data["price"] as? Number)?.toDouble() ?: 0.0,
                status = data["status"] as? String ?: "pending",
                createdAt = (data["createdAt"] as? Timestamp) 
                    ?: (data["createdAt"] as? Long)?.let { Timestamp(Date(it)) }
                    ?: Timestamp.now(),
                brand = data["brand"] as? String ?: "",
                model = data["model"] as? String ?: "",
                year = (data["year"] as? Number)?.toInt() ?: 0,
                kilometers = (data["kilometers"] as? Number)?.toInt() ?: 0,
                fuelType = data["fuelType"] as? String ?: "",
                vehicleNumber = data["vehicleNumber"] as? String ?: "",
                location = data["location"] as? String ?: "",
                startDate = (data["startDate"] as? Timestamp)
                    ?: (data["startDate"] as? Long)?.let { Timestamp(Date(it)) }
                    ?: Timestamp.now(),
                endDate = (data["endDate"] as? Timestamp)
                    ?: (data["endDate"] as? Long)?.let { Timestamp(Date(it)) }
                    ?: Timestamp.now(),
                highestBid = (data["highestBid"] as? Number)?.toDouble() ?: 0.0,
                highestBidderId = data["highestBidderId"] as? String ?: "",
                imageUrl = data["imageUrl"] as? String ?: "",
                rcBookUrl = data["rcBookUrl"] as? String ?: "",
                insuranceUrl = data["insuranceUrl"] as? String ?: "",
                rejectionComments = data["rejectionComments"] as? String ?: "",
                currentBid = (data["currentBid"] as? Number)?.toDouble() ?: 0.0,
                currentBidder = data["currentBidder"] as? String ?: "",
                currentBidTimestamp = (data["currentBidTimestamp"] as? Number)?.toLong() ?: 0L,
                currentBidRandom = (data["currentBidRandom"] as? Number)?.toLong() ?: 0L,
                buyerPaid = data["buyerPaid"] as? Boolean ?: false,
                sellerPhone = data["sellerPhone"] as? String ?: "",
                sellerEmail = data["sellerEmail"] as? String ?: "",
                buyerPhone = data["buyerPhone"] as? String ?: "",
                buyerEmail = data["buyerEmail"] as? String ?: "",
                sellerName = data["sellerName"] as? String ?: "",
                sellerAadhar = data["sellerAadhar"] as? String ?: "",
                buyerName = data["buyerName"] as? String ?: "",
                buyerAadhar = data["buyerAadhar"] as? String ?: ""
            )
        }
        
        private fun getSafeStringList(value: Any?): List<String> {
            return when (value) {
                is List<*> -> value.filterIsInstance<String>()
                else -> emptyList()
            }
        }
    }
    
    val isAuctionStarted: Boolean
        get() = System.currentTimeMillis() >= startDate.seconds * 1000
        
    val isAuctionEnded: Boolean
        get() = System.currentTimeMillis() >= endDate.seconds * 1000
        
    val formattedStatus: String
        get() = when (status) {
            "pending" -> "Pending Approval"
            "approved" -> "Approved"
            "waiting_for_start" -> "Waiting to Start"
            "in_auction" -> "In Auction"
            "sold" -> "Sold"
            "rejected" -> "Rejected"
            "completed" -> "Completed"
            else -> status.replaceFirstChar { it.uppercase() }
        }
    
    val displayPrice: String
        get() = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(price)
        
    val displayHighestBid: String
        get() = if (highestBid > 0) NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(highestBid) else "No bids yet"
}

// Factory pattern for creating sale objects, makes code more concise
class SaleBuilder {
    private val sale = Sale()
    private val mutableMap = mutableMapOf<String, Any>()
    
    fun id(id: String) = apply { mutableMap["id"] = id }
    fun sellerId(sellerId: String) = apply { mutableMap["sellerId"] = sellerId }
    fun title(title: String) = apply { mutableMap["title"] = title }
    fun description(description: String) = apply { mutableMap["description"] = description }
    fun price(price: Double) = apply { mutableMap["price"] = price }
    fun status(status: String) = apply { mutableMap["status"] = status }
    fun createdAt(createdAt: Timestamp) = apply { mutableMap["createdAt"] = createdAt }
    fun brand(brand: String) = apply { mutableMap["brand"] = brand }
    fun model(model: String) = apply { mutableMap["model"] = model }
    fun year(year: Int) = apply { mutableMap["year"] = year }
    fun kilometers(kilometers: Int) = apply { mutableMap["kilometers"] = kilometers }
    fun fuelType(fuelType: String) = apply { mutableMap["fuelType"] = fuelType }
    fun vehicleNumber(vehicleNumber: String) = apply { mutableMap["vehicleNumber"] = vehicleNumber }
    fun location(location: String) = apply { mutableMap["location"] = location }
    fun startDate(startDate: Timestamp) = apply { mutableMap["startDate"] = startDate }
    fun endDate(endDate: Timestamp) = apply { mutableMap["endDate"] = endDate }
    fun imageUrl(imageUrl: String) = apply { mutableMap["imageUrl"] = imageUrl }
    fun rcBookUrl(rcBookUrl: String) = apply { mutableMap["rcBookUrl"] = rcBookUrl }
    fun insuranceUrl(insuranceUrl: String) = apply { mutableMap["insuranceUrl"] = insuranceUrl }
    fun rejectionComments(rejectionComments: String) = apply { mutableMap["rejectionComments"] = rejectionComments }
    fun currentBid(currentBid: Double) = apply { mutableMap["currentBid"] = currentBid }
    fun currentBidder(currentBidder: String) = apply { mutableMap["currentBidder"] = currentBidder }
    fun currentBidTimestamp(currentBidTimestamp: Long) = apply { mutableMap["currentBidTimestamp"] = currentBidTimestamp }
    fun currentBidRandom(currentBidRandom: Long) = apply { mutableMap["currentBidRandom"] = currentBidRandom }
    fun buyerPaid(buyerPaid: Boolean) = apply { mutableMap["buyerPaid"] = buyerPaid }
    fun sellerPhone(sellerPhone: String) = apply { mutableMap["sellerPhone"] = sellerPhone }
    fun sellerEmail(sellerEmail: String) = apply { mutableMap["sellerEmail"] = sellerEmail }
    fun buyerPhone(buyerPhone: String) = apply { mutableMap["buyerPhone"] = buyerPhone }
    fun buyerEmail(buyerEmail: String) = apply { mutableMap["buyerEmail"] = buyerEmail }
    fun sellerName(sellerName: String) = apply { mutableMap["sellerName"] = sellerName }
    fun sellerAadhar(sellerAadhar: String) = apply { mutableMap["sellerAadhar"] = sellerAadhar }
    fun buyerName(buyerName: String) = apply { mutableMap["buyerName"] = buyerName }
    fun buyerAadhar(buyerAadhar: String) = apply { mutableMap["buyerAadhar"] = buyerAadhar }
    
    fun build(): Sale = Sale.fromMap(mutableMap, mutableMap["id"] as? String ?: "")
} 