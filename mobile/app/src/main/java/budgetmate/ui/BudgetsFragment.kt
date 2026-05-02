package budgetmate.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ArrayAdapter
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
import budgetmate.data.model.BudgetRequest
import budgetmate.data.model.BudgetResponse
import budgetmate.data.model.ExpenseResponse
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.Calendar

class BudgetsFragment : Fragment() {

    private var lastErrorMessage: String? = null
    private var latestExpenses: List<ExpenseResponse> = emptyList()
    private var budgetCategorySequenceById: Map<Long, Int> = emptyMap()
    private val pendingDeleteBudgetIds = mutableSetOf<Long>()
    private val budgetCategories = listOf("Food", "Transport", "Shopping", "Entertainment", "Health", "Education", "Utilities", "Other")

    private val rowAdapter by lazy {
        BudgetRowAdapter(
            onOpenLinkedExpenses = { budget -> showLinkedExpensesDialog(budget) },
            onEdit = { budget -> showCreateBudgetDialog((requireActivity() as MainActivity).appViewModel, budget) },
            onDelete = { budget -> confirmDeleteBudget((requireActivity() as MainActivity).appViewModel, budget) }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_budgets, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vm = (requireActivity() as MainActivity).appViewModel

        val listBudgets = view.findViewById<ListView>(R.id.listBudgets)
        listBudgets.adapter = rowAdapter

        view.findViewById<Button>(R.id.btnAddBudget).setOnClickListener {
            showCreateBudgetDialog(vm)
        }

        vm.loadBudgets()
        vm.loadExpenses()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.uiState.collect { state ->
                    budgetCategorySequenceById = buildCategorySequenceByBudgetId(state.budgets)
                    rowAdapter.submit(state.budgets, state.selectedCurrency, state.exchangeRates, budgetCategorySequenceById)
                    latestExpenses = state.expenses

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

    private fun showCreateBudgetDialog(vm: AppViewModel, existing: BudgetResponse? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_budget_form, null)
        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.etBudgetCategory)
        val etCustomCategory = dialogView.findViewById<EditText>(R.id.etBudgetCustomCategory)
        val etLimit = dialogView.findViewById<EditText>(R.id.etBudgetLimit)
        val etNotes = dialogView.findViewById<EditText>(R.id.etBudgetNotes)
        val etStart = dialogView.findViewById<EditText>(R.id.etBudgetStartDate)
        val etEnd = dialogView.findViewById<EditText>(R.id.etBudgetEndDate)
        val layoutScheduleDates = dialogView.findViewById<View>(R.id.layoutScheduleDates)
        val toggleSchedule = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggleBudgetSchedule)

        bindDatePicker(etStart)
        bindDatePicker(etEnd)
        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, budgetCategories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter

        fun toggleCustomCategoryInput() {
            val selectedCategory = spinnerCategory.selectedItem?.toString().orEmpty()
            etCustomCategory.visibility = if (selectedCategory == "Other") View.VISIBLE else View.GONE
            if (selectedCategory != "Other") {
                etCustomCategory.setText("")
            }
        }

        spinnerCategory.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                toggleCustomCategoryInput()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                toggleCustomCategoryInput()
            }
        }

        fun setSchedule(hasSchedule: Boolean) {
            toggleSchedule.check(if (hasSchedule) R.id.btnBudgetScheduled else R.id.btnBudgetFlexible)
            layoutScheduleDates.visibility = if (hasSchedule) View.VISIBLE else View.GONE
            if (!hasSchedule) {
                etStart.setText("")
                etEnd.setText("")
            }
        }

        toggleSchedule.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) {
                return@addOnButtonCheckedListener
            }
            layoutScheduleDates.visibility = if (checkedId == R.id.btnBudgetScheduled) View.VISIBLE else View.GONE
            if (checkedId == R.id.btnBudgetFlexible) {
                etStart.setText("")
                etEnd.setText("")
            }
        }

        if (existing != null) {
            val categoryIndex = budgetCategories.indexOf(existing.category)
            if (categoryIndex >= 0) {
                spinnerCategory.setSelection(categoryIndex)
            } else {
                spinnerCategory.setSelection(budgetCategories.indexOf("Other"))
                etCustomCategory.setText(existing.category)
                etCustomCategory.visibility = View.VISIBLE
            }
            etLimit.setText(existing.limitAmount.toString())
            etNotes.setText(existing.notes.orEmpty())
            etStart.setText(existing.startDate ?: "")
            etEnd.setText(existing.endDate ?: "")
            setSchedule(!existing.startDate.isNullOrBlank() && !existing.endDate.isNullOrBlank())
        } else {
            spinnerCategory.setSelection(0)
            setSchedule(false)
        }

        toggleCustomCategoryInput()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (existing == null) "Save" else "Update") { _, _ ->
                val selectedCategory = spinnerCategory.selectedItem?.toString().orEmpty().trim()
                val category = if (selectedCategory == "Other") etCustomCategory.text.toString().trim() else selectedCategory
                val limit = etLimit.text.toString().toDoubleOrNull()
                val notes = etNotes.text.toString().trim().ifBlank { null }
                val start = etStart.text.toString().trim()
                val end = etEnd.text.toString().trim()
                val hasSchedule = toggleSchedule.checkedButtonId == R.id.btnBudgetScheduled

                if (category.isBlank() || limit == null) {
                    Toast.makeText(requireContext(), "Please complete all required fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (limit <= 0.0) {
                    showValidationDialog(
                        title = "Invalid Amount",
                        message = "Amount is invalid. Please enter a value greater than 0."
                    )
                    return@setPositiveButton
                }

                if (hasSchedule && (start.isBlank() || end.isBlank())) {
                    Toast.makeText(requireContext(), "Start and end dates are required for scheduled budgets", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val payload = BudgetRequest(
                    category = category,
                    limitAmount = limit,
                    currency = "PHP",
                    startDate = if (hasSchedule) start else null,
                    endDate = if (hasSchedule) end else null,
                    notes = notes
                )

                if (existing == null) {
                    vm.createBudget(payload)
                } else {
                    vm.updateBudget(existing.id, payload)
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

    private fun showLinkedExpensesDialog(budget: BudgetResponse) {
        val linkedExpenses = latestExpenses.filter { it.budgetId == budget.id }

        val dialogView = layoutInflater.inflate(R.layout.dialog_budget_details, null)
        val tvBudgetDialogTitle = dialogView.findViewById<TextView>(R.id.tvBudgetDialogTitle)
        val tvBudgetCategoryChip = dialogView.findViewById<TextView>(R.id.tvBudgetCategoryChip)
        val layoutBudgetNotes = dialogView.findViewById<View>(R.id.layoutBudgetNotes)
        val tvBudgetNotesLabel = dialogView.findViewById<TextView>(R.id.tvBudgetNotesLabel)
        val tvBudgetNotesValue = dialogView.findViewById<TextView>(R.id.tvBudgetNotesValue)
        val listBudgetExpenses = dialogView.findViewById<ListView>(R.id.listBudgetExpenses)
        val tvBudgetExpensesEmpty = dialogView.findViewById<TextView>(R.id.tvBudgetExpensesEmpty)

        val categorySequence = budgetCategorySequenceById[budget.id]
        val displayCategory = if (categorySequence != null) "${budget.category} $categorySequence" else budget.category

        tvBudgetDialogTitle.text = displayCategory
        tvBudgetCategoryChip.text = displayCategory

        if (!budget.notes.isNullOrBlank()) {
            layoutBudgetNotes.visibility = View.VISIBLE
            tvBudgetNotesLabel.visibility = View.VISIBLE
            tvBudgetNotesValue.text = budget.notes
        }

        if (linkedExpenses.isEmpty()) {
            tvBudgetExpensesEmpty.visibility = View.VISIBLE
            listBudgetExpenses.visibility = View.GONE
        } else {
            tvBudgetExpensesEmpty.visibility = View.GONE
            listBudgetExpenses.visibility = View.VISIBLE
            listBudgetExpenses.adapter = LinkedExpenseListAdapter(linkedExpenses)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()

        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
    }

    private class LinkedExpenseListAdapter(
        private val items: List<ExpenseResponse>
    ) : BaseAdapter() {
        override fun getCount(): Int = items.size

        override fun getItem(position: Int): Any = items[position]

        override fun getItemId(position: Int): Long = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_budget_details_expense_row, parent, false)

            val item = items[position]
            val tvTitle = view.findViewById<TextView>(R.id.tvBudgetDetailExpenseTitle)
            val tvDate = view.findViewById<TextView>(R.id.tvBudgetDetailExpenseDate)
            val tvAmount = view.findViewById<TextView>(R.id.tvBudgetDetailExpenseAmount)

            tvTitle.text = item.title.ifBlank { "Expense" }
            tvDate.text = item.expenseDate
            tvAmount.text = CurrencyUi.formatAmount(item.amount, item.currency ?: "PHP")

            return view
        }
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

    private fun confirmDeleteBudget(vm: AppViewModel, budget: BudgetResponse) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Budget")
            .setMessage("Delete budget \"${budget.category}\"? All expenses linked to this budget will also be deleted.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                if (!pendingDeleteBudgetIds.add(budget.id)) {
                    return@setPositiveButton
                }
                rowAdapter.setPendingDeleteIds(pendingDeleteBudgetIds)
                vm.deleteBudget(budget.id) {
                    pendingDeleteBudgetIds.remove(budget.id)
                    if (isAdded) {
                        rowAdapter.setPendingDeleteIds(pendingDeleteBudgetIds)
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

    private class BudgetRowAdapter(
        private val onOpenLinkedExpenses: (BudgetResponse) -> Unit,
        private val onEdit: (BudgetResponse) -> Unit,
        private val onDelete: (BudgetResponse) -> Unit
    ) : BaseAdapter() {
        private var items: List<BudgetResponse> = emptyList()
        private var selectedCurrency: String = "PHP"
        private var exchangeRates: Map<String, Double> = mapOf("PHP" to 1.0)
        private var categorySequenceById: Map<Long, Int> = emptyMap()
        private var pendingDeleteIds: Set<Long> = emptySet()

        fun submit(
            newItems: List<BudgetResponse>,
            selectedCurrency: String,
            exchangeRates: Map<String, Double>,
            categorySequenceById: Map<Long, Int>
        ) {
            items = newItems
            this.selectedCurrency = selectedCurrency
            this.exchangeRates = exchangeRates
            this.categorySequenceById = categorySequenceById
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
                .inflate(R.layout.item_budget_action_row, parent, false)

            val item = items[position]
            val tvCategoryChip = view.findViewById<TextView>(R.id.tvBudgetCategoryChip)
            val tvSpent = view.findViewById<TextView>(R.id.tvBudgetSpent)
            val tvLimit = view.findViewById<TextView>(R.id.tvBudgetLimit)
            val tvStart = view.findViewById<TextView>(R.id.tvBudgetStart)
            val tvEnd = view.findViewById<TextView>(R.id.tvBudgetEnd)
            val progressBudget = view.findViewById<ProgressBar>(R.id.progressBudget)
            val btnEdit = view.findViewById<ImageButton>(R.id.btnEditBudgetRow)
            val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteBudgetRow)
            btnDelete.isLongClickable = false
            btnDelete.setOnLongClickListener { true }
            TooltipCompat.setTooltipText(btnDelete, null)
            btnDelete.contentDescription = null
            btnDelete.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

            val convertedLimit = convertAmount(item.limitAmount, item.currency, selectedCurrency, exchangeRates)
            val convertedSpent = convertAmount(item.spentAmount, item.currency, selectedCurrency, exchangeRates)
            val percent = if (convertedLimit > 0.0) {
                ((convertedSpent / convertedLimit) * 100.0).toInt().coerceIn(0, 100)
            } else {
                0
            }
            val isDeleting = pendingDeleteIds.contains(item.id)

            val categorySequence = categorySequenceById[item.id]
            tvCategoryChip.text = if (categorySequence != null) "${item.category} $categorySequence" else item.category
            tvCategoryChip.background = ContextCompat.getDrawable(view.context, R.drawable.bg_chip_blue)
            tvCategoryChip.setTextColor(ContextCompat.getColor(view.context, R.color.bm_chip_blue_text))
            tvSpent.text = "Spent: ${CurrencyUi.formatAmount(convertedSpent, selectedCurrency)}"
            tvLimit.text = "Budget: ${CurrencyUi.formatAmount(convertedLimit, selectedCurrency)}"
            tvStart.text = "Start: ${item.startDate ?: "-"}"
            tvEnd.text = "End: ${item.endDate ?: "-"}"
            progressBudget.progress = percent

            val progressColor = when {
                percent >= 90 -> R.color.bm_error
                percent >= 70 -> R.color.bm_warning
                else -> R.color.bm_progress_normal
            }
            progressBudget.progressTintList = ContextCompat.getColorStateList(view.context, progressColor)

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
            view.setOnClickListener { onOpenLinkedExpenses(item) }

            return view
        }

        private fun convertAmount(amount: Double, fromCurrency: String?, toCurrency: String, rates: Map<String, Double>): Double {
            val from = (fromCurrency ?: "PHP").uppercase()
            val to = toCurrency.uppercase()
            if (from == to) {
                return amount
            }

            val fromRate = rates[from]
            val toRate = rates[to]
            if (from != "PHP" && fromRate == null) {
                return amount
            }
            if (to != "PHP" && toRate == null) {
                return amount
            }

            val phpAmount = if (from == "PHP") amount else amount / (fromRate ?: 1.0)
            return if (to == "PHP") phpAmount else phpAmount * (toRate ?: 1.0)
        }
    }
}
