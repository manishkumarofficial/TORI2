package com.tori.safety.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.tori.safety.data.model.DetectionSettings
import com.tori.safety.data.model.Language
import com.tori.safety.data.repository.SettingsRepository
import com.tori.safety.databinding.ActivitySettingsBinding
import com.tori.safety.ui.contacts.ContactsActivity

/**
 * Activity for app settings
 */
class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repository: SettingsRepository
    private var currentSettings = DetectionSettings()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        repository = SettingsRepository(this)
        loadSettings()
        setupUI()
    }
    
    private fun loadSettings() {
        currentSettings = repository.getSettings()
        updateUIWithSettings(currentSettings)
    }
    
    private fun updateUIWithSettings(settings: DetectionSettings) {
        with(binding) {
            // EAR Threshold
            val earProgress = (settings.earThreshold * 100).toInt()
            seekEarThreshold.progress = earProgress
            tvEarThresholdValue.text = String.format("%.2f", settings.earThreshold)
            
            // Consecutive Frames
            seekConsecutiveFrames.progress = settings.consecutiveFrames
            tvConsecutiveFramesValue.text = settings.consecutiveFrames.toString()
            
            // Alert Volume
            seekVolume.progress = settings.alertVolume
            tvVolumeValue.text = "${settings.alertVolume}%"
            
            // Toggles
            switchVibration.isChecked = settings.vibrationEnabled
            switchTts.isChecked = settings.ttsEnabled
            switchLowPower.isChecked = settings.lowPowerMode
            switchBackground.isChecked = settings.backgroundMonitoring
            switchAutoStart.isChecked = settings.autoStart
            
            // Language
            when (settings.language) {
                Language.ENGLISH -> radioEnglish.isChecked = true
                Language.TAMIL -> radioTamil.isChecked = true
            }
        }
    }
    
    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        
        binding.btnManageContacts.setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }
        
        setupSeekBars()
        setupSwitches()
        setupLanguage()
    }
    
    private fun setupSeekBars() {
        binding.seekEarThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val value = progress / 100.0
                    binding.tvEarThresholdValue.text = String.format("%.2f", value)
                    currentSettings = currentSettings.copy(earThreshold = value)
                    saveSettings()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        binding.seekConsecutiveFrames.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvConsecutiveFramesValue.text = progress.toString()
                    currentSettings = currentSettings.copy(consecutiveFrames = progress)
                    saveSettings()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        binding.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvVolumeValue.text = "$progress%"
                    currentSettings = currentSettings.copy(alertVolume = progress)
                    saveSettings()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun setupSwitches() {
        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(vibrationEnabled = isChecked)
            saveSettings()
        }
        
        binding.switchTts.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(ttsEnabled = isChecked)
            saveSettings()
        }
        
        binding.switchLowPower.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(lowPowerMode = isChecked)
            saveSettings()
        }
        
        binding.switchBackground.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(backgroundMonitoring = isChecked)
            saveSettings()
        }
        
        binding.switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            currentSettings = currentSettings.copy(autoStart = isChecked)
            saveSettings()
        }
    }
    
    private fun setupLanguage() {
        binding.radioLanguage.setOnCheckedChangeListener { _, checkedId ->
            val language = when (checkedId) {
                binding.radioEnglish.id -> Language.ENGLISH
                binding.radioTamil.id -> Language.TAMIL
                else -> Language.ENGLISH
            }
            currentSettings = currentSettings.copy(language = language)
            saveSettings()
        }
    }
    
    private fun saveSettings() {
        repository.saveSettings(currentSettings)
    }
}