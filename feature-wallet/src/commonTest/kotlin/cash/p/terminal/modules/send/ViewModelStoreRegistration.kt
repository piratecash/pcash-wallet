package cash.p.terminal.modules.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Lets [ViewModelStore.clear] run onCleared; `ViewModelStore.put` is restricted and absent at runtime on desktop. */
internal inline fun <reified VM : ViewModel> ViewModelStore.register(viewModel: VM): VM =
    ViewModelProvider.create(this, viewModelFactory { initializer { viewModel } })["${viewModel.hashCode()}", VM::class]
