package com.bidware.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bidware.R
import com.bidware.models.Sale
import com.bidware.utils.FirebaseUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.text.NumberFormat
import java.util.*

private const val ADAPTER_TAG = "SaleAdapter"

/**
 * Adapter for displaying Sale items in a RecyclerView with various interaction options
 */
class SaleAdapter(
    private val onSaleClick: (Sale) -> Unit
) : ListAdapter<Sale, SaleAdapter.SaleViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Sale>() {
            override fun areItemsTheSame(oldItem: Sale, newItem: Sale): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Sale, newItem: Sale): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SaleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sale, parent, false)
        return SaleViewHolder(view)
    }

    override fun onBindViewHolder(holder: SaleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SaleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val brandText: TextView = itemView.findViewById(R.id.tvVehicleBrand)
        private val modelText: TextView = itemView.findViewById(R.id.tvVehicleModel)
        private val priceText: TextView = itemView.findViewById(R.id.tvBasePrice)
        private val yearText: TextView = itemView.findViewById(R.id.tvYear)
        private val kilometersText: TextView = itemView.findViewById(R.id.tvKilometers)
        private val statusText: TextView = itemView.findViewById(R.id.tvStatus)
        private val imageView: ImageView = itemView.findViewById(R.id.ivVehicle)
        private val btnPayment: Button = itemView.findViewById(R.id.btnPayment)
        private val currentBidText: TextView? = itemView.findViewById(R.id.tvCurrentBid)
        private val fuelTypeText: TextView = itemView.findViewById(R.id.tvFuelType)
        private val locationText: TextView = itemView.findViewById(R.id.tvLocation)

        @SuppressLint("SetTextI18n")
        fun bind(sale: Sale) {
            Log.d(ADAPTER_TAG, "Binding Sale ID: ${sale.id}, Status: ${sale.status}")
            brandText.text = sale.brand
            modelText.text = sale.model
            priceText.text = sale.displayPrice
            yearText.text = "${sale.year}"
            kilometersText.text = "${sale.kilometers} km"
            fuelTypeText.text = "Fuel: ${sale.fuelType}"
            locationText.text = "Location: ${sale.location}"
            
            // Set status text with special handling for approved sales
            if (sale.status == "approved") {
                statusText.text = "Payment Required"
            } else {
                statusText.text = sale.formattedStatus
            }
            
            // Show current bid if it exists and we're in auction
            currentBidText?.let { bidText ->
                if (sale.status == "in_auction" && sale.currentBid > 0) {
                    bidText.visibility = View.VISIBLE
                    bidText.text = "Current Bid: ${NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(sale.currentBid)}"
                } else {
                    bidText.visibility = View.GONE
                }
            }
            
            // Set color based on status
            val statusColor = when(sale.status) {
                "active" -> R.color.status_active
                "in_auction" -> R.color.status_in_auction
                "approved" -> R.color.status_waiting // Add a new color for waiting status
                "rejected" -> R.color.status_rejected
                "sold", "completed" -> R.color.status_sold
                "expired" -> R.color.status_expired
                else -> R.color.text_color
            }
            statusText.setTextColor(itemView.context.getColor(statusColor))
            
            // Set up click listeners
            itemView.setOnClickListener { onSaleClick(sale) }
            
            // Hide payment button by default
            btnPayment.visibility = View.GONE
            
            // Load single image from base64 string
            if (sale.imageUrl.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val bitmap = FirebaseUtils.decodeBase64(sale.imageUrl)
                    withContext(Dispatchers.Main) {
                        bitmap?.let {
                            imageView.setImageBitmap(it)
                        }
                    }
                }
            } else {
                // Set a placeholder if no image
                imageView.setImageResource(R.drawable.ic_image_placeholder)
            }
        }
    }
} 