package benson.tracker.common.views

import android.content.Context
import android.support.v4.widget.NestedScrollView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration


class NestedScroll : NestedScrollView {

    private var slop: Int = 0

    constructor(context: Context) : super(context) {
        init(context)
    }

    private fun init(context: Context) {
        val config = ViewConfiguration.get(context)
        slop = config.scaledEdgeSlop
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context)
    }


    private var xDistance: Float = 0.toFloat()
    private var yDistance: Float = 0.toFloat()
    private var lastX: Float = 0.toFloat()
    private var lastY: Float = 0.toFloat()

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                yDistance = 0f
                xDistance = yDistance

                lastX = ev.x
                lastY = ev.y
            }
            MotionEvent.ACTION_MOVE -> {
                xDistance += Math.abs(ev.x - lastX)
                yDistance += Math.abs(ev.y - lastY)

                lastX = ev.x
                lastY = ev.y

                if (xDistance > yDistance)
                    return false
            }
        }

        return super.onInterceptTouchEvent(ev) || ev.pointerCount == 2
    }
}
