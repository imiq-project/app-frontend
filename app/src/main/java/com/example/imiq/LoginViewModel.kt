package com.example.imiq

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel(
    private val repository: LoginRepository = LoginRepository()
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    fun login(code: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading

            try {
                val result = repository.login(code)
                if (result.isSuccess) {
                    val token = result.getOrNull()
                    if (token != null) {
                        // Save token locally
                        TokenManager.saveToken(token)
                        _loginState.value = LoginState.Success
                    } else {
                        _loginState.value = LoginState.Error("Invalid response from server")
                    }
                } else {
                    val error = result.exceptionOrNull()
                    _loginState.value = LoginState.Error(
                        error?.message ?: "Login failed. Please check your code."
                    )
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(
                    "Network error: ${e.message}"
                )
            }
        }
    }

    fun resetState() {
        _loginState.value = LoginState.Idle
    }
}

