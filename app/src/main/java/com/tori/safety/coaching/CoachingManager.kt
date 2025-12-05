package com.tori.safety.coaching

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.tori.safety.data.model.Language
import kotlin.random.Random

/**
 * Manager for driver coaching tips
 */
class CoachingManager(private val context: Context) {
    
    private val coachingTips = mapOf(
        Language.ENGLISH to listOf(
            "Take a 15-minute break and get fresh air",
            "Drink water to stay hydrated",
            "Do some light stretching exercises",
            "Listen to upbeat music to stay alert",
            "Chew gum or have a light snack",
            "Splash cold water on your face",
            "Take deep breaths and focus on the road",
            "Open the window for fresh air",
            "Adjust your seat position for better posture",
            "Take a short walk if possible"
        ),
        Language.TAMIL to listOf(
            "15 நிமிட ஓய்வு எடுத்து புதிய காற்றை சுவாசிக்கவும்",
            "நீரேற்றத்திற்கு தண்ணீர் குடிக்கவும்",
            "இலகுவான உடற்பயிற்சிகள் செய்யவும்",
            "உற்சாகமான இசை கேட்டு விழிப்புடன் இருக்கவும்",
            "பால் மிட்டாய் அல்லது இலகுவான உணவு எடுக்கவும்",
            "முகத்தில் குளிர்ந்த நீர் தெளிக்கவும்",
            "ஆழமாக சுவாசித்து சாலையில் கவனம் செலுத்தவும்",
            "புதிய காற்றுக்காக ஜன்னலை திறக்கவும்",
            "சிறந்த நிலைக்காக இருக்கை நிலையை சரிசெய்யவும்",
            "முடிந்தால் குறுகிய நடை எடுக்கவும்"
        )
    )
    
    private val _currentTip = MutableLiveData<String>()
    val currentTip: LiveData<String> = _currentTip
    
    private val _tipsEnabled = MutableLiveData<Boolean>()
    val tipsEnabled: LiveData<Boolean> = _tipsEnabled
    
    init {
        _tipsEnabled.value = true
    }
    
    fun showRandomTip(language: Language = Language.ENGLISH) {
        if (_tipsEnabled.value == true) {
            val tips = coachingTips[language] ?: coachingTips[Language.ENGLISH]!!
            val randomTip = tips[Random.nextInt(tips.size)]
            _currentTip.value = randomTip
        }
    }
    
    fun showSpecificTip(tipIndex: Int, language: Language = Language.ENGLISH) {
        if (_tipsEnabled.value == true) {
            val tips = coachingTips[language] ?: coachingTips[Language.ENGLISH]!!
            if (tipIndex >= 0 && tipIndex < tips.size) {
                _currentTip.value = tips[tipIndex]
            }
        }
    }
    
    fun setTipsEnabled(enabled: Boolean) {
        _tipsEnabled.value = enabled
    }
    
    fun getTipCount(): Int {
        return coachingTips[Language.ENGLISH]?.size ?: 0
    }
    
    fun getAllTips(language: Language = Language.ENGLISH): List<String> {
        return coachingTips[language] ?: coachingTips[Language.ENGLISH]!!
    }
    
    fun clearCurrentTip() {
        _currentTip.value = ""
    }
}
