package com.hairconsultant.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

/** Builds a ViewModel with dependencies pulled straight from [AppContainer] — no Hilt needed. */
class ViewModelFactory(
    private val container: AppContainer,
    private val create: (AppContainer) -> ViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        create(container) as T
}
