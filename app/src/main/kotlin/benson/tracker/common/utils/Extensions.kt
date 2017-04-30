package benson.tracker.common.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.support.annotation.DrawableRes
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import benson.tracker.R
import com.mikhaellopez.circularimageview.CircularImageView

fun String.Toast(ctx: Context, duration: Int = Toast.LENGTH_SHORT) = Toast.makeText(ctx, this, duration).show()

fun String.isValidEmail(): Boolean {
    val regex = Regex("^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@" + "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$").matches(this)
    return regex
}

fun ViewGroup.inflate(layoutId: Int, attachToRoot: Boolean = false): View =
        LayoutInflater.from(context).inflate(layoutId, this, attachToRoot)


fun Context.getMarkerBitmapFromView(@DrawableRes resId: Int): Bitmap {
    val customMarkerView = (getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.marker_header, null)
    val markerImageView = customMarkerView.findViewById(R.id.marker_profile_photo) as CircularImageView
    markerImageView.setImageResource(resId)
    customMarkerView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    customMarkerView.layout(0, 0, customMarkerView.measuredWidth, customMarkerView.measuredHeight)
    customMarkerView.buildDrawingCache()
    val returnedBitmap = Bitmap.createBitmap(customMarkerView.measuredWidth, customMarkerView.measuredHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(returnedBitmap)
    canvas.drawColor(Color.WHITE, PorterDuff.Mode.SRC_IN)
    customMarkerView.background?.draw(canvas)
    customMarkerView.draw(canvas)
    return returnedBitmap
}