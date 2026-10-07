package app.prajakeeyam.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.prajakeeyam.AppContainer
import app.prajakeeyam.data.Page
import app.prajakeeyam.data.Place
import app.prajakeeyam.data.Problem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Scope { VILLAGE, MANDAL, CONSTITUENCY }

class FeedViewModel(private val c: AppContainer, private val place: Place) : ViewModel() {

    data class State(
        val scope: Scope,
        val sort: String = "new",
        val items: List<Problem> = emptyList(),
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val error: Throwable? = null,
        val nextCursor: String? = null,
    )

    private val _state = MutableStateFlow(State(scope = if (place.villageId != null) Scope.VILLAGE else Scope.MANDAL))
    val state: StateFlow<State> = _state
    private val inFlightSupport = mutableSetOf<Int>()

    init {
        load()
        viewModelScope.launch { c.problemUpdates.collect { onProblemUpdate(it) } }
    }

    fun setScope(scope: Scope) {
        if (scope == _state.value.scope) return
        _state.update { it.copy(scope = scope, items = emptyList(), nextCursor = null) }
        load()
    }

    fun setSort(sort: String) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort, items = emptyList(), nextCursor = null) }
        load()
    }

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = !refresh && it.items.isEmpty(), refreshing = refresh, error = null) }
            val snapshot = _state.value
            try {
                val page = fetch(snapshot, cursor = null)
                _state.update { it.copy(items = page.items, nextCursor = page.nextCursor, loading = false, refreshing = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, refreshing = false, error = e) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.nextCursor == null || s.loadingMore || s.loading || s.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val page = fetch(s, cursor = s.nextCursor)
                _state.update { it.copy(items = it.items + page.items.filter { p -> it.items.none { old -> old.id == p.id } }, nextCursor = page.nextCursor, loadingMore = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    /** Support straight from the card: optimistic flip, corrected by the server. */
    fun toggleSupport(problemId: Int) {
        val current = _state.value.items.firstOrNull { it.id == problemId } ?: return
        if (!inFlightSupport.add(problemId)) return
        val optimistic = current.copy(myUpvote = !current.myUpvote, upvoteCount = current.upvoteCount + if (current.myUpvote) -1 else 1)
        replace(optimistic)
        viewModelScope.launch {
            try {
                val (upvoted, count) = c.api.toggleUpvote(problemId)
                val fixed = (_state.value.items.firstOrNull { it.id == problemId } ?: optimistic).copy(myUpvote = upvoted, upvoteCount = count)
                replace(fixed)
                c.problemUpdates.tryEmit(fixed)
            } catch (e: Exception) {
                replace(current)
            } finally {
                inFlightSupport.remove(problemId)
            }
        }
    }

    private fun replace(p: Problem) = _state.update { st -> st.copy(items = st.items.map { if (it.id == p.id) p else it }) }

    private suspend fun fetch(s: State, cursor: String?): Page<Problem> = when (s.scope) {
        Scope.VILLAGE -> c.api.problems(villageId = place.villageId, sort = s.sort, cursor = cursor)
        Scope.MANDAL -> c.api.problems(mandalId = place.mandalId, sort = s.sort, cursor = cursor)
        Scope.CONSTITUENCY -> c.api.problems(constituencyId = place.constituencyId, sort = s.sort, cursor = cursor)
    }

    private fun onProblemUpdate(p: Problem) {
        _state.update { st ->
            val inScope = when (st.scope) {
                Scope.VILLAGE -> p.villageId == place.villageId
                Scope.MANDAL -> p.mandalId == place.mandalId
                Scope.CONSTITUENCY -> p.constituencyId == place.constituencyId
            }
            val exists = st.items.any { it.id == p.id }
            val items = when {
                p.isHidden -> st.items.filter { it.id != p.id }
                exists -> st.items.map { if (it.id == p.id) p else it }
                inScope -> listOf(p) + st.items
                else -> st.items
            }
            st.copy(items = items)
        }
    }
}
