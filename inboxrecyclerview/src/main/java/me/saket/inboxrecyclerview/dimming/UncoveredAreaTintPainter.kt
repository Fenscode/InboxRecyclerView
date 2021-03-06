package me.saket.inboxrecyclerview.dimming

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import me.saket.inboxrecyclerview.ANIMATION_START_DELAY
import me.saket.inboxrecyclerview.InboxRecyclerView
import me.saket.inboxrecyclerview.animation.PageLocationChangeDetector
import me.saket.inboxrecyclerview.page.PageStateChangeCallbacks

/**
 * Draws a tint on [InboxRecyclerView] only in the area that's not covered by its page.
 * This allows the page content to not have another background of its own, thus reducing
 * overdraw by a level.
 *
 * If the tinted area appears incorrect, try using [TintPainter.completeList] instead.
 */
open class UncoveredAreaTintPainter(color: Int, opacity: Float) : TintPainter(), PageStateChangeCallbacks {

  private val minIntensity = 0
  private val maxIntensity = (255 * opacity).toInt()    // [0..255]

  private var tintAnimator: ValueAnimator = ObjectAnimator()
  private var lastIsCollapseEligible = false

  protected val tintPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
  protected lateinit var recyclerView: InboxRecyclerView
  private lateinit var changeDetector: PageLocationChangeDetector

  init {
    tintPaint.color = color
    tintPaint.alpha = minIntensity
  }

  override fun onAttachRecyclerView(recyclerView: InboxRecyclerView) {
    this.recyclerView = recyclerView
    this.changeDetector = PageLocationChangeDetector(recyclerView.page, changeListener = ::onPageMove)

    recyclerView.page.viewTreeObserver.addOnGlobalLayoutListener(changeDetector)
    recyclerView.page.viewTreeObserver.addOnPreDrawListener(changeDetector)
    recyclerView.page.addStateChangeCallbacks(this)
  }

  override fun onDetachRecyclerView(recyclerView: InboxRecyclerView) {
    recyclerView.page.removeStateChangeCallbacks(this)
    recyclerView.page.viewTreeObserver.removeOnGlobalLayoutListener(changeDetector)
    recyclerView.page.viewTreeObserver.removeOnPreDrawListener(changeDetector)
    tintAnimator.cancel()
  }

  private fun onPageMove() {
    // Remove dimming when the page is being pulled and is eligible for collapse.
    if (recyclerView.page.isExpanded) {
      val collapseThreshold = recyclerView.page.pullToCollapseThresholdDistance
      val translationYAbs = Math.abs(recyclerView.page.translationY)
      val isCollapseEligible = translationYAbs >= collapseThreshold

      if (isCollapseEligible != lastIsCollapseEligible) {
        animateDimming(
            toAlpha = if (isCollapseEligible) minIntensity else maxIntensity,
            dimDuration = 300)
      }
      lastIsCollapseEligible = isCollapseEligible

    } else {
      lastIsCollapseEligible = false
    }
  }

  override fun drawTint(canvas: Canvas) {
    recyclerView.apply {
      // Content above the page.
      canvas.drawRect(0F, 0F, right.toFloat(), page.translationY, tintPaint)

      // Content below the page.
      if (page.isExpanded) {
        canvas.drawRect(0F, (bottom + page.translationY), right.toFloat(), bottom.toFloat(), tintPaint)

      } else if (page.isExpandingOrCollapsing) {
        val pageBottom = page.translationY + page.clippedDimens.height().toFloat()
        canvas.drawRect(0F, pageBottom, right.toFloat(), bottom.toFloat(), tintPaint)
      }
    }
  }

  override fun onPageAboutToExpand(expandAnimDuration: Long) {
    tintAnimator.cancel()
    animateDimming(maxIntensity, expandAnimDuration)
  }

  override fun onPageAboutToCollapse(collapseAnimDuration: Long) {
    tintAnimator.cancel()
    animateDimming(minIntensity, collapseAnimDuration)
  }

  private fun animateDimming(toAlpha: Int, dimDuration: Long) {
    tintAnimator = ObjectAnimator.ofInt(tintPaint.alpha, toAlpha).apply {
      duration = dimDuration
      interpolator = recyclerView.page.animationInterpolator
      startDelay = ANIMATION_START_DELAY
    }
    tintAnimator.addUpdateListener {
      tintPaint.alpha = it.animatedValue as Int
      recyclerView.postInvalidate()
    }
    tintAnimator.start()
  }

  override fun onPageExpanded() {}

  override fun onPageCollapsed() {}
}
