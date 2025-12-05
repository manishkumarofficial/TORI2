package com.tori.safety.ui.monitoring

sealed class AlertState {
    object Inactive : AlertState()
    data class Active(val title: String, val message: String) : AlertState()
}
