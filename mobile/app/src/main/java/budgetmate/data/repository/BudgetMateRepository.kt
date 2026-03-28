package budgetmate.data.repository

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import budgetmate.data.model.AuthResponse
import budgetmate.data.model.BudgetRequest
import budgetmate.data.model.BudgetResponse
import budgetmate.data.model.ExchangeConvertResponse
import budgetmate.data.model.ExchangeRatesResponse
import budgetmate.data.model.ExpenseRequest
import budgetmate.data.model.ExpenseResponse
import budgetmate.data.model.ForgotPasswordRequest
import budgetmate.data.model.LoginRequest
import budgetmate.data.model.RegisterRequest
import budgetmate.data.model.ResetPasswordRequest
import budgetmate.data.model.UserResponse
import budgetmate.data.network.BudgetMateApi
import java.io.IOException
import org.json.JSONObject
import retrofit2.HttpException

class BudgetMateRepository(private val api: BudgetMateApi) {

    private val gson = Gson()

    suspend fun login(email: String, password: String): Result<AuthResponse> = wrap {
        api.login(LoginRequest(email, password))
    }

    suspend fun register(name: String, email: String, password: String): Result<AuthResponse> = wrap {
        api.register(RegisterRequest(name, email, password))
    }

    suspend fun logout(): Result<Unit> = wrap {
        api.logout()
        Unit
    }

    suspend fun me(): Result<UserResponse> = wrap { api.me() }

    suspend fun forgotPassword(email: String): Result<String> = wrap {
        api.forgotPassword(ForgotPasswordRequest(email)).message ?: "Password reset email sent"
    }

    suspend fun resetPassword(token: String, newPassword: String): Result<String> = wrap {
        api.resetPassword(ResetPasswordRequest(token, newPassword)).message ?: "Password has been reset"
    }

    suspend fun getBudgets(page: Int = 0, size: Int = 20): Result<List<BudgetResponse>> = wrap {
        parseList(api.getBudgets(page, size))
    }

    suspend fun createBudget(request: BudgetRequest): Result<BudgetResponse> = wrap { api.createBudget(request) }

    suspend fun updateBudget(id: Long, request: BudgetRequest): Result<BudgetResponse> = wrap {
        api.updateBudget(id, request)
    }

    suspend fun deleteBudget(id: Long): Result<Unit> = wrap {
        api.deleteBudget(id)
        Unit
    }

    suspend fun getExpenses(page: Int = 0, size: Int = 20): Result<List<ExpenseResponse>> = wrap {
        parseList(api.getExpenses(page, size))
    }

    suspend fun createExpense(request: ExpenseRequest): Result<ExpenseResponse> = wrap { api.createExpense(request) }

    suspend fun updateExpense(id: Long, request: ExpenseRequest): Result<ExpenseResponse> = wrap {
        api.updateExpense(id, request)
    }

    suspend fun deleteExpense(id: Long): Result<Unit> = wrap {
        api.deleteExpense(id)
        Unit
    }

    suspend fun getExchangeRates(base: String): Result<ExchangeRatesResponse> = wrap {
        api.getExchangeRates(base)
    }

    suspend fun convertCurrency(amount: Double, from: String, to: String): Result<ExchangeConvertResponse> = wrap {
        api.convertCurrency(amount, from, to)
    }

    suspend fun getUsers(page: Int = 0, size: Int = 20): Result<List<UserResponse>> = wrap {
        parseList(api.getUsers(page, size))
    }

    suspend fun deleteUser(id: Long): Result<Unit> = wrap {
        api.deleteUser(id)
        Unit
    }

    private inline fun <T> wrap(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (error: Throwable) {
            Result.failure(Exception(extractErrorMessage(error)))
        }
    }

    private inline fun <reified T> parseList(response: JsonObject): List<T> {
        val element: JsonElement = if (response.has("content")) {
            response.get("content")
        } else {
            response
        }

        if (!element.isJsonArray) {
            return emptyList()
        }

        val type = object : TypeToken<List<T>>() {}.type
        return gson.fromJson(element, type)
    }

    private fun extractErrorMessage(error: Throwable): String {
        if (error is HttpException) {
            val body = error.response()?.errorBody()?.string()
            if (!body.isNullOrBlank()) {
                try {
                    val json = JSONObject(body)
                    val fieldErrors = json.optJSONObject("fields") ?: json.optJSONObject("errors")
                    if (fieldErrors != null) {
                        val aggregated = mutableListOf<String>()
                        val keys = fieldErrors.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = fieldErrors.opt(key)?.toString().orEmpty().trim()
                            if (value.isNotBlank()) {
                                aggregated.add(value)
                            }
                        }
                        if (aggregated.isNotEmpty()) {
                            return aggregated.joinToString(". ")
                        }
                    }

                    val message = json.optString("message")
                    if (message.isNotBlank()) {
                        return message
                    }
                } catch (_: Exception) {
                    // Fall through to generic handling if body is not valid JSON.
                }
            }
        }

        if (error is IOException) {
            val msg = error.message.orEmpty()
            if (msg.contains("CLEARTEXT", ignoreCase = true)) {
                return "Unable to connect to server. Please try again."
            }
            return "Unable to connect to server. Please check your network and try again."
        }

        return error.message ?: "Request failed. Please try again."
    }
}
