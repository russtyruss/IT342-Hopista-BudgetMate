package budgetmate.features.expense.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
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
import budgetmate.shared.data.model.BudgetResponse
import budgetmate.shared.data.model.ExpenseRequest
import budgetmate.shared.data.model.ExpenseResponse
import budgetmate.shared.ui.CurrencyUi
import budgetmate.shared.viewmodel.AppViewModel
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.Calendar

class ExpensesFragment : Fragment() {

    private val expenseCategories = listOf(
        "Food",
        "Transportation",
        "Utilities",
        "Housing",
        "Healthcare",
        "Education",
        "Shopping",
        "Entertainment",
        "Savings",
        "Other"
    )

    private var lastErrorMessage: String? = null
    private val pendingDeleteExpenseIds = mutableSetOf<Long>()
    private var budgetCategorySequenceById: Map<Long, Int> = emptyMap()

    private val rowAdapter by lazy {
        ExpenseRowAdapter(
            onOpenDetails = { expense -> showExpenseDetailsDialog(expense) },
            onEdit = { expense -> showCreateExpenseDialog((requireActivity() as MainActivity).appViewModel, expense) },
            onDelete = { expense -> confirmDeleteExpense((requireActivity() as MainActivity).appViewModel, expense) }
        )
    }

    private var latestBudgets: List<BudgetResponse> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_expenses, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vm = (requireActivity() as MainActivity).appViewModel

        val listExpenses = view.findViewById<ListView>(R.id.listExpenses)
        listExpenses.adapter = rowAdapter

        view.findViewById<Button>(R.id.btnAddExpense).setOnClickListener {
            showCreateExpenseDialog(vm)
        }

