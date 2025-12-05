package com.tori.safety.data.weather

import android.location.Location
import kotlinx.coroutines.delay
import kotlin.random.Random

data class WeatherInfo(
    val temperature: Double,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val description: String
)

enum class WeatherCondition {
    CLEAR,
    CLOUDY,
    RAIN,
    FOG,
    SNOW,
    THUNDERSTORM
}

/**
 * Manages weather data retrieval.
 * Currently mocks data as we don't have a real API key.
 */
class WeatherManager {

    suspend fun getWeather(location: Location): WeatherInfo {
        // Simulate network delay
        delay(500)
        
        // Mock logic: Random weather for now, or based on time/location hash
        // In a real app, this would call an API like OpenWeatherMap
        
        val conditions = WeatherCondition.values()
        val randomCondition = conditions[Random.nextInt(conditions.size)]
        
        // Bias towards clear/cloudy for demo purposes
        val condition = if (Random.nextFloat() > 0.7) randomCondition else WeatherCondition.CLEAR
        
        val temp = 20.0 + Random.nextDouble() * 10.0
        
        return WeatherInfo(
            temperature = temp,
            condition = condition,
            isDay = true, // Simplified
            description = condition.name.lowercase().replaceFirstChar { it.uppercase() }
        )
    }
}
