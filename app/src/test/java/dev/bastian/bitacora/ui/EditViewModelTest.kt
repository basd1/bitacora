package dev.bastian.bitacora.ui

import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.domain.CoordinateError
import dev.bastian.bitacora.domain.LocationResult
import dev.bastian.bitacora.domain.TitleError
import dev.bastian.bitacora.testing.FakeLocationProvider
import dev.bastian.bitacora.testing.FakeObservationRepository
import dev.bastian.bitacora.testing.FakePhotoStorage
import dev.bastian.bitacora.testing.MainDispatcherRule
import dev.bastian.bitacora.testing.observation
import dev.bastian.bitacora.ui.edit.EditViewModel
import dev.bastian.bitacora.ui.edit.LocationStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeObservationRepository()
    private val photos = FakePhotoStorage()
    private val location = FakeLocationProvider()
    private val viewModel = EditViewModel(repository, photos, location)

    @Test
    fun newFormStartsEmptyAndLoaded() {
        viewModel.load(null)
        val state = viewModel.uiState.value
        assertTrue(state.isNew)
        assertFalse(state.isLoading)
        assertEquals("", state.title)
    }

    @Test
    fun blankTitleIsRejectedAndNothingIsSaved() {
        viewModel.load(null)
        viewModel.onTitleChange("   ")
        viewModel.save()
        assertEquals(TitleError.EMPTY, viewModel.uiState.value.titleError)
        assertTrue(repository.savedDrafts.isEmpty())
    }

    @Test
    fun editingTheTitleClearsItsError() {
        viewModel.load(null)
        viewModel.save()
        viewModel.onTitleChange("Heron")
        assertNull(viewModel.uiState.value.titleError)
    }

    @Test
    fun validFormIsSavedAsANewObservation() = runTest {
        viewModel.load(null)
        viewModel.onTitleChange("Heron")
        viewModel.onNotesChange("by the river")
        viewModel.onCategoryChange(Category.FAUNA)
        viewModel.save()
        val draft = repository.savedDrafts.single()
        assertNull(draft.id)
        assertEquals("Heron", draft.title)
        assertEquals(Category.FAUNA, draft.category)
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun loadingAnExistingObservationFillsTheForm() = runTest {
        repository.seed(observation(id = "x", title = "Oak", notes = "tall", category = Category.FLORA))
        viewModel.load("x")
        val state = viewModel.uiState.value
        assertFalse(state.isNew)
        assertEquals("Oak", state.title)
        assertEquals("tall", state.notes)
        assertEquals(Category.FLORA, state.category)
    }

    @Test
    fun savingAnExistingObservationKeepsItsId() = runTest {
        repository.seed(observation(id = "x", title = "Oak"))
        viewModel.load("x")
        viewModel.onTitleChange("Old oak")
        viewModel.save()
        assertEquals("x", repository.savedDrafts.single().id)
    }

    @Test
    fun missingObservationIsReportedAsNotFound() = runTest {
        viewModel.load("missing")
        assertTrue(viewModel.uiState.value.notFound)
    }

    @Test
    fun deleteReportsTheDeletedId() = runTest {
        repository.seed(observation(id = "x"))
        viewModel.load("x")
        viewModel.delete()
        assertEquals("x", viewModel.uiState.value.deletedId)
    }

    @Test
    fun loadingAgainAfterConsumingTheResultStartsClean() = runTest {
        viewModel.load(null)
        viewModel.onTitleChange("Heron")
        viewModel.save()
        viewModel.consumeResult()
        viewModel.load(null)
        assertEquals("", viewModel.uiState.value.title)
        assertFalse(viewModel.uiState.value.saved)
    }

    @Test
    fun manualCoordinatesAreSavedAsNumbers() = runTest {
        viewModel.load(null)
        viewModel.onTitleChange("Heron")
        viewModel.onLatitudeChange("40,5")
        viewModel.onLongitudeChange("-3.25")
        viewModel.save()
        val draft = repository.savedDrafts.single()
        assertEquals(40.5, draft.latitude!!, 0.0)
        assertEquals(-3.25, draft.longitude!!, 0.0)
    }

    @Test
    fun invalidCoordinatesBlockTheSave() {
        viewModel.load(null)
        viewModel.onTitleChange("Heron")
        viewModel.onLatitudeChange("95")
        viewModel.onLongitudeChange("0")
        viewModel.save()
        assertEquals(CoordinateError.OUT_OF_RANGE, viewModel.uiState.value.coordinatesError)
        assertTrue(repository.savedDrafts.isEmpty())
    }

    @Test
    fun locationFixFillsTheCoordinateFields() = runTest {
        location.result = LocationResult.Found(40.4168, -3.7038)
        viewModel.load(null)
        viewModel.fetchLocation()
        val state = viewModel.uiState.value
        assertEquals("40.416800", state.latitude)
        assertEquals("-3.703800", state.longitude)
        assertEquals(LocationStatus.IDLE, state.locationStatus)
    }

    @Test
    fun deniedPermissionKeepsManualEntryAvailable() {
        viewModel.load(null)
        viewModel.onLocationPermissionResult(granted = false, permanentlyDenied = false)
        assertEquals(LocationStatus.DENIED, viewModel.uiState.value.locationStatus)
        viewModel.onLatitudeChange("10")
        assertEquals("10", viewModel.uiState.value.latitude)
        assertEquals(0, location.calls)
    }

    @Test
    fun permanentDenialIsReportedSeparately() {
        viewModel.load(null)
        viewModel.onLocationPermissionResult(granted = false, permanentlyDenied = true)
        assertEquals(LocationStatus.PERMANENTLY_DENIED, viewModel.uiState.value.locationStatus)
    }

    @Test
    fun grantedPermissionTriggersAFix() = runTest {
        location.result = LocationResult.Found(1.0, 2.0)
        viewModel.load(null)
        viewModel.onLocationPermissionResult(granted = true, permanentlyDenied = false)
        assertEquals(1, location.calls)
        assertEquals("1.000000", viewModel.uiState.value.latitude)
    }

    @Test
    fun disabledProviderAndMissingFixAreReported() = runTest {
        viewModel.load(null)
        location.result = LocationResult.ProviderDisabled
        viewModel.fetchLocation()
        assertEquals(LocationStatus.PROVIDER_DISABLED, viewModel.uiState.value.locationStatus)
        location.result = LocationResult.Unavailable
        viewModel.fetchLocation()
        assertEquals(LocationStatus.UNAVAILABLE, viewModel.uiState.value.locationStatus)
    }

    @Test
    fun pickedPhotoIsImportedAndSavedWithTheObservation() = runTest {
        viewModel.load(null)
        viewModel.onTitleChange("Heron")
        viewModel.onPhotoPicked("content://media/1")
        val path = viewModel.uiState.value.photoPath
        assertEquals("/photos/1.jpg", path)
        viewModel.save()
        assertEquals(path, repository.savedDrafts.single().photoPath)
        assertTrue("/photos/1.jpg" in photos.existing)
    }

    @Test
    fun failedImportShowsAnErrorAndKeepsTheForm() = runTest {
        photos.failImports = true
        viewModel.load(null)
        viewModel.onPhotoPicked("content://media/1")
        assertTrue(viewModel.uiState.value.photoError)
        assertNull(viewModel.uiState.value.photoPath)
    }

    @Test
    fun dismissedPickerChangesNothing() = runTest {
        viewModel.load(null)
        viewModel.onPhotoPicked(null)
        assertNull(viewModel.uiState.value.photoPath)
        assertTrue(photos.existing.isEmpty())
    }

    @Test
    fun replacingAnUnsavedPhotoDeletesTheFirstCopy() = runTest {
        viewModel.load(null)
        viewModel.onPhotoPicked("content://media/1")
        viewModel.onPhotoPicked("content://media/2")
        assertEquals(setOf("/photos/2.jpg"), photos.existing)
    }

    @Test
    fun cancellingDiscardsPhotosImportedInTheSession() = runTest {
        viewModel.load(null)
        viewModel.onPhotoPicked("content://media/1")
        viewModel.cancel()
        assertTrue(photos.existing.isEmpty())
    }

    @Test
    fun removingThePhotoOfASavedObservationDeletesTheFileOnSave() = runTest {
        photos.existing += "/photos/old.jpg"
        repository.seed(observation(id = "x", title = "Oak").copy(photoPath = "/photos/old.jpg"))
        viewModel.load("x")
        viewModel.onPhotoRemoved()
        viewModel.save()
        assertNull(repository.savedDrafts.single().photoPath)
        assertFalse(photos.existing.contains("/photos/old.jpg"))
    }
}
