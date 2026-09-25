/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cash.p.terminal.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import kotlin.reflect.KClass

/** Remembers a [SharedViewModelStoreNavEntryDecorator] backed by [viewModelStoreOwner]. */
@Composable
fun <T : Any> rememberSharedViewModelStoreNavEntryDecorator(
    viewModelStoreOwner: ViewModelStoreOwner =
        checkNotNull(LocalViewModelStoreOwner.current) {
            "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
        },
): SharedViewModelStoreNavEntryDecorator<T> = remember(viewModelStoreOwner) {
    SharedViewModelStoreNavEntryDecorator(viewModelStoreOwner)
}

/**
 * Gives every nav entry its own [ViewModelStore], keyed by the entry's content key and cleared once
 * the entry is popped and nothing composes against it anymore. Needs the saveable state holder
 * decorator before it for SavedStateHandles.
 */
class SharedViewModelStoreNavEntryDecorator<T : Any>(
    rootViewModelStoreOwner: ViewModelStoreOwner,
) : NavEntryDecorator<T>(
    onPop = { contentKey ->
        rootViewModelStoreOwner.viewModelStore.entryViewModel().onPop(contentKey)
    },
    decorate = { entry ->
        val rootViewModelStore = rootViewModelStoreOwner.viewModelStore
        CompositionLocalProvider(
            LocalRootViewModelStore provides rootViewModelStore,
            LocalViewModelStoreOwner provides rememberChildViewModelStoreOwner(rootViewModelStore, entry.contentKey),
        ) {
            entry.Content()
        }
    },
)

/**
 * The ViewModel store of the last back stack entry of [klass], for ViewModels shared across a flow.
 * Resolved once per composition, so it survives that entry's pop while the caller animates out;
 * the caller entry's own store if no such entry exists at first composition.
 */
@Composable
fun HSNavigation.viewModelStoreOwnerForPage(klass: KClass<out HSPage>): ViewModelStoreOwner {
    val pageKey = remember(klass) { backStack.lastOrNull { it::class == klass }?.contentKey() }
        ?: return checkNotNull(LocalViewModelStoreOwner.current) {
            "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
        }
    val rootViewModelStore = checkNotNull(LocalRootViewModelStore.current) {
        "No SharedViewModelStoreNavEntryDecorator above this entry"
    }
    return rememberChildViewModelStoreOwner(rootViewModelStore, pageKey)
}

@Composable
private fun rememberChildViewModelStoreOwner(rootViewModelStore: ViewModelStore, contentKey: Any): ViewModelStoreOwner {
    val savedStateRegistryOwner = LocalSavedStateRegistryOwner.current
    val entryViewModel = remember(rootViewModelStore) { rootViewModelStore.entryViewModel() }
    DisposableEffect(entryViewModel, contentKey) {
        entryViewModel.acquire(contentKey)
        onDispose { entryViewModel.release(contentKey) }
    }
    return remember(entryViewModel, contentKey) {
        ChildViewModelStoreOwner(savedStateRegistryOwner, entryViewModel.viewModelStoreForKey(contentKey))
    }
}

private val LocalRootViewModelStore = staticCompositionLocalOf<ViewModelStore?> { null }

// Mutated only from composition and effects, i.e. on the main thread.
private class EntryViewModel : ViewModel() {
    private val stores = mutableMapOf<Any, ViewModelStore>()
    private val holders = mutableMapOf<Any, Int>()
    private val popped = mutableSetOf<Any>()

    fun viewModelStoreForKey(key: Any): ViewModelStore = stores.getOrPut(key) { ViewModelStore() }

    fun acquire(key: Any) {
        holders[key] = holders.getOrElse(key) { 0 } + 1
    }

    fun release(key: Any) {
        val remaining = holders.getOrElse(key) { 1 } - 1
        if (remaining > 0) {
            holders[key] = remaining
            return
        }
        holders.remove(key)
        if (popped.remove(key)) clearViewModelStoreForKey(key)
    }

    // An entry popped while pages still compose against its store keeps it until the last one leaves.
    fun onPop(key: Any) {
        if (key in holders) popped += key else clearViewModelStoreForKey(key)
    }

    private fun clearViewModelStoreForKey(key: Any) {
        stores.remove(key)?.clear()
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
    }
}

private fun ViewModelStore.entryViewModel(): EntryViewModel =
    ViewModelProvider.create(
        store = this,
        factory = viewModelFactory { initializer { EntryViewModel() } },
    )[EntryViewModel::class]

private class ChildViewModelStoreOwner(
    savedStateRegistryOwner: SavedStateRegistryOwner,
    override val viewModelStore: ViewModelStore,
) : ViewModelStoreOwner,
    SavedStateRegistryOwner by savedStateRegistryOwner,
    HasDefaultViewModelProviderFactory {

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = SavedStateViewModelFactory()

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras().also {
            it[SAVED_STATE_REGISTRY_OWNER_KEY] = this
            it[VIEW_MODEL_STORE_OWNER_KEY] = this
        }

    init {
        require(lifecycle.currentState == Lifecycle.State.INITIALIZED) {
            "SharedViewModelStoreNavEntryDecorator requires the saveable state holder decorator " +
                "to support SavedStateHandles."
        }
        enableSavedStateHandles()
    }
}
