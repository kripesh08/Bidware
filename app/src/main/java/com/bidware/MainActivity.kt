package com.bidware

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bidware.databinding.ActivityMainBinding
import com.bidware.fragments.HomeFragment
import com.bidware.fragments.MySalesFragment
import com.bidware.fragments.SaleDetailsFragment
import com.bidware.fragments.WishlistFragment
import com.bidware.services.SaleStatusService
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.razorpay.PaymentResultListener

class MainActivity : AppCompatActivity(), PaymentResultListener {

    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var binding: ActivityMainBinding
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Start the sale status service
        startService(Intent(this, SaleStatusService::class.java))

        bottomNavigation = binding.bottomNavigation
        setupBottomNavigation()

        // Set up menu button
        binding.menuButton.setOnClickListener { view ->
            showPopupMenu(view)
        }

        // Load the default fragment
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }
    }

    private fun showPopupMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_profile -> {
                    Toast.makeText(this, "Profile clicked", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, ProfileActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.action_logout -> {
                    signOut()
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.navigation_my_sales -> {
                    loadFragment(MySalesFragment())
                    true
                }
                R.id.navigation_wishlist -> {
                    loadFragment(WishlistFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    // Method to load the add sale fragment with animation
    fun navigateToAddSale() {
        val addSaleFragment = MySalesFragment.newAddSaleInstance()
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.fragmentContainer, addSaleFragment)
            .addToBackStack(null)
            .commit()
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
        // Navigate to login activity
        val intent = android.content.Intent(this, com.bidware.LoginActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // Implement Razorpay payment result callbacks
    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        Log.d("RazorpayPayment", "Payment successful with ID: $razorpayPaymentId")
        Toast.makeText(this, "Payment successful! Payment ID: $razorpayPaymentId", Toast.LENGTH_LONG).show()
        
        // Update sale status in Firestore
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        when (currentFragment) {
            is MySalesFragment -> {
                currentFragment.handlePaymentSuccess(razorpayPaymentId)
            }
            is SaleDetailsFragment -> {
                currentFragment.handlePaymentSuccess(razorpayPaymentId)
            }
        }
    }
    
    override fun onPaymentError(code: Int, description: String?) {
        Log.e("RazorpayPayment", "Payment failed with code: $code, description: $description")
        
        // Parse and clean up the error message
        val cleanErrorMessage = try {
            // Check if the description is a JSON error
            if (description?.startsWith("{") == true) {
                "Payment could not be completed. Please try again."
            } else {
                "Payment failed: ${description?.take(50) ?: "Unknown error"}"
            }
        } catch (e: Exception) {
            "Payment failed. Please try again."
        }
        
        Toast.makeText(this, cleanErrorMessage, Toast.LENGTH_LONG).show()
        
        // Update payment status in Firestore
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        when (currentFragment) {
            is MySalesFragment -> {
                currentFragment.handlePaymentFailure(description)
            }
            is SaleDetailsFragment -> {
                currentFragment.handlePaymentFailure(description)
            }
        }
    }
} 