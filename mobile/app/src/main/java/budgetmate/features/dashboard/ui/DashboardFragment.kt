package edu.cit.hopista.budgetmate.features.dashboard.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import edu.cit.hopista.budgetmate.MainActivity
import edu.cit.hopista.budgetmate.R
import edu.cit.hopista.budgetmate.shared.data.model.BudgetResponse
import edu.cit.hopista.budgetmate.shared.data.model.ExpenseResponse
import edu.cit.hopista.budgetmate.shared.ui.CurrencyUi
import edu.cit.hopista.budgetmate.shared.viewmodel.AppUiState
import kotlinx.coroutines.launch
import java.util.Calendar

class DashboardFragment : Fragment() {

    private enum class SummaryPeriod {
        MONTH,
        YEAR
    }

    private enum class SummaryMetric {
        BUDGET,
        SPENT
    }

    private lateinit var tvWelcome: TextView
    private lateinit var tvSummaryBudgetLabel: TextView
    private lateinit var tvSummaryExpenseLabel: TextView
    private lateinit var tvSummaryBudget: TextView
    private lateinit var tvSummaryExpense: TextView
    private lateinit var tvSummaryActiveBudgets: TextView
    private lateinit var cardSummaryBudget: MaterialCardView
    private lateinit var cardSummaryExpense: MaterialCardView
    private lateinit var listRecentExpenses: ListView
    private lateinit var listBudgetStatus: ListView
    private val recentExpensesAdapter by lazy { RecentExpenseAdapter() }
    private val budgetStatusAdapter by lazy { BudgetStatusAdapter() }
    private var budgetSummaryPeriod: SummaryPeriod = SummaryPeriod.MONTH
    private var spentSummaryPeriod: SummaryPeriod = SummaryPeriod.MONTH
    private var latestUiState: AppUiState? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vm = (requireActivity() as MainActivity).appViewModel

        tvWelcome = view.findViewById(R.id.tvWelcome)
        tvSummaryBudgetLabel = view.findViewById(R.id.tvSummaryBudgetLabel)
        tvSummaryExpenseLabel = view.findViewById(R.id.tvSummaryExpenseLabel)
        tvSummaryBudget = view.findViewById(R.id.tvSummaryBudget)
        tvSummaryExpense = view.findViewById(R.id.tvSummaryExpense)
        tvSummaryActiveBudgets = view.findViewById(R.id.tvSummaryActiveBudgets)
        cardSummaryBudget = view.findViewById(R.id.cardSummaryBudget)
        cardSummaryExpense = view.findViewById(R.id.cardSummaryExpense)
        listRecentExpenses = view.findViewById(R.id.listRecentExpenses)
        listBudgetStatus = view.findViewById(R.id.listBudgetStatus)

        listRecentExpenses.adapter = recentExpensesAdapter
        listBudgetStatus.adapter = budgetStatusAdapter
        updateSummaryLabels()

        cardSummaryBudget.setOnClickListener { showSummaryPeriodPicker(SummaryMetric.BUDGET) }
        cardSummaryExpense.setOnClickListener { showSummaryPeriodPicker(SummaryMetric.SPENT) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.uiState.collect { state ->
                    latestUiState = state
                    renderDashboard(state)
                }
            }
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

