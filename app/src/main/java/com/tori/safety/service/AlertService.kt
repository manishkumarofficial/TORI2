package com.tori.safety.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.tori.safety.R
import com.tori.safety.alert.AlertManager
import com.tori.safety.data.database.TorIDatabase
import com.tori.safety.data.database.TripLogDao
import com.tori.safety.data.model.EmergencyContact
import com.tori.safety.data.repository.ContactRepository
import com.tori.safety.data.repository.SettingsRepository
import com.tori.safety.data.trip.TripManager
import com.tori.safety.data.weather.WeatherInfo
import com.tori.safety.data.weather.WeatherManager
import com.tori.safety.intelligence.PredictiveFatigueManager
import com.tori.safety.location.LocationManager
import com.tori.safety.location.LocationResult
import com.tori.safety.ml.AlertType
import com.tori.safety.ml.DetectionResult
import com.tori.safety.ml.DrowsinessDetector
import com.tori.safety.sms.SmsManager
import com.tori.safety.ui.monitoring.AlertState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Background service for monitoring drowsiness
 */
class AlertService : LifecycleService(), SensorEventListener {
    
    // Binder
    private val binder = LocalBinder()
    
    // LiveData for UI updates
    private val _earValue = MutableLiveData<Float>()
    val earValue: LiveData<Float> = _earValue

    private val _detectionStatus = MutableLiveData<String>()
    val detectionStatus: LiveData<String> = _detectionStatus

    private val _alertState = MutableLiveData<AlertState>()
    val alertState: LiveData<AlertState> = _alertState

    private val _faceDetected = MutableLiveData<Boolean>()
    val faceDetected: LiveData<Boolean> = _faceDetected
    
    private val _fatigueRiskScore = MutableLiveData<Int>()
    val fatigueRiskScore: LiveData<Int> = _fatigueRiskScore
    
    private val _weatherInfo = MutableLiveData<WeatherInfo>()
    val weatherInfo: LiveData<WeatherInfo> = _weatherInfo

    // Managers
    private lateinit var alertManager: AlertManager
    private lateinit var smsManager: SmsManager
    private lateinit var locationManager: LocationManager
    private lateinit var predictiveFatigueManager: PredictiveFatigueManager
    private lateinit var weatherManager: WeatherManager
    private lateinit var tripManager: TripManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var contactRepository: ContactRepository
    
    // Database
    private lateinit var tripLogDao: TripLogDao
    
    // Camera & Detection
    private lateinit var drowsinessDetector: DrowsinessDetector
    private lateinit var cameraExecutor: ExecutorService
    private var isMonitoring = false
    private var detectionJob: Job? = null
    private var fatigueCheckJob: Job? = null
    
    // Sensor for accident detection
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lastAcceleration = 0f
    private var lastShakeTime: Long = 0
    private val SHAKE_THRESHOLD = 15f // m/s²
    private val SHAKE_INTERVAL = 1000L // 1 second
    
    // Alert response handling
    private var alertStartTime: Long = 0
    private val ALERT_RESPONSE_TIME = 10000L // 10 seconds for user response
    
    // Emergency contacts
    private var emergencyContacts = listOf<EmergencyContact>()

    inner class LocalBinder : Binder() {
        fun getService(): AlertService = this@AlertService
    }
    
