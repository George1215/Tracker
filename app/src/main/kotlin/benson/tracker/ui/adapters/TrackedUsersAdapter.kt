package benson.tracker.ui.adapters

import android.app.Activity
import android.content.Context
import android.support.v7.widget.AppCompatButton
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import benson.tracker.R
import benson.tracker.common.utils.Toast
import benson.tracker.domain.model.TrackedUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener


class TrackedUsersAdapter(
        var ctx: Context,
        var layoutResourceId: Int,
        var data: ArrayList<TrackedUser>,
        var dataType: String) :

        ArrayAdapter<TrackedUser>(ctx,
                layoutResourceId, data) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var row: View? = convertView
        val holder: TrackedUsersHolder?

        if (row == null) {
            val inflater = (ctx as Activity).layoutInflater
            row = inflater.inflate(layoutResourceId, parent, false)

            holder = TrackedUsersHolder()
            holder.userName = row?.findViewById(R.id.tracked_user_title) as TextView
            holder.removeUser = row.findViewById(R.id.button_remove_user) as AppCompatButton

            row.tag = holder
        } else {
            holder = row.tag as TrackedUsersHolder
        }

        val trackedUser = data[position]
        holder.userName?.text = trackedUser.username
        holder.removeUser?.setOnClickListener {
            val auth = FirebaseAuth.getInstance()
            val databaseRef = FirebaseDatabase.getInstance().reference

            databaseRef.child("username").orderByValue().equalTo(auth.currentUser?.email).addValueEventListener(object : ValueEventListener {
                override fun onCancelled(databaseError: DatabaseError?) {

                }

                override fun onDataChange(snapshot: DataSnapshot?) {
                    val name = snapshot?.value.toString().split("=")[0].split("{")[1]

                    when (dataType) {
                        "tracking" -> {
                            "${trackedUser.username} was removed from your tracking list!".Toast(ctx)

                            databaseRef.child("tracking").child(name).child(trackedUser.username).removeValue()
                            databaseRef.child("trackedBy").child(trackedUser.username).child(name).removeValue()

                            this@TrackedUsersAdapter.notifyDataSetChanged()
                            this@TrackedUsersAdapter.remove(trackedUser)
                        }

                        "trackedBy" -> {
                            "${trackedUser.username} was removed and cannot track you anymore!".Toast(ctx)

                            databaseRef.child("trackedBy").child(name).child(trackedUser.username).removeValue()
                            databaseRef.child("tracking").child(trackedUser.username).child(name).removeValue()

                            this@TrackedUsersAdapter.notifyDataSetChanged()
                            this@TrackedUsersAdapter.remove(trackedUser)
                        }
                    }
                }
            })
        }

        return row
    }

    internal class TrackedUsersHolder {
        var userName: TextView? = null
        var removeUser: AppCompatButton? = null
    }
}