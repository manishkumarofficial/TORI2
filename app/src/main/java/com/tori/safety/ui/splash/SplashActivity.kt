package com.tori.safety.ui.splash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tori.safety.R
import com.tori.safety.ui.main.MainActivity
import com.tori.safety.utils.PermissionManager

/**
 * Splash screen activity for TOR-I app
 */
class SplashActivity : AppCompatActivity() {
    
    private val permissionManager = PermissionManager(this)
    
    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.VIBRATE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.WAKE_LOCK
    )
    
    private val permissionRequestCode = 1001
    private val splashDelay = 3000L // 3 seconds
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        // Disable back button on splash screen
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - disable back button
            }
        })
        
        // Check if all permissions are granted
        if (hasAllPermissions()) {
            proceedToMainActivity()
        } else {
            // Request permissions after a short delay
            Handler(Looper.getMainLooper()).postDelayed({
                requestPermissions()
            }, 1500L)
        }
    }
    
    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestPermissions() {
        val permissionsToRequest = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest,
                permissionRequestCode
            )
        } else {
            proceedToMainActivity()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == permissionRequestCode) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            if (allGranted) {
                proceedToMainActivity()
            } else {
                showPermissionDeniedMessage()
            }
        }
    }
    
    private fun proceedToMainActivity() {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }, splashDelay)
    }
    
    private fun showPermissionDeniedMessage() {
        Toast.makeText(
            this,
            "TOR-I requires all permissions to function properly. Please grant permissions in Settings.",
            Toast.LENGTH_LONG
        ).show()
        
        // Still proceed to main activity after delay
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }, 2000L)
    }
}