        vm.loadBudgets()
        vm.loadExpenses()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.uiState.collect { state ->
                    latestBudgets = state.budgets
                    budgetCategorySequenceById = buildCategorySequenceByBudgetId(state.budgets)
                    rowAdapter.submit(state.expenses, state.budgets, state.selectedCurrency)

                    val errorMessage = state.errorMessage
                    if (!errorMessage.isNullOrBlank() && errorMessage != lastErrorMessage) {
                        lastErrorMessage = errorMessage
                        showErrorDialog(errorMessage)
                        vm.clearMessages()
                        lastErrorMessage = null
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val vm = (requireActivity() as MainActivity).appViewModel
        vm.loadBudgets()
        vm.loadExpenses()
    }

    private fun showCreateExpenseDialog(vm: AppViewModel, existing: ExpenseResponse? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_expense_form, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etExpenseTitle)
        val etAmount = dialogView.findViewById<EditText>(R.id.etExpenseAmount)
        val etDate = dialogView.findViewById<EditText>(R.id.etExpenseDate)
        val etNotes = dialogView.findViewById<EditText>(R.id.etExpenseNotes)
        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinnerExpenseCategory)
        val etCustomCategory = dialogView.findViewById<EditText>(R.id.etExpenseCustomCategory)
        val layoutExpenseCategory = dialogView.findViewById<LinearLayout>(R.id.layoutExpenseCategory)
        val toggleLinkBudget = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggleLinkBudget)
        val layoutLinkedBudget = dialogView.findViewById<LinearLayout>(R.id.layoutLinkedBudget)
        val spinnerBudgetLink = dialogView.findViewById<Spinner>(R.id.spinnerBudgetLink)

        bindDatePicker(etDate)

        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            expenseCategories
        )
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter

        fun updateCustomCategoryVisibility() {
            val isOther = spinnerCategory.selectedItem?.toString() == "Other"
            val show = layoutExpenseCategory.visibility == View.VISIBLE && isOther
            etCustomCategory.visibility = if (show) View.VISIBLE else View.GONE
            if (!show) {
                etCustomCategory.setText("")
            }
        }

        spinnerCategory.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateCustomCategoryVisibility()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                updateCustomCategoryVisibility()
            }
        }

        val budgetLabels = latestBudgets.map { budget ->
            val displayName = formatBudgetDisplayName(budget, budgetCategorySequenceById)
            val isScheduled = !budget.startDate.isNullOrBlank() && !budget.endDate.isNullOrBlank()
            if (isScheduled) {
                "$displayName (Scheduled: ${budget.startDate} to ${budget.endDate})"
            } else {
                "$displayName (Flexible)"
            }
        }
        val budgetAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            budgetLabels
        )
        budgetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBudgetLink.adapter = budgetAdapter

        if (existing != null) {
            etTitle.setText(existing.title)
            etAmount.setText(existing.amount.toString())
            etDate.setText(existing.expenseDate)
            etNotes.setText(existing.description.orEmpty())

            val categoryIndex = expenseCategories.indexOf(existing.category)
            if (categoryIndex >= 0) {
                spinnerCategory.setSelection(categoryIndex)
            } else {
                spinnerCategory.setSelection(expenseCategories.indexOf("Other"))
                etCustomCategory.setText(existing.category)
                etCustomCategory.visibility = View.VISIBLE
            }

            if (existing.budgetId != null && latestBudgets.isNotEmpty()) {
                toggleLinkBudget.check(R.id.btnLinkBudget)
                layoutLinkedBudget.visibility = View.VISIBLE
                layoutExpenseCategory.visibility = View.GONE
                etCustomCategory.visibility = View.GONE
                val selectedIndex = latestBudgets.indexOfFirst { it.id == existing.budgetId }
                if (selectedIndex >= 0) {
                    spinnerBudgetLink.setSelection(selectedIndex)
                }
            } else {
                toggleLinkBudget.check(R.id.btnNoLink)
                layoutExpenseCategory.visibility = View.VISIBLE
                updateCustomCategoryVisibility()
            }
        } else {
            toggleLinkBudget.check(R.id.btnNoLink)
            layoutExpenseCategory.visibility = View.VISIBLE
            updateCustomCategoryVisibility()
        }

        toggleLinkBudget.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) {
                return@addOnButtonCheckedListener
            }
            layoutLinkedBudget.visibility = if (checkedId == R.id.btnLinkBudget) View.VISIBLE else View.GONE
            layoutExpenseCategory.visibility = if (checkedId == R.id.btnLinkBudget) View.GONE else View.VISIBLE
            updateCustomCategoryVisibility()
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (existing == null) "Save" else "Update") { _, _ ->
                val title = etTitle.text.toString().trim()
                val amount = etAmount.text.toString().toDoubleOrNull()
                val date = etDate.text.toString().trim()
                val notes = etNotes.text.toString().trim()
                val selectedCategory = spinnerCategory.selectedItem?.toString()?.trim().orEmpty()
                val customCategory = etCustomCategory.text.toString().trim()
                val linkedBudgetId = if (toggleLinkBudget.checkedButtonId == R.id.btnLinkBudget && latestBudgets.isNotEmpty()) {
                    latestBudgets.getOrNull(spinnerBudgetLink.selectedItemPosition)?.id
                } else {
                    null
                }

                val resolvedCategory = if (linkedBudgetId != null) {
                    latestBudgets.firstOrNull { it.id == linkedBudgetId }?.category.orEmpty()
                } else {
                    if (selectedCategory == "Other") customCategory else selectedCategory
                }

                if (title.isBlank() || amount == null || date.isBlank() || resolvedCategory.isBlank()) {
                    Toast.makeText(requireContext(), "Please complete all required fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (amount <= 0.0) {
                    showValidationDialog(
                        title = "Invalid Amount",
                        message = "Amount is invalid. Please enter a value greater than 0."
                    )
                    return@setPositiveButton
                }

                if (linkedBudgetId != null) {
                    val linkedBudget = latestBudgets.firstOrNull { it.id == linkedBudgetId }
                    if (linkedBudget != null) {
                        val currentSpent = linkedBudget.spentAmount
                        val budgetLimit = linkedBudget.limitAmount
                        val existingAmountOnThisBudget = if (existing != null && existing.budgetId == linkedBudget.id) {
                            existing.amount
                        } else {
                            0.0
                        }
                        val nextSpent = currentSpent - existingAmountOnThisBudget + amount

                        if (nextSpent > budgetLimit) {
                            val remaining = (budgetLimit - (currentSpent - existingAmountOnThisBudget)).coerceAtLeast(0.0)
                            showValidationDialog(
                                title = "Budget Limit Reached",
                                message = "Amount exceeds linked budget. Remaining: ${CurrencyUi.formatAmount(remaining, linkedBudget.currency)}"
                            )
                            return@setPositiveButton
                        }
                    }
                }

                val payload = ExpenseRequest(
                    title = title,
                    description = notes.ifBlank { null },
                    amount = amount,
                    category = resolvedCategory,
                    expenseDate = date,
                    currency = "PHP",
                    budgetId = linkedBudgetId
                )

                if (existing == null) {
                    vm.createExpense(payload)
                } else {
                    vm.updateExpense(existing.id, payload)
                }
            }
            .show()

        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
    }

    private fun bindDatePicker(target: EditText) {
        target.keyListener = null
        target.isFocusable = false
        target.isClickable = true
        target.showSoftInputOnFocus = false
        target.setOnClickListener { openDatePicker(target) }
    }

    private fun openDatePicker(target: EditText) {
        val calendar = Calendar.getInstance()
        val currentText = target.text?.toString().orEmpty()
        val parsed = currentText.split("-")
        if (parsed.size == 3) {
            val year = parsed[0].toIntOrNull()
            val month = parsed[1].toIntOrNull()?.minus(1)
            val day = parsed[2].toIntOrNull()
            if (year != null && month != null && day != null) {
                calendar.set(year, month, day)
            }
        }

        DatePickerDialog(
            requireContext(),
            { _, year, monthOfYear, dayOfMonth ->
                val month = (monthOfYear + 1).toString().padStart(2, '0')
                val day = dayOfMonth.toString().padStart(2, '0')
                target.setText("$year-$month-$day")
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showValidationDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun confirmDeleteExpense(vm: AppViewModel, expense: ExpenseResponse) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Expense")
            .setMessage("Delete \"${expense.title}\"?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                if (!pendingDeleteExpenseIds.add(expense.id)) {
                    return@setPositiveButton
                }
                rowAdapter.setPendingDeleteIds(pendingDeleteExpenseIds)
                vm.deleteExpense(expense.id) {
                    pendingDeleteExpenseIds.remove(expense.id)
                    if (isAdded) {
                        rowAdapter.setPendingDeleteIds(pendingDeleteExpenseIds)
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

    private fun showExpenseDetailsDialog(expense: ExpenseResponse) {
        val amount = CurrencyUi.formatAmount(expense.amount, expense.currency ?: "PHP")
        val linkedBudget = expense.budgetId?.let { budgetId ->
            latestBudgets.firstOrNull { it.id == budgetId }?.let { formatBudgetDisplayName(it, budgetCategorySequenceById) } ?: "#$budgetId"
        }
        val dialogView = layoutInflater.inflate(R.layout.dialog_expense_details, null)
        val tvDetailTitle = dialogView.findViewById<TextView>(R.id.tvDetailTitle)
        val tvDetailCategoryChip = dialogView.findViewById<TextView>(R.id.tvDetailCategoryChip)
        val tvDetailLinkedBudgetChip = dialogView.findViewById<TextView>(R.id.tvDetailLinkedBudgetChip)
        val tvDetailAmount = dialogView.findViewById<TextView>(R.id.tvDetailAmount)
        val tvDetailDate = dialogView.findViewById<TextView>(R.id.tvDetailDate)
        val layoutDetailDescription = dialogView.findViewById<LinearLayout>(R.id.layoutDetailDescription)
        val tvDetailDescription = dialogView.findViewById<TextView>(R.id.tvDetailDescription)

        tvDetailTitle.text = expense.title
        tvDetailCategoryChip.text = expense.category
        tvDetailAmount.text = amount
        tvDetailDate.text = expense.expenseDate

        if (!linkedBudget.isNullOrBlank()) {
            tvDetailLinkedBudgetChip.visibility = View.VISIBLE
            tvDetailLinkedBudgetChip.text = "Linked: $linkedBudget"
        }

        if (!expense.description.isNullOrBlank()) {
            layoutDetailDescription.visibility = View.VISIBLE
            tvDetailDescription.text = expense.description
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()

        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
    }

    private fun buildCategorySequenceByBudgetId(budgets: List<BudgetResponse>): Map<Long, Int> {
        val indicatorById = mutableMapOf<Long, Int>()
        budgets
            .groupBy { it.category.trim().lowercase() }
            .values
            .forEach { groupedBudgets ->
                val sortedByRecency = groupedBudgets
                    .sortedWith(
                        compareByDescending<BudgetResponse> {
                            (it.createdAt ?: it.updatedAt ?: it.startDate).orEmpty()
                        }.thenByDescending { it.id }
                    )

                if (sortedByRecency.size > 1) {
                    sortedByRecency.forEachIndexed { index, budget ->
                        indicatorById[budget.id] = index + 1
                    }
                }
            }
        return indicatorById
    }

    private fun formatBudgetDisplayName(
        budget: BudgetResponse,
        categorySequenceById: Map<Long, Int>
    ): String {
        val sequence = categorySequenceById[budget.id]
        return if (sequence != null) "${budget.category} $sequence" else budget.category
    }

    private class ExpenseRowAdapter(
        private val onOpenDetails: (ExpenseResponse) -> Unit,
        private val onEdit: (ExpenseResponse) -> Unit,
        private val onDelete: (ExpenseResponse) -> Unit
    ) : BaseAdapter() {
        private var items: List<ExpenseResponse> = emptyList()
        private var budgetNameById: Map<Long, String> = emptyMap()
        private var selectedCurrency: String = "PHP"
        private var pendingDeleteIds: Set<Long> = emptySet()

        fun submit(
            newItems: List<ExpenseResponse>,
            budgets: List<BudgetResponse>,
            selectedCurrency: String
        ) {
            items = newItems
            val categorySequenceById = budgets
                .groupBy { it.category.trim().lowercase() }
                .values
                .flatMap { groupedBudgets ->
                    val sortedByRecency = groupedBudgets
                        .sortedWith(
                            compareByDescending<BudgetResponse> {
                                (it.createdAt ?: it.updatedAt ?: it.startDate).orEmpty()
                            }.thenByDescending { it.id }
                        )
                    if (sortedByRecency.size <= 1) {
                        emptyList()
                    } else {
                        sortedByRecency.mapIndexed { index, budget -> budget.id to (index + 1) }
                    }
                }
                .toMap()

            budgetNameById = budgets.associate { budget ->
                val sequence = categorySequenceById[budget.id]
                val displayName = if (sequence != null) "${budget.category} $sequence" else budget.category
                budget.id to displayName
            }
            this.selectedCurrency = selectedCurrency
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
                .inflate(R.layout.item_expense_action_row, parent, false)

            val item = items[position]
            val tvDescription = view.findViewById<TextView>(R.id.tvExpenseDescription)
            val tvCategory = view.findViewById<TextView>(R.id.tvExpenseCategory)
            val tvAmount = view.findViewById<TextView>(R.id.tvExpenseAmount)
            val tvDate = view.findViewById<TextView>(R.id.tvExpenseDate)
            val btnEdit = view.findViewById<ImageButton>(R.id.btnEditExpenseRow)
            val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteExpenseRow)
            btnDelete.isLongClickable = false
            btnDelete.setOnLongClickListener { true }
            TooltipCompat.setTooltipText(btnDelete, null)
            btnDelete.contentDescription = null
            btnDelete.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

            val converted = item.amount
            val isDeleting = pendingDeleteIds.contains(item.id)
            val resolvedCategory = if (item.budgetId != null) {
                budgetNameById[item.budgetId] ?: item.category
            } else {
                item.category
            }
            tvDescription.text = item.title
            tvCategory.text = resolvedCategory
            tvAmount.text = CurrencyUi.formatAmount(converted, selectedCurrency)
            tvDate.text = "Date: ${item.expenseDate}"
            tvCategory.background = ContextCompat.getDrawable(view.context, R.drawable.bg_chip_blue)
            tvCategory.setTextColor(ContextCompat.getColor(view.context, R.color.bm_chip_blue_text))

            btnEdit.setOnClickListener { onEdit(item) }
            if (isDeleting) {
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