    companion object {
        private const val CHANNEL_ID = "AlertServiceChannel"
        private const val NOTIFICATION_ID = 1
    }
    
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        initializeManagers()
        initializeDatabase()
        initializeSensors()
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        drowsinessDetector = DrowsinessDetector(this)
    }
    
    private fun initializeManagers() {
        alertManager = AlertManager(this)
        smsManager = SmsManager(this)
        locationManager = LocationManager(this)
        predictiveFatigueManager = PredictiveFatigueManager()
        weatherManager = WeatherManager()
        tripManager = TripManager()
        settingsRepository = SettingsRepository(this)
        
        // Initialize ContactRepository
        val database = TorIDatabase.getDatabase(this)
        contactRepository = ContactRepository(database.contactDao())
        
        lifecycleScope.launch {
            alertManager.initialize()
            applySettings()
            loadContacts()
        }
    }
    
    private suspend fun loadContacts() {
        contactRepository.getAllContacts().collect { contacts ->
            emergencyContacts = contacts
        }
    }
    
    private fun applySettings() {
        val settings = settingsRepository.getSettings()
        alertManager.updateSettings(
            language = settings.language,
            volume = settings.alertVolume,
            vibrationEnabled = settings.vibrationEnabled,
            ttsEnabled = settings.ttsEnabled
        )
    }
    
    private fun initializeDatabase() {
        val database = TorIDatabase.getDatabase(this)
        tripLogDao = database.tripLogDao()
    }
    
    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Alert Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TOR-I Active")
            .setContentText("Monitoring driver safety in background")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }
    
    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        
        // Apply settings
        val settings = settingsRepository.getSettings()
        drowsinessDetector.updateSettings(settings)
        applySettings()
        
        predictiveFatigueManager.startDrive()
        tripManager.startTrip()
        
        // Register sensor listener
        accelerometer?.also { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        
        // Start CameraX ImageAnalysis
        startCameraAnalysis()
        
        // Start detection flow collection
        detectionJob = lifecycleScope.launch {
            drowsinessDetector.detectionFlow.collect { result ->
                handleDetectionResult(result)
            }
        }
        
        // Start periodic fatigue check
        fatigueCheckJob = lifecycleScope.launch {
            fetchWeather()
            while (isMonitoring) {
                checkPredictiveFatigue()
                delay(60000) // 1 minute
            }
        }
    }
    
    private fun startCameraAnalysis() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            
            imageAnalysis.setAnalyzer(cameraExecutor, drowsinessDetector)
            
            // Unbind only analysis to avoid conflict if activity bound it? 
            // Actually, we want to bind it to the Service lifecycle.
            // If Activity is also bound, CameraX handles multiple use cases.
            // But ImageAnalysis can only be bound once? No, multiple use cases can be bound.
            // However, we need to be careful about lifecycle owners.
            
            try {
                // We don't unbind all because Activity might have Preview.
                // But we need to make sure Analysis is bound to THIS service.
                cameraProvider.bindToLifecycle(
                    this, 
                    androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
        }, ContextCompat.getMainExecutor(this))
    }
    
    fun stopMonitoring() {
        isMonitoring = false
        detectionJob?.cancel()
        fatigueCheckJob?.cancel()
        
        predictiveFatigueManager.stopDrive()
        
        val tripLog = tripManager.endTrip()
        tripLog?.let { log ->
            lifecycleScope.launch(Dispatchers.IO) {
                tripLogDao.insertTripLog(log)
            }
        }
        
        sensorManager?.unregisterListener(this)
        alertManager.stopCurrentAlert()
        
        // Stop Camera (unbind)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            // We can't easily unbind just one use case without reference, 
            // but since we bound it to 'this', it should auto-unbind when service is destroyed.
            // However, we want to stop it explicitly when monitoring stops.
            // For now, we rely on LifecycleService.
        }, ContextCompat.getMainExecutor(this))
    }
    
    private suspend fun fetchWeather() {
        val locationResult = locationManager.getCurrentLocation()
        if (locationResult is LocationResult.Success) {
            val weather = weatherManager.getWeather(locationResult.location)
            _weatherInfo.postValue(weather)
        }
    }
    
    private fun checkPredictiveFatigue() {
        val riskScore = predictiveFatigueManager.calculateFatigueRisk()
        _fatigueRiskScore.postValue(riskScore)
        
        if (riskScore >= 90) {
             _alertState.postValue(AlertState.Active(
                title = "High Fatigue Risk!",
                message = "You have been driving for too long. Please take a break immediately."
            ))
            alertManager.triggerAlert(AlertType.DROWSINESS)
            tripManager.recordAlert(AlertType.DROWSINESS)
        }
    }
    
    private fun handleDetectionResult(result: DetectionResult) {
        _faceDetected.postValue(result.faceDetected)
        
        val avgEyeOpen = (result.leftEyeOpenProbability + result.rightEyeOpenProbability) / 2
        _earValue.postValue(avgEyeOpen)
        
        _detectionStatus.postValue(result.message)
        
        if (result.isYawning) {
            tripManager.recordYawn()
        }
        
        result.alertType?.let { alertType ->
            alertManager.triggerAlert(alertType)
            tripManager.recordAlert(alertType)
            
            val title = when(alertType) {
                AlertType.DROWSINESS -> "Drowsiness Detected"
                AlertType.FAINT -> "Possible Faint Detected!"
                AlertType.SOS -> "Emergency Detected!"
                AlertType.DISTRACTION -> "Distraction Detected"
            }
            
            val message = when(alertType) {
                AlertType.DROWSINESS -> "You appear to be getting sleepy. Please take a break."
                AlertType.FAINT -> "Please respond if you're okay!"
                AlertType.SOS -> "Sending SOS to your contacts..."
                AlertType.DISTRACTION -> "Keep your eyes on the road."
            }
            
            _alertState.postValue(AlertState.Active(title, message))
            
            if (alertType == AlertType.SOS) {
                lifecycleScope.launch { sendAutomaticSOS() }
            } else if (alertType == AlertType.FAINT) {
                alertStartTime = System.currentTimeMillis()
                startAlertResponseCountdown()
            }
        }
    }
    
    private fun startAlertResponseCountdown() {
        lifecycleScope.launch {
            delay(ALERT_RESPONSE_TIME)
             if (_alertState.value is AlertState.Active) {
                sendAutomaticSOS()
             }
        }
    }
    
    private suspend fun sendAutomaticSOS() {
        val locationResult = locationManager.getCurrentLocation()
        val result = smsManager.sendSOSMessage(emergencyContacts, locationResult, true)
        
        if (result.success) {
            _alertState.postValue(AlertState.Active("SOS Sent", "Emergency message sent."))
            tripManager.recordAlert(AlertType.SOS)
        } else {
            _alertState.postValue(AlertState.Active("SOS Failed", "Failed: ${result.error}"))
        }
    }
    
    fun sendManualSOS() {
        lifecycleScope.launch {
            val locationResult = locationManager.getCurrentLocation()
            val result = smsManager.sendSOSMessage(emergencyContacts, locationResult, false)
            
            if (result.success) {
                _alertState.postValue(AlertState.Active("SOS Sent", "Emergency message sent."))
                tripManager.recordAlert(AlertType.SOS)
            } else {
                _alertState.postValue(AlertState.Active("SOS Failed", "Failed: ${result.error}"))
            }
        }
    }
    
    fun dismissAlert() {
        _alertState.postValue(AlertState.Inactive)
        alertManager.stopCurrentAlert()
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { sensorEvent ->
            if (sensorEvent.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = sensorEvent.values[0]
                val y = sensorEvent.values[1]
                val z = sensorEvent.values[2]
                
                val acceleration = kotlin.math.sqrt(x * x + y * y + z * z)
                val delta = kotlin.math.abs(acceleration - lastAcceleration)
                lastAcceleration = acceleration
                
                if (delta > SHAKE_THRESHOLD) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastShakeTime > SHAKE_INTERVAL) {
                        lastShakeTime = currentTime
                        if (!isMonitoring) { 
                            lifecycleScope.launch {
                                triggerAccidentAlert()
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun triggerAccidentAlert() {
        _alertState.postValue(AlertState.Active("Possible Accident!", "Are you okay?"))
        alertManager.triggerSOSAlert()
        tripManager.recordAlert(AlertType.SOS)
        alertStartTime = System.currentTimeMillis()
        startAlertResponseCountdown()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    
    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        cameraExecutor.shutdown()
        drowsinessDetector.release()
        alertManager.release()
    }
}