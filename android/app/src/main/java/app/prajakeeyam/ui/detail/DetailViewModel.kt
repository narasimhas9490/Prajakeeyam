package app.prajakeeyam.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.prajakeeyam.AppContainer
import app.prajakeeyam.R
import app.prajakeeyam.data.ApiException
import app.prajakeeyam.data.Comment
import app.prajakeeyam.data.Problem
import app.prajakeeyam.data.isNetworkError
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DetailViewModel(private val c: AppContainer, private val id: Int) : ViewModel() {

    data class State(
        val problem: Problem? = null,
        val comments: List<Comment> = emptyList(),
        val loading: Boolean = true,
        val error: Throwable? = null,
        val sending: Boolean = false,
        val message: Int? = null, // string resource shown in a snackbar
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.problem == null, error = null) }
            try {
                coroutineScope {
                    val p = async { c.api.problem(id) }
                    val cs = async { c.api.comments(id) }
                    _state.update { it.copy(problem = p.await(), comments = cs.await().items, loading = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e) }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun publish(p: Problem) {
        _state.update { it.copy(problem = p) }
        c.problemUpdates.tryEmit(p)
    }

    private fun fail(e: Exception) {
        _state.update { it.copy(sending = false, message = if (e.isNetworkError()) R.string.error_network else R.string.error_generic) }
    }

    fun toggleUpvote() {
        val p = _state.value.problem ?: return
        // optimistic flip, corrected by the server response
        publish(p.copy(myUpvote = !p.myUpvote, upvoteCount = p.upvoteCount + if (p.myUpvote) -1 else 1))
        viewModelScope.launch {
            try {
                val (upvoted, count) = c.api.toggleUpvote(id)
                _state.value.problem?.let { publish(it.copy(myUpvote = upvoted, upvoteCount = count)) }
            } catch (e: Exception) {
                publish(p)
                fail(e)
            }
        }
    }

    fun addComment(body: String) {
        viewModelScope.launch {
            _state.update { it.copy(sending = true) }
            try {
                val comment = c.api.addComment(id, body)
                _state.update { st ->
                    st.copy(sending = false, comments = st.comments + comment, problem = st.problem?.copy(commentCount = st.problem.commentCount + 1))
                }
                _state.value.problem?.let { c.problemUpdates.tryEmit(it) }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun report(reason: String) {
        viewModelScope.launch {
            try {
                c.api.reportProblem(id, reason)
                _state.update { it.copy(message = R.string.reported_thanks) }
            } catch (e: ApiException) {
                _state.update { it.copy(message = R.string.reported_thanks) } // already reported: still thank them
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun blockAuthor() {
        val p = _state.value.problem ?: return
        viewModelScope.launch {
            try {
                c.api.blockUser(p.userId)
                _state.update { it.copy(message = R.string.blocked_done) }
                c.problemUpdates.tryEmit(p.copy(isHidden = true))
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun setStatus(status: String) {
        viewModelScope.launch {
            try {
                publish(c.api.updateProblem(id, status = status))
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val p = c.api.updateProblem(id, hidden = true)
                c.problemUpdates.tryEmit(p.copy(isHidden = true))
                onDone()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }
}
