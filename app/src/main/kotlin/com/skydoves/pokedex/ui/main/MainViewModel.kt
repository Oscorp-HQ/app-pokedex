/*
 * Designed and developed by 2022 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.skydoves.pokedex.ui.main
import androidx.annotation.MainThread
import androidx.databinding.Bindable
import androidx.lifecycle.viewModelScope
import com.skydoves.bindables.BindingViewModel
import com.skydoves.bindables.asBindingProperty
import com.skydoves.bindables.bindingProperty
import com.skydoves.pokedex.core.model.Pokemon
import com.skydoves.pokedex.core.repository.MainRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class MainViewModel @Inject constructor(
  private val mainRepository: MainRepository,
) : BindingViewModel() {
  @get:Bindable
  var isLoading: Boolean by bindingProperty(false)
    private set
    
  @get:Bindable
  var toastMessage: String? by bindingProperty(null)
    private set
    
  // New search feature
  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery
  
  @get:Bindable
  var searchActive: Boolean by bindingProperty(false)
    private set
    
  private val pokemonFetchingIndex: MutableStateFlow<Int> = MutableStateFlow(0)
  
  private val pokemonListFlow = pokemonFetchingIndex.flatMapLatest { page ->
    mainRepository.fetchPokemonList(
      page = page,
      onStart = { isLoading = true },
      onComplete = { isLoading = false },
      onError = { toastMessage = it },
    )
  }
  
  // Filtered Pokemon list based on search query
  private val filteredPokemonFlow = combine(
    pokemonListFlow,
    _searchQuery.debounce(300)
  ) { pokemonList, query ->
    if (query.isBlank()) {
      pokemonList
    } else {
      pokemonList.filter { pokemon ->
        pokemon.name.contains(query, ignoreCase = true) ||
        pokemon.id.toString() == query
      }
    }
  }
  
  @get:Bindable
  val pokemonList: List<Pokemon> by pokemonListFlow.asBindingProperty(viewModelScope, emptyList())
  
  @get:Bindable
  val filteredPokemonList: List<Pokemon> by filteredPokemonFlow.asBindingProperty(viewModelScope, emptyList())
  
  @get:Bindable
  val displayedPokemonList: List<Pokemon> by bindingProperty(emptyList())
    get() = if (searchActive && _searchQuery.value.isNotBlank()) filteredPokemonList else pokemonList
  
  init {
    Timber.d("init MainViewModel")
  }
  
  @MainThread
  fun fetchNextPokemonList() {
    if (!isLoading && !searchActive) {
      pokemonFetchingIndex.value++
    }
  }
  
  /**
   * Updates the search query and activates search mode
   *
   * @param query The search text entered by user
   */
  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
    searchActive = query.isNotBlank()
  }
  
  /**
   * Clears the search and returns to the full Pokemon list
   */
  fun clearSearch() {
    _searchQuery.value = ""
    searchActive = false
  }
  
  /**
   * Resets the Pokemon list to the first page
   */
  fun resetPokemonList() {
    pokemonFetchingIndex.value = 0
  }
}
