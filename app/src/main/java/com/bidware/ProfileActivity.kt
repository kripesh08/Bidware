package com.bidware

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var fullNameField: EditText
    private lateinit var emailField: EditText
    private lateinit var mobileField: EditText
    private lateinit var aadharField: EditText
    private lateinit var editButton: Button
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        fullNameField = findViewById(R.id.fullNameField)
        emailField = findViewById(R.id.emailField)
        mobileField = findViewById(R.id.mobileField)
        aadharField = findViewById(R.id.aadharField)
        editButton = findViewById(R.id.editButton)
        saveButton = findViewById(R.id.saveButton)

        // Set up back button
        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }

        // Initially disable all fields
        setFieldsEnabled(false)

        // Load user data
        loadUserData()

        // Set up edit button
        editButton.setOnClickListener {
            setFieldsEnabled(true)
            editButton.visibility = View.GONE
            saveButton.visibility = View.VISIBLE
        }

        // Set up save button
        saveButton.setOnClickListener {
            saveUserData()
        }
    }

    private fun setFieldsEnabled(enabled: Boolean) {
        fullNameField.isEnabled = enabled
        emailField.isEnabled = enabled
        mobileField.isEnabled = enabled
        aadharField.isEnabled = enabled
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        fullNameField.setText(document.getString("fullName"))
                        emailField.setText(document.getString("email"))
                        mobileField.setText(document.getString("mobile"))
                        aadharField.setText(document.getString("aadhar"))
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveUserData() {
        val fullName = fullNameField.text.toString().trim()
        val email = emailField.text.toString().trim()
        val mobile = mobileField.text.toString().trim()
        val aadhar = aadharField.text.toString().trim()

        // Validate inputs
        if (fullName.isEmpty() || email.isEmpty() || mobile.isEmpty() || aadhar.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (mobile.length != 10 || !mobile.all { it.isDigit() }) {
            Toast.makeText(this, "Enter a valid 10-digit mobile number", Toast.LENGTH_SHORT).show()
            return
        }

        if (aadhar.length != 12 || !aadhar.all { it.isDigit() }) {
            Toast.makeText(this, "Aadhaar must be 12 digits", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = auth.currentUser?.uid
        if (userId != null) {
            val userData = mapOf(
                "fullName" to fullName,
                "email" to email,
                "mobile" to mobile,
                "aadhar" to aadhar
            )

            db.collection("users").document(userId)
                .update(userData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    setFieldsEnabled(false)
                    editButton.visibility = View.VISIBLE
                    saveButton.visibility = View.GONE
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
} 