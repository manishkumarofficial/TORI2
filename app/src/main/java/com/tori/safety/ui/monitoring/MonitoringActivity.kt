package com.tori.safety.ui.monitoring

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.tori.safety.R
import com.tori.safety.databinding.ActivityMonitoringBinding

/**
 * Monitoring activity for real-time drowsiness detection
 */
class MonitoringActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonitoringBinding
    private val viewModel: MonitoringViewModel by viewModels { 
        MonitoringViewModelFactory(this) 
    }

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonitoringBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupClickListeners()
        setupObservers()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun setupUI() {
        supportActionBar?.hide()
    }

    private fun setupClickListeners() {
        binding.btnStopMonitoring.setOnClickListener {
            viewModel.stopMonitoring()
            finish()
        }
        binding.btnImOk.setOnClickListener { 
            viewModel.onImOkClicked()
            hideAlert()
        }
        binding.btnTakeBreak.setOnClickListener { 
            viewModel.onTakeBreakClicked()
            hideAlert()
        }
        binding.btnSendSos.setOnClickListener { 
            viewModel.onSendSosClicked()
            hideAlert()
        }
        binding.btnEmergencySos.setOnClickListener {
            viewModel.onSendSosClicked()
        }
        binding.btnBack.setOnClickListener {
            viewModel.stopMonitoring()
            finish()
        }
    }

    private fun setupObservers() {
        viewModel.earValue.observe(this) { ear ->
            binding.tvEarValue.text = String.format("%.2f", ear)
        }
        
        viewModel.detectionStatus.observe(this) { status ->
            binding.tvDetectionStatus.text = status
        }
        
        viewModel.alertState.observe(this) { state ->
            if (state is AlertState.Active) {
                showAlert(state.title, state.message)
            } else {
                hideAlert()
            }
        }
        
        viewModel.fatigueRiskScore.observe(this) { score ->
            binding.tvFatigueRisk.text = "$score%"
            val color = when {
                score < 50 -> ContextCompat.getColor(this, R.color.safety_safe)
                score < 80 -> ContextCompat.getColor(this, R.color.safety_warning)
                else -> ContextCompat.getColor(this, R.color.safety_critical)
            }
            binding.tvFatigueRisk.setTextColor(color)
        }
        
        viewModel.weatherInfo.observe(this) { weather ->
            binding.tvWeather.text = "${weather.temperature}°C, ${weather.condition}"
        }
    }

    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
        
        // Start monitoring service
        viewModel.startMonitoring()
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

        try {
            // Bind Preview use case to the Activity lifecycle.
            // We DO NOT call unbindAll() because that would unbind the Service's ImageAnalysis use case.
            cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            
        } catch(exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun showAlert(title: String, message: String) {
        binding.layoutAlertOverlay.visibility = View.VISIBLE
        binding.tvAlertTitle.text = title
        binding.tvAlertMessage.text = message
    }

    private fun hideAlert() {
        binding.layoutAlertOverlay.visibility = View.GONE
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Log.e(TAG, "Permissions not granted by the user.")
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // No need to shutdown executor or release detector here as they are in Service
    }

    companion object {
        private const val TAG = "MonitoringActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS
        )
    }
}