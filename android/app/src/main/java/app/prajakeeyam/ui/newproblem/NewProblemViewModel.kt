package app.prajakeeyam.ui.newproblem

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.prajakeeyam.AppContainer
import app.prajakeeyam.data.ApiException
import app.prajakeeyam.data.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NewProblemViewModel(private val c: AppContainer, private val place: Place) : ViewModel() {

    data class State(
        val category: String = "road",
        val title: String = "",
        val description: String = "",
        val photoUri: Uri? = null,
        val submitting: Boolean = false,
        val uploading: Boolean = false,
        val error: String? = null,
        val posted: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun setCategory(cat: String) = _state.update { it.copy(category = cat) }
    fun setTitle(v: String) = _state.update { it.copy(title = v.take(120)) }
    fun setDescription(v: String) = _state.update { it.copy(description = v.take(2000)) }
    fun setPhoto(uri: Uri?) = _state.update { it.copy(photoUri = uri) }

    fun submit(tooShortMessage: String) {
        val s = _state.value
        if (s.title.trim().length < 3 || s.description.trim().length < 3) {
            _state.update { it.copy(error = tooShortMessage) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            try {
                var photoUrl: String? = null
                if (s.photoUri != null) {
                    _state.update { it.copy(uploading = true) }
                    photoUrl = c.uploader.upload(s.photoUri)
                    _state.update { it.copy(uploading = false) }
                }
                val problem = c.api.createProblem(
                    mandalId = place.mandalId, villageId = place.villageId, category = s.category,
                    title = s.title.trim(), description = s.description.trim(), photoUrl = photoUrl,
                )
                c.problemUpdates.tryEmit(problem)
                _state.update { it.copy(submitting = false, posted = true) }
            } catch (e: ApiException) {
                _state.update { it.copy(submitting = false, uploading = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(submitting = false, uploading = false, error = e.message ?: "error") }
            }
        }
    }
}
