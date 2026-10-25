package com.valoid.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valoid.mobile.data.Announcement
import com.valoid.mobile.data.HubRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

class HubViewModel(private val repo: HubRepository = HubRepository()) : ViewModel() {

    val announcements: StateFlow<List<Announcement>> = repo.announcements()
        .catch { }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
