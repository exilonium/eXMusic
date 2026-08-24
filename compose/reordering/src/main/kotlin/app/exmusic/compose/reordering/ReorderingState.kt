package app.exmusic.compose.reordering

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val MAX_AUTO_SCROLL_PER_FRAME = 24

@Stable
class ReorderingState(
    val lazyListState: LazyListState,
    val coroutineScope: CoroutineScope,
    private val lastIndex: Int,
    internal val onDragStart: () -> Unit,
    internal val onDragEnd: (Int, Int) -> Unit,
    private val extraItemCount: Int
) {
    /** What [draggedItem] shifts the row by: where it should be, less where the list placed it. */
    internal var offset by mutableIntStateOf(0)
        private set

    internal var draggingIndex by mutableIntStateOf(-1)
    internal var reachedIndex by mutableIntStateOf(-1)
    internal var draggingItemSize by mutableIntStateOf(0)

    private lateinit var itemInfo: LazyListItemInfo

    private var previousItemSize = 0
    private var nextItemSize = 0

    private var minTravel = 0
    private var maxTravel = 0

    /**
     * Everything the finger has done, unrounded and unclamped. Rounding each delta as it arrives
     * drops the same fraction of a pixel every event and the row falls behind; clamping it drops
     * whatever was dragged past the end of the list and the row never catches back up.
     */
    private var dragged = 0f

    private var scrolled = 0f

    /**
     * Where the list placed the row when the drag began, and where it places it now, measured by
     * [onSlotPositioned]. A lazy list parks a pinned row by the viewport edge once it scrolls out
     * of view instead of placing it where its index falls, so adding up scroll deltas stops
     * describing where the row is and the item slides off the finger.
     */
    private var slotAtStart: Float? = null
    private var slot = 0f

    /** Drives the row while it animates into place after a drop, in place of [travel]. */
    private var settling: Float? = null

    private var autoScrollJob: Job? = null

    internal var indexesToAnimate = mutableStateMapOf<Int, Animatable<Int, AnimationVector1D>>()
    private var animatablesPool: AnimatablesPool<Int, AnimationVector1D>? = null

    val isDragging: Boolean
        get() = draggingIndex != -1

    fun isDragging(index: Int) = draggingIndex == index

    /** How far the row has moved through the list: what the finger did, plus what the list did. */
    private val travel
        get() = (dragged + scrolled).roundToInt().coerceIn(minTravel, maxTravel)

    private val settledTravel
        get() = (reachedIndex - draggingIndex) * draggingItemSize

    fun onDragStart(index: Int) {
        itemInfo = lazyListState.layoutInfo.visibleItemsInfo
            .find { it.index == index + extraItemCount } ?: return

        onDragStart()
        draggingIndex = index
        reachedIndex = index
        draggingItemSize = itemInfo.size

        offset = 0
        dragged = 0f
        scrolled = 0f
        slotAtStart = null
        settling = null

        nextItemSize = draggingItemSize
        previousItemSize = -draggingItemSize

        minTravel = -index * draggingItemSize
        maxTravel = (lastIndex - index) * draggingItemSize

        animatablesPool = AnimatablesPool(
            initialValue = 0,
            typeConverter = Int.VectorConverter
        )
    }

    fun onDrag(change: PointerInputChange, dragAmount: Offset) {
        if (!isDragging) return

        change.consume()

        dragged += when (lazyListState.layoutInfo.orientation) {
            Orientation.Vertical -> dragAmount.y
            Orientation.Horizontal -> dragAmount.x
        }

        update()
        updateAutoScroll()
    }

    /** Called by [draggedItem] every time the list places the dragged row. */
    internal fun onSlotPositioned(position: Float) {
        if (!isDragging) return

        slot = position
        if (slotAtStart == null) slotAtStart = position

        update()
    }

    fun onDragEnd() {
        if (!isDragging) return

        stopAutoScroll()

        coroutineScope.launch {
            Animatable(travel.toFloat()).animateTo(settledTravel.toFloat()) {
                settling = value
                update()
            }

            withContext(Dispatchers.Main) {
                // Moving a row changes which index every key below it sits at, and a lazy list
                // keeps whichever key was at the top of the viewport there, scrolling the list
                // under the reader. Hold the viewport where it was instead.
                val anchorIndex = lazyListState.firstVisibleItemIndex
                val anchorOffset = lazyListState.firstVisibleItemScrollOffset

                onDragEnd(draggingIndex, reachedIndex)
                lazyListState.requestScrollToItem(anchorIndex, anchorOffset)
            }

            if (areEquals()) {
                draggingIndex = -1
                reachedIndex = -1
                draggingItemSize = 0
                offset = 0
            }

            animatablesPool = null
        }
    }

    /**
     * Puts the row where the finger has dragged it and lets it pass however many rows that covers.
     * A fast drag, or a frame of auto-scroll on a short row, can cover more than one.
     */
    private fun update() {
        val start = slotAtStart ?: return
        val travel = settling?.roundToInt() ?: travel

        // Travel is measured through the list while the row is drawn on a screen the list slides
        // under. Take the scroll back out and what is left is what the finger did.
        offset = (start + travel - scrolled - slot).roundToInt()

        if (settling != null) return

        while (travel > nextItemSize && reachedIndex < lastIndex) {
            reachedIndex += 1
            nextItemSize += draggingItemSize
            previousItemSize += draggingItemSize

            val ahead = draggingIndex < reachedIndex
            animatePassedItem(
                index = reachedIndex - if (ahead) 0 else 1,
                from = if (ahead) 0 else draggingItemSize,
                to = if (ahead) -draggingItemSize else 0
            )
        }

        while (travel < previousItemSize && reachedIndex > 0) {
            reachedIndex -= 1
            previousItemSize -= draggingItemSize
            nextItemSize -= draggingItemSize

            val behind = draggingIndex > reachedIndex
            animatePassedItem(
                index = reachedIndex + if (behind) 0 else 1,
                from = if (behind) 0 else -draggingItemSize,
                to = if (behind) draggingItemSize else 0
            )
        }
    }

    private fun animatePassedItem(index: Int, from: Int, to: Int) {
        coroutineScope.launch {
            val animatable = indexesToAnimate.getOrPut(index) {
                animatablesPool?.acquire() ?: return@launch
            }

            animatable.snapTo(from)
            animatable.animateTo(to)

            indexesToAnimate.remove(index)
            animatablesPool?.release(animatable)
        }
    }

    /**
     * How far to scroll this frame, towards whichever edge the row has crossed. The viewport is in
     * the list's coordinates, so it is shifted into the window's by the one row whose place in both
     * is known.
     */
    private fun autoScrollAmount(): Int {
        val layoutInfo = lazyListState.layoutInfo
        val listOrigin = (slotAtStart ?: return 0) - itemInfo.offset

        val start = listOrigin + layoutInfo.viewportStartOffset + layoutInfo.beforeContentPadding
        val end = listOrigin + layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding

        val top = slot + offset
        val bottom = top + draggingItemSize

        return when {
            top < start -> -(start - top).roundToInt().coerceAtMost(MAX_AUTO_SCROLL_PER_FRAME)
            bottom > end -> (bottom - end).roundToInt().coerceAtMost(MAX_AUTO_SCROLL_PER_FRAME)
            else -> 0
        }
    }

    /** Scrolls for as long as the row is held past an edge. A parked finger sends no events. */
    private fun updateAutoScroll() {
        if (autoScrollJob?.isActive == true) return
        if (autoScrollAmount() == 0) return

        autoScrollJob = coroutineScope.launch {
            var scrolling = true

            while (isActive && isDragging && scrolling) {
                withFrameNanos {}

                val amount = autoScrollAmount()

                // A list scrolls on past the last row the item can reach, over the queue's
                // suggestions or a bottom padding, and carries the item along for nothing
                val blocked = (amount > 0 && travel >= maxTravel) ||
                    (amount < 0 && travel <= minTravel)

                // Only what the list moved counts. At either end it moves nothing
                val consumed = if (amount == 0 || blocked) 0f
                else lazyListState.scrollBy(amount.toFloat())

                if (consumed == 0f) scrolling = false else {
                    scrolled += consumed
                    update()
                }
            }

            autoScrollJob = null
        }
    }

    private fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    /** Whether the list has caught up with the drop: the dragged row now holds the reached key. */
    private fun areEquals(): Boolean {
        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo

        val draggingKey = visibleItems
            .find { it.index == draggingIndex + extraItemCount }
            ?.key
            ?: return false
        val reachedKey = visibleItems
            .find { it.index == reachedIndex + extraItemCount }
            ?.key
            ?: return false

        return draggingKey == reachedKey
    }
}

@Composable
fun rememberReorderingState(
    lazyListState: LazyListState,
    key: Any,
    onDragEnd: (Int, Int) -> Unit,
    onDragStart: () -> Unit = {},
    extraItemCount: Int = 0
): ReorderingState {
    val coroutineScope = rememberCoroutineScope()

    return remember(key) {
        ReorderingState(
            lazyListState = lazyListState,
            coroutineScope = coroutineScope,
            lastIndex = if (key is List<*>) key.lastIndex else lazyListState.layoutInfo.totalItemsCount,
            onDragStart = onDragStart,
            onDragEnd = onDragEnd,
            extraItemCount = extraItemCount
        )
    }
}
