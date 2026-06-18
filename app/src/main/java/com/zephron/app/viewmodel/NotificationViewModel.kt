package com.zephron.app.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class NotificationType { INFO, SUCCESS, ERROR }

data class NotificationData(
    val message: String,
    val type: NotificationType = NotificationType.INFO
)

class NotificationViewModel : ViewModel() {
    private val _currentNotification = mutableStateOf<NotificationData?>(null)
    val currentNotification: State<NotificationData?> = _currentNotification

    fun showNotification(message: String, type: NotificationType = NotificationType.INFO) {
        viewModelScope.launch {
            _currentNotification.value = NotificationData(message, type)
            delay(3000)
            if (_currentNotification.value?.message == message) {
                _currentNotification.value = null
            }
        }
    }

    fun dismiss() {
        _currentNotification.value = null
    }
}