    private fun showSummaryPeriodPicker(metric: SummaryMetric) {
        val options = arrayOf("Total Month", "Total Year")
        val currentPeriod = if (metric == SummaryMetric.BUDGET) budgetSummaryPeriod else spentSummaryPeriod
        val checkedItem = if (currentPeriod == SummaryPeriod.MONTH) 0 else 1
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (metric == SummaryMetric.BUDGET) "Total Budget Period" else "Total Spent Period")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val selected = if (which == 0) SummaryPeriod.MONTH else SummaryPeriod.YEAR
                if (metric == SummaryMetric.BUDGET) {
                    budgetSummaryPeriod = selected
                } else {
                    spentSummaryPeriod = selected
                }
                updateSummaryLabels()
                latestUiState?.let { renderDashboard(it) }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()

        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
    }

    private fun updateSummaryLabels() {
        val budgetLabel = if (budgetSummaryPeriod == SummaryPeriod.MONTH) "Month" else "Year"
        val spentLabel = if (spentSummaryPeriod == SummaryPeriod.MONTH) "Month" else "Year"
        tvSummaryBudgetLabel.text = "Total Budget ($budgetLabel)"
        tvSummaryExpenseLabel.text = "Total Spent ($spentLabel)"
    }

    private fun renderDashboard(state: AppUiState) {
        val budgetCategorySequenceById = buildCategorySequenceByBudgetId(state.budgets)
        val filteredBudgets = state.budgets.filter { matchesBudgetPeriod(it, budgetSummaryPeriod) }
        val filteredExpenses = state.expenses.filter { matchesExpensePeriod(it, spentSummaryPeriod) }
        val totalBudget = filteredBudgets.sumOf {
            it.limitAmount
        }
        val totalExpense = filteredExpenses.sumOf {
            it.amount
        }
        tvWelcome.text = "Welcome back, ${state.user?.name ?: "User"}"
        tvSummaryBudget.text = CurrencyUi.formatAmount(totalBudget, state.selectedCurrency)
        tvSummaryExpense.text = CurrencyUi.formatAmount(totalExpense, state.selectedCurrency)
        tvSummaryActiveBudgets.text = state.budgets.size.toString()

        recentExpensesAdapter.submit(state.expenses.take(10), state.selectedCurrency, state)
        // Always recompute spentAmount for each budget from expenses, matching Budgets page logic
        val budgetsWithSpent = state.budgets.map { budget ->
            val spent = state.expenses
                .filter { it.budgetId == budget.id }
                .sumOf { it.amount }
            budget.copy(spentAmount = spent)
        }
        budgetStatusAdapter.submit(budgetsWithSpent, state.selectedCurrency, state, budgetCategorySequenceById)
    }

    private fun matchesBudgetPeriod(budget: BudgetResponse, period: SummaryPeriod): Boolean {
        val referenceDate = budget.startDate ?: budget.createdAt ?: budget.updatedAt
        return isInSelectedPeriod(referenceDate, period)
    }

    private fun matchesExpensePeriod(expense: ExpenseResponse, period: SummaryPeriod): Boolean {
        val referenceDate = expense.expenseDate.ifBlank { expense.createdAt ?: expense.updatedAt.orEmpty() }
        return isInSelectedPeriod(referenceDate, period)
    }

    private fun isInSelectedPeriod(rawDate: String?, period: SummaryPeriod): Boolean {
        val (year, month) = extractYearMonth(rawDate) ?: return false
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        if (period == SummaryPeriod.YEAR) {
            return year == currentYear
        }

        val currentMonth = now.get(Calendar.MONTH) + 1
        return year == currentYear && month == currentMonth
    }

    private fun extractYearMonth(rawDate: String?): Pair<Int, Int>? {
        if (rawDate.isNullOrBlank()) {
            return null
        }

        val normalized = rawDate.trim().replace('/', '-')
        val parts = normalized.split('-')
        if (parts.size < 2) {
            return null
        }

        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].take(2).toIntOrNull() ?: return null
        if (month !in 1..12) {
            return null
        }
        return year to month
    }


    private inner class RecentExpenseAdapter : BaseAdapter() {
        private var items: List<ExpenseResponse> = emptyList()
        private var selectedCurrency: String = "PHP"
        private var uiState: AppUiState? = null

        fun submit(items: List<ExpenseResponse>, selectedCurrency: String, uiState: AppUiState) {
            this.items = items
            this.selectedCurrency = selectedCurrency
            this.uiState = uiState
            notifyDataSetChanged()
        }

        override fun getCount(): Int = if (items.isEmpty()) 1 else items.size

        override fun getItem(position: Int): Any = if (items.isEmpty()) "No expenses" else items[position]

        override fun getItemId(position: Int): Long = if (items.isEmpty()) -1 else items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_dashboard_recent_row, parent, false)

            val tvTitle = view.findViewById<TextView>(R.id.tvRecentTitle)
            val tvAmount = view.findViewById<TextView>(R.id.tvRecentAmount)
            val tvCategory = view.findViewById<TextView>(R.id.tvRecentCategory)
            val tvDate = view.findViewById<TextView>(R.id.tvRecentDate)

            if (items.isEmpty()) {
                tvTitle.text = "No expenses"
                tvAmount.text = ""
                tvCategory.visibility = View.GONE
                tvDate.visibility = View.GONE
                return view
            }

            val state = uiState ?: return view
            val item = items[position]
            val converted = item.amount

            tvTitle.text = item.title
            tvAmount.text = CurrencyUi.formatAmount(converted, selectedCurrency)
            tvCategory.text = item.category
            tvCategory.visibility = View.VISIBLE
            tvCategory.background = ContextCompat.getDrawable(view.context, R.drawable.bg_chip_blue)
            tvCategory.setTextColor(ContextCompat.getColor(view.context, R.color.bm_chip_blue_text))
            tvDate.text = item.expenseDate
            tvDate.visibility = View.VISIBLE
            tvDate.setTextColor(ContextCompat.getColor(view.context, R.color.bm_text_secondary))
            return view
        }
    }

    private inner class BudgetStatusAdapter : BaseAdapter() {
        private var items: List<BudgetResponse> = emptyList()
        private var selectedCurrency: String = "PHP"
        private var uiState: AppUiState? = null
        private var categorySequenceById: Map<Long, Int> = emptyMap()

        fun submit(
            items: List<BudgetResponse>,
            selectedCurrency: String,
            uiState: AppUiState,
            categorySequenceById: Map<Long, Int>
        ) {
            this.items = items
            this.selectedCurrency = selectedCurrency
            this.uiState = uiState
            this.categorySequenceById = categorySequenceById
            notifyDataSetChanged()
        }

        override fun getCount(): Int = if (items.isEmpty()) 1 else items.size

        override fun getItem(position: Int): Any = if (items.isEmpty()) "No budgets" else items[position]

        override fun getItemId(position: Int): Long = if (items.isEmpty()) -1 else items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_dashboard_budget_row, parent, false)

            val tvCategory = view.findViewById<TextView>(R.id.tvBudgetRowCategory)
            val tvValues = view.findViewById<TextView>(R.id.tvBudgetRowValues)
            val progressBudget = view.findViewById<ProgressBar>(R.id.progressBudgetRow)

            if (items.isEmpty()) {
                tvCategory.text = "No budgets"
                tvCategory.background = null
                tvValues.text = ""
                progressBudget.visibility = View.GONE
                return view
            }

            val item = items[position]
            val convertedLimit = item.limitAmount
            val convertedSpent = item.spentAmount
            val percent = if (convertedLimit > 0.0) {
                ((convertedSpent / convertedLimit) * 100.0).toInt().coerceIn(0, 100)
            } else {
                0
            }

            val categorySequence = categorySequenceById[item.id]
            tvCategory.text = if (categorySequence != null) "${item.category} $categorySequence" else item.category
            tvCategory.background = ContextCompat.getDrawable(view.context, R.drawable.bg_chip_blue)
            tvCategory.setTextColor(ContextCompat.getColor(view.context, R.color.bm_chip_blue_text))
            tvValues.text = "Spent: ${CurrencyUi.formatAmount(convertedSpent, selectedCurrency)}  Budget: ${CurrencyUi.formatAmount(convertedLimit, selectedCurrency)}"
            progressBudget.visibility = View.VISIBLE
            progressBudget.progress = percent
            val progressColor = when {
                percent >= 90 -> R.color.bm_error
                percent >= 70 -> R.color.bm_warning
                else -> R.color.bm_progress_normal
            }
            progressBudget.progressTintList = ContextCompat.getColorStateList(view.context, progressColor)
            return view
        }
    }
}
