package edu.cit.hopista.budgetmate.shared.data.repository

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import edu.cit.hopista.budgetmate.shared.data.model.AuthResponse
import edu.cit.hopista.budgetmate.shared.data.model.BudgetRequest
import edu.cit.hopista.budgetmate.shared.data.model.BudgetResponse
import edu.cit.hopista.budgetmate.shared.data.model.ChangePasswordRequest
import edu.cit.hopista.budgetmate.shared.data.model.ExpenseRequest
import edu.cit.hopista.budgetmate.shared.data.model.ExpenseResponse
import edu.cit.hopista.budgetmate.shared.data.model.ForgotPasswordRequest
import edu.cit.hopista.budgetmate.shared.data.model.LoginRequest
import edu.cit.hopista.budgetmate.shared.data.model.ProfileImagePayload
import edu.cit.hopista.budgetmate.shared.data.model.RegisterRequest
import edu.cit.hopista.budgetmate.shared.data.model.ResetPasswordRequest
import edu.cit.hopista.budgetmate.shared.data.model.UpdateProfileNameRequest
import edu.cit.hopista.budgetmate.shared.data.model.UserResponse
import edu.cit.hopista.budgetmate.shared.data.network.BudgetMateApi
import java.io.IOException
import org.json.JSONObject
import okhttp3.MultipartBody
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

    suspend fun updateMyName(name: String): Result<UserResponse> = wrap {
        api.updateMyName(UpdateProfileNameRequest(name))
    }

    suspend fun changeMyPassword(currentPassword: String, newPassword: String): Result<Unit> = wrap {
        val response = api.changeMyPassword(ChangePasswordRequest(currentPassword, newPassword))
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
        Unit
    }

    suspend fun uploadMyProfileImage(part: MultipartBody.Part): Result<UserResponse> = wrap {
        api.uploadMyProfileImage(part)
    }

    suspend fun downloadMyProfileImage(): Result<ProfileImagePayload> = wrap {
        val response = api.downloadMyProfileImage(System.currentTimeMillis())
        if (!response.isSuccessful) {
            throw HttpException(response)
        }

        val body = response.body() ?: throw IOException("Profile image is empty")
        val contentType = response.headers()["Content-Type"] ?: body.contentType()?.toString()
        ProfileImagePayload(body.bytes(), contentType)
    }

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
        val response = api.deleteBudget(id)
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
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
        val response = api.deleteExpense(id)
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
        Unit
    }

    suspend fun getUsers(page: Int = 0, size: Int = 20): Result<List<UserResponse>> = wrap {
        parseList(api.getUsers(page, size))
    }

    suspend fun deleteUser(id: Long): Result<Unit> = wrap {
        val response = api.deleteUser(id)
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
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
                            val value = normalizeMessage(fieldErrors.opt(key)?.toString())
                            if (value != null) {
                                aggregated.add(value)
                            }
                        }
                        if (aggregated.isNotEmpty()) {
                            return aggregated.joinToString(". ")
                        }
                    }

                    val message = normalizeMessage(json.optString("message"))
                    if (message != null) {
                        return message
                    }

                    val errorText = normalizeMessage(json.optString("error"))
                    if (errorText != null) {
                        return errorText
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

        return normalizeMessage(error.message) ?: "Request failed. Please try again."
    }

    private fun normalizeMessage(raw: String?): String? {
        val value = raw.orEmpty().trim()
        if (value.isBlank()) {
            return null
        }
        if (value.equals("null", ignoreCase = true)) {
            return null
        }
        if (value.equals("undefined", ignoreCase = true)) {
            return null
        }
        return value
    }
}
