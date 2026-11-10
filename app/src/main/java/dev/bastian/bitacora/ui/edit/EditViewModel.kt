package dev.bastian.bitacora.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.domain.CoordinateError
import dev.bastian.bitacora.domain.LocationProvider
import dev.bastian.bitacora.domain.LocationResult
import dev.bastian.bitacora.domain.Observation
import dev.bastian.bitacora.domain.ObservationDraft
import dev.bastian.bitacora.domain.ObservationRepository
import dev.bastian.bitacora.domain.PhotoStorage
import dev.bastian.bitacora.domain.TitleError
import dev.bastian.bitacora.domain.formatCoordinate
import dev.bastian.bitacora.domain.notesTooLong
import dev.bastian.bitacora.domain.parseCoordinates
import dev.bastian.bitacora.domain.validateTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LocationStatus { IDLE, FETCHING, DENIED, PERMANENTLY_DENIED, PROVIDER_DISABLED, UNAVAILABLE }

data class EditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val notFound: Boolean = false,
    val title: String = "",
    val notes: String = "",
    val category: Category = Category.OTHER,
    val latitude: String = "",
    val longitude: String = "",
    val photoPath: String? = null,
    val titleError: TitleError? = null,
    val notesError: Boolean = false,
    val coordinatesError: CoordinateError? = null,
    val photoError: Boolean = false,
    val locationStatus: LocationStatus = LocationStatus.IDLE,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val deletedId: String? = null,
)

class EditViewModel(
    private val repository: ObservationRepository,
    private val photoStorage: PhotoStorage,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    private var loaded = false
    private var loadedId: String? = null
    private var original: Observation? = null

    /** Photos copied into internal storage during this editing session and not yet committed. */
    private val importedPhotos = mutableListOf<String>()

    /** Loads the observation to edit, or an empty form when [id] is null. Idempotent for the same id. */
    fun load(id: String?) {
        if (loaded && loadedId == id) return
        discardImportedPhotos()
        loaded = true
        loadedId = id
        original = null
        if (id == null) {
            _uiState.value = EditUiState(isLoading = false, isNew = true)
            return
        }
        _uiState.value = EditUiState(isLoading = true, isNew = false)
        viewModelScope.launch {
            val observation = repository.get(id)
            if (loadedId != id) return@launch
            original = observation
            _uiState.value = if (observation == null) {
                EditUiState(isLoading = false, isNew = false, notFound = true)
            } else {
                EditUiState(
                    isLoading = false,
                    isNew = false,
                    title = observation.title,
                    notes = observation.notes,
                    category = observation.category,
                    latitude = observation.latitude?.let(::formatCoordinate).orEmpty(),
                    longitude = observation.longitude?.let(::formatCoordinate).orEmpty(),
                    photoPath = observation.photoPath,
                )
            }
        }
    }

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value, titleError = null) }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value, notesError = false) }

    fun onCategoryChange(value: Category) = _uiState.update { it.copy(category = value) }

    fun onLatitudeChange(value: String) = _uiState.update { it.copy(latitude = value, coordinatesError = null) }

    fun onLongitudeChange(value: String) = _uiState.update { it.copy(longitude = value, coordinatesError = null) }

    /** Called with the picked image, or null when the picker was dismissed. */
    fun onPhotoPicked(uri: String?) {
        if (uri == null) return
        viewModelScope.launch {
            photoStorage.import(uri).fold(
                onSuccess = { path ->
                    releaseImportedPhoto(_uiState.value.photoPath)
                    importedPhotos += path
                    _uiState.update { it.copy(photoPath = path, photoError = false) }
                },
                onFailure = { _uiState.update { it.copy(photoError = true) } },
            )
        }
    }

    fun onPhotoRemoved() {
        releaseImportedPhoto(_uiState.value.photoPath)
        _uiState.update { it.copy(photoPath = null, photoError = false) }
    }

    /** Asks the provider for a fix. The UI makes sure the location permission is granted first. */
    fun fetchLocation() {
        if (_uiState.value.locationStatus == LocationStatus.FETCHING) return
        _uiState.update { it.copy(locationStatus = LocationStatus.FETCHING) }
        viewModelScope.launch {
            val result = locationProvider.currentLocation()
            _uiState.update { state ->
                when (result) {
                    is LocationResult.Found -> state.copy(
                        latitude = formatCoordinate(result.latitude),
                        longitude = formatCoordinate(result.longitude),
                        coordinatesError = null,
                        locationStatus = LocationStatus.IDLE,
                    )
                    LocationResult.PermissionDenied -> state.copy(locationStatus = LocationStatus.DENIED)
                    LocationResult.ProviderDisabled -> state.copy(locationStatus = LocationStatus.PROVIDER_DISABLED)
                    LocationResult.Unavailable -> state.copy(locationStatus = LocationStatus.UNAVAILABLE)
                }
            }
        }
    }

    fun onLocationPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        if (granted) {
            fetchLocation()
        } else {
            val status = if (permanentlyDenied) LocationStatus.PERMANENTLY_DENIED else LocationStatus.DENIED
            _uiState.update { it.copy(locationStatus = status) }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.saved) return
        val titleError = validateTitle(state.title)
        val notesError = notesTooLong(state.notes)
        val coordinates = parseCoordinates(state.latitude, state.longitude)
        if (titleError != null || notesError || coordinates.error != null) {
            _uiState.update {
                it.copy(titleError = titleError, notesError = notesError, coordinatesError = coordinates.error)
            }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val base = original
            repository.save(
                ObservationDraft(
                    id = loadedId,
                    title = state.title,
                    notes = state.notes,
                    category = state.category,
                    latitude = coordinates.latitude,
                    longitude = coordinates.longitude,
                    photoPath = state.photoPath,
                    observedAt = base?.observedAt,
                ),
            )
            commitPhotos(finalPath = state.photoPath)
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }

    fun delete() {
        val id = loadedId ?: return
        viewModelScope.launch {
            repository.delete(id)
            discardImportedPhotos()
            _uiState.update { it.copy(deletedId = id) }
        }
    }

    /** The screen reacted to [EditUiState.saved] or [EditUiState.deletedId]; the next load starts clean. */
    fun consumeResult() {
        loaded = false
    }

    /** The user left the form without saving. */
    fun cancel() {
        discardImportedPhotos()
        loaded = false
    }

    override fun onCleared() {
        discardImportedPhotos()
    }

    /** After a save only the photo the record now points to may remain on disk. */
    private fun commitPhotos(finalPath: String?) {
        val obsolete = (importedPhotos + listOfNotNull(original?.photoPath)).distinct().filter { it != finalPath }
        obsolete.forEach(photoStorage::delete)
        importedPhotos.clear()
        original = original?.copy(photoPath = finalPath)
    }

    private fun discardImportedPhotos() {
        importedPhotos.forEach(photoStorage::delete)
        importedPhotos.clear()
    }

    /** Deletes a photo imported in this session when it is replaced or removed before saving. */
    private fun releaseImportedPhoto(path: String?) {
        if (path != null && importedPhotos.remove(path)) photoStorage.delete(path)
    }
}
