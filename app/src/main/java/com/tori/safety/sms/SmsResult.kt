package com.tori.safety.sms

/**
 * SMS result data class
 */
data class SmsResult(
    val success: Boolean,
    val error: String?,
    val sentCount: Int,
    val totalCount: Int
)