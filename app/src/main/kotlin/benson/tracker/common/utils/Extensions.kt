package benson.tracker.common.utils

import android.content.Context
import android.widget.Toast

fun String.Toast(ctx: Context, duration: Int = Toast.LENGTH_SHORT) = Toast.makeText(ctx, this, duration).show()

fun String.isValidEmail(): Boolean {
    val regex = Regex("^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@" + "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$").matches(this)
    return regex
}

