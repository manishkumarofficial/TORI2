package com.tori.safety.data.database

import androidx.room.TypeConverter
import com.tori.safety.data.model.AlertType
import com.tori.safety.data.model.Language
import com.tori.safety.data.model.ResponseType

/**
 * Type converters for Room database
 */
class Converters {
    
    @TypeConverter
    fun fromAlertType(alertType: AlertType): String {
        return alertType.name
    }
    
    @TypeConverter
    fun toAlertType(alertType: String): AlertType {
        return AlertType.valueOf(alertType)
    }
    
    @TypeConverter
    fun fromResponseType(responseType: ResponseType?): String? {
        return responseType?.name
    }
    
    @TypeConverter
    fun toResponseType(responseType: String?): ResponseType? {
        return responseType?.let { ResponseType.valueOf(it) }
    }
    
    @TypeConverter
    fun fromLanguage(language: Language): String {
        return language.name
    }
    
    @TypeConverter
    fun toLanguage(language: String): Language {
        return Language.valueOf(language)
    }
}
