package budgetmate.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import budgetmate.MainActivity
import budgetmate.R
import budgetmate.data.model.UserResponse
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class AdminFragment : Fragment() {

    private val pendingDeleteUserIds = mutableSetOf<Long>()

    private val rowAdapter by lazy {
        UserRowAdapter(
            onDelete = { user -> confirmDeleteUser((requireActivity() as MainActivity).appViewModel, user) },
            onOpenDetails = { user -> showUserDetailsDialog(user) }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_admin, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vm = (requireActivity() as MainActivity).appViewModel
        val tvTotalUsers = view.findViewById<TextView>(R.id.tvAdminTotalUsers)
        val tvActiveUsers = view.findViewById<TextView>(R.id.tvAdminActiveUsers)
        val tvInactiveUsers = view.findViewById<TextView>(R.id.tvAdminInactiveUsers)

        val listUsers = view.findViewById<ListView>(R.id.listUsers)
        listUsers.adapter = rowAdapter

        vm.loadUsers()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.uiState.collect { state ->
                    rowAdapter.submit(state.users)

                    val total = state.users.size
                    val active = state.users.count { (it.status ?: "").equals("ACTIVE", ignoreCase = true) }
                    val inactive = total - active

                    tvTotalUsers.text = total.toString()
                    tvActiveUsers.text = active.toString()
                    tvInactiveUsers.text = inactive.toString()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val vm = (requireActivity() as MainActivity).appViewModel
        vm.loadUsers()
    }

    private fun confirmDeleteUser(vm: AppViewModel, user: UserResponse) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete User")
            .setMessage("Delete user \"${user.name}\"?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                if (!pendingDeleteUserIds.add(user.id)) {
                    return@setPositiveButton
                }
                rowAdapter.setPendingDeleteIds(pendingDeleteUserIds)
                vm.deleteUser(user.id) {
                    pendingDeleteUserIds.remove(user.id)
                    if (isAdded) {
                        rowAdapter.setPendingDeleteIds(pendingDeleteUserIds)
                    }
                }
            }
            .show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.apply {
            isLongClickable = false
            setOnLongClickListener { true }
            TooltipCompat.setTooltipText(this, null)
            contentDescription = null
        }
    }

    private fun showUserDetailsDialog(user: UserResponse) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_user_details, null)
        val role = if (user.roles.any { it == "ROLE_ADMIN" || it == "ADMIN" }) "Admin" else "User"
        val status = if ((user.status ?: "").equals("ACTIVE", ignoreCase = true)) "Active" else "Inactive"

        dialogView.findViewById<TextView>(R.id.tvDialogUserName).text = user.name
        dialogView.findViewById<TextView>(R.id.tvDialogUserEmail).text = user.email
        dialogView.findViewById<TextView>(R.id.tvDialogUserRole).text = role
        dialogView.findViewById<TextView>(R.id.tvDialogUserStatus).text = status
        dialogView.findViewById<TextView>(R.id.tvDialogUserLastLogin).text = formatLastLogin(user.lastLoginAt)

        val tvRole = dialogView.findViewById<TextView>(R.id.tvDialogUserRole)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvDialogUserStatus)

        if (role == "Admin") {
            tvRole.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_red)
            tvRole.setTextColor(ContextCompat.getColor(requireContext(), R.color.bm_chip_red_text))
        } else {
            tvRole.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_blue)
            tvRole.setTextColor(ContextCompat.getColor(requireContext(), R.color.bm_chip_blue_text))
        }

        if (status == "Active") {
            tvStatus.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_green)
            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.bm_chip_green_text))
        } else {
            tvStatus.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_gray)
            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.bm_chip_gray_text))
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()

        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
    }

    private fun formatLastLogin(lastLoginAt: String?): String {
        if (lastLoginAt.isNullOrBlank()) {
            return "Never"
        }
        return lastLoginAt.take(10)
    }

    private class UserRowAdapter(
        private val onDelete: (UserResponse) -> Unit,
        private val onOpenDetails: (UserResponse) -> Unit
    ) : BaseAdapter() {
        private var items: List<UserResponse> = emptyList()
        private var pendingDeleteIds: Set<Long> = emptySet()

        fun submit(newItems: List<UserResponse>) {
            items = newItems
            notifyDataSetChanged()
        }

        fun setPendingDeleteIds(ids: Set<Long>) {
            pendingDeleteIds = ids.toSet()
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): Any = items[position]

        override fun getItemId(position: Int): Long = items[position].id

        private fun createSpinAnimation(): Animation {
            return RotateAnimation(
                0f,
                360f,
                Animation.RELATIVE_TO_SELF,
                0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f
            ).apply {
                duration = 700
                repeatCount = Animation.INFINITE
                interpolator = LinearInterpolator()
            }
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_user_action_row, parent, false)

            val item = items[position]
            val tvEmail = view.findViewById<TextView>(R.id.tvUserEmail)
            val tvStatus = view.findViewById<TextView>(R.id.tvUserStatus)
            val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteUserRow)
            btnDelete.isLongClickable = false
            btnDelete.setOnLongClickListener { true }
            TooltipCompat.setTooltipText(btnDelete, null)
            btnDelete.contentDescription = null
            btnDelete.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

            val role = if (item.roles.any { it == "ROLE_ADMIN" || it == "ADMIN" }) "Admin" else "User"
            val status = if ((item.status ?: "").equals("ACTIVE", ignoreCase = true)) "Active" else "Inactive"
            val isProtectedAdmin = role == "Admin"
            val isDeleting = pendingDeleteIds.contains(item.id)

            tvEmail.text = item.email
            tvStatus.text = status

            if (status == "Active") {
                tvStatus.background = ContextCompat.getDrawable(view.context, R.drawable.bg_chip_green)
                tvStatus.setTextColor(ContextCompat.getColor(view.context, R.color.bm_chip_green_text))
            } else {
                tvStatus.background = ContextCompat.getDrawable(view.context, R.drawable.bg_chip_gray)
                tvStatus.setTextColor(ContextCompat.getColor(view.context, R.color.bm_chip_gray_text))
            }

            if (isProtectedAdmin) {
                btnDelete.alpha = 0.35f
                btnDelete.isEnabled = false
                btnDelete.setImageResource(android.R.drawable.ic_menu_delete)
                btnDelete.imageTintList = ContextCompat.getColorStateList(view.context, R.color.bm_error)
                btnDelete.clearAnimation()
                btnDelete.setOnClickListener(null)
            } else if (isDeleting) {
                btnDelete.alpha = 0.35f
                btnDelete.isEnabled = false
                btnDelete.setImageResource(android.R.drawable.ic_popup_sync)
                btnDelete.imageTintList = ContextCompat.getColorStateList(view.context, R.color.bm_text_secondary)
                btnDelete.startAnimation(createSpinAnimation())
                btnDelete.setOnClickListener(null)
            } else {
                btnDelete.alpha = 1f
                btnDelete.isEnabled = true
                btnDelete.setImageResource(android.R.drawable.ic_menu_delete)
                btnDelete.imageTintList = ContextCompat.getColorStateList(view.context, R.color.bm_error)
                btnDelete.clearAnimation()
                btnDelete.setOnClickListener { onDelete(item) }
            }

            view.setOnClickListener { onOpenDetails(item) }

            return view
        }
    }
}
