package com.bidware

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignupActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val TAG = "SignupActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val fullNameEditText = findViewById<EditText>(R.id.fullNameField)
        val emailEditText = findViewById<EditText>(R.id.emailField)
        val mobileEditText = findViewById<EditText>(R.id.mobileField)
        val aadharEditText = findViewById<EditText>(R.id.aadharField)
        val passwordEditText = findViewById<EditText>(R.id.passwordField)
        val confirmPasswordEditText = findViewById<EditText>(R.id.confirmPasswordField)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val loginTextView = findViewById<TextView>(R.id.loginNow)

        registerButton.setOnClickListener {
            val fullName = fullNameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val mobile = mobileEditText.text.toString().trim()
            val aadhar = aadharEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()

            // Validate inputs
            if (fullName.isEmpty() || email.isEmpty() || mobile.isEmpty() || aadhar.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (mobile.length != 10 || !mobile.all { it.isDigit() }) {
                Toast.makeText(this, "Enter a valid 10-digit mobile number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (aadhar.length != 12 || !aadhar.all { it.isDigit() }) {
                Toast.makeText(this, "Aadhaar must be 12 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show loading indicator
            registerButton.isEnabled = false
            registerButton.text = "Registering..."

            // Check if mobile number already exists in Firestore
            db.collection("users")
                .whereEqualTo("mobile", mobile)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        registerButton.isEnabled = true
                        registerButton.text = "Register"
                        Toast.makeText(this, "Mobile number already registered", Toast.LENGTH_SHORT).show()
                    } else {
                        registerUser(fullName, email, mobile, aadhar, password)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error checking mobile number: ${e.message}")
                    registerButton.isEnabled = true
                    registerButton.text = "Register"
                    Toast.makeText(this, "Error checking mobile number. Please try again.", Toast.LENGTH_SHORT).show()
                }
        }

        loginTextView.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registerUser(fullName: String, email: String, mobile: String, aadhar: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val userId = user?.uid

                    if (userId != null) {
                        Log.d(TAG, "User created successfully: $userId")

                        val userData = hashMapOf(
                            "fullName" to fullName,
                            "email" to email,
                            "mobile" to mobile,
                            "aadhar" to aadhar
                        )

                        // Save user data to Firestore
                        db.collection("users").document(userId).set(userData)
                            .addOnSuccessListener {
                                Log.d(TAG, "User data successfully stored in Firestore!")
                                Toast.makeText(this, "Signup Successful", Toast.LENGTH_SHORT).show()

                                // Navigate to LoginActivity after successful signup
                                val intent = Intent(this, LoginActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Error saving user data: ${e.message}")
                                Toast.makeText(this, "Error saving user data. Please try again.", Toast.LENGTH_SHORT).show()
                                // Re-enable the register button
                                findViewById<Button>(R.id.registerButton).apply {
                                    isEnabled = true
                                    text = "Register"
                                }
                            }
                    }
                } else {
                    Log.e(TAG, "Signup Failed: ${task.exception?.message}")
                    Toast.makeText(this, "Signup Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    // Re-enable the register button
                    findViewById<Button>(R.id.registerButton).apply {
                        isEnabled = true
                        text = "Register"
                    }
                }
            }
    }
}
