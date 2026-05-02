package budgetmate.data.model

import com.google.gson.annotations.SerializedName

data class ApiMessage(
    val message: String? = null
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)

data class ForgotPasswordRequest(
    val email: String
)

data class ResetPasswordRequest(
    val token: String,
    val newPassword: String
)

data class UpdateProfileNameRequest(
    val name: String
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

data class AuthResponse(
    @SerializedName("accessToken")
    val accessToken: String,
    val tokenType: String = "Bearer",
    val userId: Long,
    val name: String,
    val email: String,
    val role: String
)

data class UserResponse(
    val id: Long,
    val name: String,
    val email: String,
    val imageUrl: String? = null,
    val emailVerified: Boolean = false,
    val roles: Set<String> = emptySet(),
    val status: String? = null,
    val lastLoginAt: String? = null,
    val provider: String? = null,
    val createdAt: String? = null
)

data class BudgetRequest(
    val category: String,
    val limitAmount: Double,
    val currency: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val notes: String? = null
)

data class BudgetResponse(
    val id: Long,
    val category: String,
    val limitAmount: Double,
    val spentAmount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val currency: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val notes: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class ExpenseRequest(
    val title: String,
    val description: String? = null,
    val amount: Double,
    val currency: String,
    val category: String,
    val expenseDate: String,
    val isRecurring: Boolean = false,
    val budgetId: Long? = null
)

data class ExpenseResponse(
    val id: Long,
    val userId: Long,
    val title: String,
    val description: String? = null,
    val amount: Double,
    val currency: String,
    val category: String,
    val expenseDate: String,
    val isRecurring: Boolean = false,
    val budgetId: Long? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class ExchangeRatesResponse(
    @SerializedName("base_code")
    val baseCode: String? = null,
    @SerializedName("conversion_rates")
    val conversionRates: Map<String, Double> = emptyMap()
)

data class ExchangeConvertResponse(
    val result: String? = null,
    val baseCode: String? = null,
    val targetCode: String? = null,
    val conversionRate: Double? = null,
    val conversionResult: Double? = null
)

data class DashboardNotification(
    val type: String,
    val userId: Long,
    val timestamp: String
)

data class ProfileImagePayload(
    val bytes: ByteArray,
    val contentType: String?
)
