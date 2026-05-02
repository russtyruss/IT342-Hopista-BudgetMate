package budgetmate.data.network

import com.google.gson.JsonObject
import budgetmate.data.model.ApiMessage
import budgetmate.data.model.AuthResponse
import budgetmate.data.model.BudgetRequest
import budgetmate.data.model.BudgetResponse
import budgetmate.data.model.ChangePasswordRequest
import budgetmate.data.model.ExchangeConvertResponse
import budgetmate.data.model.ExchangeRatesResponse
import budgetmate.data.model.ExpenseRequest
import budgetmate.data.model.ExpenseResponse
import budgetmate.data.model.ForgotPasswordRequest
import budgetmate.data.model.LoginRequest
import budgetmate.data.model.RegisterRequest
import budgetmate.data.model.ResetPasswordRequest
import budgetmate.data.model.UpdateProfileNameRequest
import budgetmate.data.model.UserResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface BudgetMateApi {

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("auth/logout")
    suspend fun logout(): ApiMessage

    @GET("auth/me")
    suspend fun me(): UserResponse

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): ApiMessage

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): ApiMessage

    @GET("budgets")
    suspend fun getBudgets(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): JsonObject

    @GET("budgets/{id}")
    suspend fun getBudgetById(@Path("id") id: Long): BudgetResponse

    @POST("budgets")
    suspend fun createBudget(@Body request: BudgetRequest): BudgetResponse

    @PUT("budgets/{id}")
    suspend fun updateBudget(@Path("id") id: Long, @Body request: BudgetRequest): BudgetResponse

    @DELETE("budgets/{id}")
    suspend fun deleteBudget(@Path("id") id: Long): Response<Unit>

    @GET("expenses")
    suspend fun getExpenses(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String = "expenseDate,desc"
    ): JsonObject

    @GET("expenses/{id}")
    suspend fun getExpenseById(@Path("id") id: Long): ExpenseResponse

    @POST("expenses")
    suspend fun createExpense(@Body request: ExpenseRequest): ExpenseResponse

    @PUT("expenses/{id}")
    suspend fun updateExpense(@Path("id") id: Long, @Body request: ExpenseRequest): ExpenseResponse

    @DELETE("expenses/{id}")
    suspend fun deleteExpense(@Path("id") id: Long): Response<Unit>

    @GET("exchange-rates")
    suspend fun getExchangeRates(@Query("base") base: String = "PHP"): ExchangeRatesResponse

    @GET("exchange-rates/convert")
    suspend fun convertCurrency(
        @Query("amount") amount: Double,
        @Query("from") from: String,
        @Query("to") to: String
    ): ExchangeConvertResponse

    @GET("users")
    suspend fun getUsers(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): JsonObject

    @GET("users/me")
    suspend fun getCurrentUser(): UserResponse

    @PUT("users/me/name")
    suspend fun updateMyName(@Body request: UpdateProfileNameRequest): UserResponse

    @PUT("users/me/password")
    suspend fun changeMyPassword(@Body request: ChangePasswordRequest): Response<Unit>

    @Multipart
    @POST("users/me/profile-image")
    suspend fun uploadMyProfileImage(@Part file: MultipartBody.Part): UserResponse

    @GET("users/me/profile-image")
    suspend fun downloadMyProfileImage(@Query("t") cacheKey: Long = System.currentTimeMillis()): Response<ResponseBody>

    @DELETE("users/{id}")
    suspend fun deleteUser(@Path("id") id: Long): Response<Unit>
}
