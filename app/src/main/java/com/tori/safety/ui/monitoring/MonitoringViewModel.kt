package com.tori.safety.ui.monitoring

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tori.safety.data.weather.WeatherInfo
import com.tori.safety.service.AlertService

/**
 * ViewModel for MonitoringActivity - acts as a client for AlertService
 */
class MonitoringViewModel(private val context: Context) : ViewModel() {

    private val _earValue = MutableLiveData<Float>()
    val earValue: LiveData<Float> = _earValue

    private val _detectionStatus = MutableLiveData<String>()
    val detectionStatus: LiveData<String> = _detectionStatus

    private val _alertState = MutableLiveData<AlertState>()
    val alertState: LiveData<AlertState> = _alertState
    
    private val _fatigueRiskScore = MutableLiveData<Int>()
    val fatigueRiskScore: LiveData<Int> = _fatigueRiskScore
    
    private val _weatherInfo = MutableLiveData<WeatherInfo>()
    val weatherInfo: LiveData<WeatherInfo> = _weatherInfo

    private var alertService: AlertService? = null
    private var isBound = false
    
    private val _serviceConnected = MutableLiveData<Boolean>()
    val serviceConnected: LiveData<Boolean> = _serviceConnected
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AlertService.LocalBinder
            alertService = binder.getService()
            isBound = true
            _serviceConnected.postValue(true)
            setupServiceObservers()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            alertService = null
            isBound = false
            _serviceConnected.postValue(false)
        }
    }
    
    init {
        bindService()
    }
    
    private fun bindService() {
        val intent = Intent(context, AlertService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun setupServiceObservers() {
        alertService?.let { service ->
            service.earValue.observeForever { _earValue.postValue(it) }
            service.detectionStatus.observeForever { _detectionStatus.postValue(it) }
            service.alertState.observeForever { _alertState.postValue(it) }
            service.fatigueRiskScore.observeForever { _fatigueRiskScore.postValue(it) }
            service.weatherInfo.observeForever { _weatherInfo.postValue(it) }
        }
    }

    fun startMonitoring() {
        val intent = Intent(context, AlertService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        alertService?.startMonitoring()
    }

    fun stopMonitoring() {
        alertService?.stopMonitoring()
        val intent = Intent(context, AlertService::class.java)
        context.stopService(intent)
    }
    
    fun onImOkClicked() {
        alertService?.dismissAlert()
    }
    
    fun onTakeBreakClicked() {
        alertService?.dismissAlert()
    }
    
    fun onSendSosClicked() {
        alertService?.dismissAlert()
        alertService?.sendManualSOS()
    }

    fun setPreviewSurface(surfaceProvider: androidx.camera.core.Preview.SurfaceProvider?) {
        alertService?.setPreviewSurface(surfaceProvider)
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }
}
