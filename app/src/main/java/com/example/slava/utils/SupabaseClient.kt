package com.example.slava.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slava.exceptions.translateError
import com.example.slava.models.Answer
import com.example.slava.models.Challenge
import com.example.slava.models.Question
import com.example.slava.models.QuestionWithAnswers
import com.example.slava.models.Rating
import com.example.slava.models.RatingItem
import com.example.slava.models.RatingWithDetails
import com.example.slava.models.User
import com.example.slava.models.UserChallenge
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class SupabaseClient : ViewModel() {

    private val _ratingItems = MutableStateFlow<List<RatingItem>>(emptyList())
    val ratingItems: StateFlow<List<RatingItem>> get() = _ratingItems

    // Инициализация Supabase клиента с конфигурацией
    private val supabase = createSupabaseClient(
        supabaseUrl = "https://ycybrdkzpztnwhobwwrg.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InljeWJyZGt6cHp0bndob2J3d3JnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MzI3NzA2MzEsImV4cCI6MjA0ODM0NjYzMX0.EeDaj5f8z8q-jYphPDN2fd5QSDXTsS_E_qc__85-EPs"
    ) {
        install(Auth)     
        install(Postgrest)
        install(Storage)   
        defaultSerializer = KotlinXSerializer(Json) // Настройка сериализатора
    }

    // Функция авторизации пользователя
    suspend fun login(
        context: Context,
        userEmail: String,
        userPassword: String
    ): Result<Boolean> {
        return try {
            // Попытка входа через email и пароль
            supabase.auth.signInWith(Email) {
                email = userEmail
                password = userPassword
            }
            saveToken(context) // Сохранение токена после успешной авторизации
            Result.success(true) // Возвращаем успешный результат
        } catch (e: AuthRestException) {
            // Обработка ошибок аутентификации с переводом кода ошибки
            val errorMessage = translateError(e.errorCode?.value, e.message)
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            // Обработка любых других исключений
            Result.failure(Exception("Неизвестная ошибка: ${e.message}"))
        }
    }
}

    // Регистрация
    suspend fun signup(
        context: Context,
        userEmail: String,
        userPassword: String,
        userFullName: String,
        userPhone: String,
        userDate: String
    ): Result<Boolean> {
        return try {
            supabase.auth.signUpWith(Email) {
                email = userEmail
                password = userPassword
            }
            val user = User(
                uid = supabase.auth.currentUserOrNull()!!.id,
                name = userFullName,
                phone = userPhone,
                date_of_birth = userDate,
                role = "Пользователь"
            )
            supabase.from("users").insert(user)
            saveToken(context)
            Result.success(true)
        } catch (e: AuthRestException) {
            val errorMessage = translateError(e.errorCode?.value, e.message)
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            Result.failure(Exception("Неизвестная ошибка: ${e.message}"))
        }
    }

    // Отправка кода на почту
    suspend fun sendEmailOtp(
        userEmail: String
    ): Result<Boolean> {
        return try {
            supabase.auth.resetPasswordForEmail(userEmail)
            Result.success(true)
        } catch (e: AuthRestException) {
            val errorMessage = translateError(e.errorCode?.value, e.message)
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            Result.failure(Exception("Неизвестная ошибка: ${e.message}"))
        }
    }

    // Проверка кода
    suspend fun checkOtp(
        code: String,
        userEmail: String
    ): Result<Boolean> {
        return try {
            supabase.auth.verifyEmailOtp(
                type = OtpType.Email.RECOVERY,
                email = userEmail,
                token = code
            )
            Result.success(true)
        } catch (e: AuthRestException) {
            val errorMessage = translateError(e.errorCode?.value, e.message)
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            Result.failure(Exception("Неизвестная ошибка: ${e.message}"))
        }
    }

    // Обновление пароля
    suspend fun updatePassword(userPassword: String): Result<Boolean> {
        return try {
            supabase.auth.updateUser {
                password = userPassword
            }
            Result.success(true)
        } catch (e: AuthRestException) {
            val errorMessage = translateError(e.errorCode?.value, e.message)
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            Result.failure(Exception("Неизвестная ошибка: ${e.message}"))
        }
    }

    // Сохранение токена
    private fun saveToken(context: Context) {
        val accessToken = supabase.auth.currentUserOrNull()?.id
        val sharedPref = SharedPreferenceHelper(context)
        sharedPref.saveStringData("accessToken", accessToken)
    }

    // Получение токена
    fun getToken(context: Context): String? {
        val sharedPref = SharedPreferenceHelper(context)
        return sharedPref.getStringData("accessToken")
    }

    suspend fun getUserSession(jwtToken: String) {
        val session = supabase.auth.currentSessionOrNull()
        Log.e("session", session.toString())
        supabase.auth.retrieveUser(jwtToken)
        supabase.auth.refreshCurrentSession()
    }

    // Получение пользователя
    suspend fun getUserById(userId: String): Result<User> {
        return try {
            val response = supabase.from("users")
                .select {
                    filter {
                        eq("uid", userId)
                    }
                }
                .decodeSingle<User>()

            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Получение всех челленджей
    suspend fun fetchAllChallenges(challengeId: Int? = null): Result<List<Challenge>> {
        return try {
            val columns = Columns.raw("""
                id_challenge,
                category (
                    id_category,
                    name
                ),
                name,
                description,
                tasks,
                reward,
                challenge_start_date,
                challenge_end_date
            """.trimIndent())
            var response: List<Challenge> = if (challengeId != null) {
                supabase.from("challenge").select(columns) {
                    filter {
                        eq("id_challenge", challengeId)
                    }
                }.decodeList<Challenge>()
            } else {
                supabase.from("challenge").select(columns).decodeList<Challenge>()
            }

            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Получение деталей челленджа
    suspend fun fetchChallenge(challengeId: Int): Result<Challenge> {
        return try {
            val columns = Columns.raw("""
                id_challenge,
                category (
                    id_category,
                    name
                ),
                name,
                description,
                tasks,
                reward,
                challenge_start_date,
                challenge_end_date
            """.trimIndent())
            val response = supabase.from("challenge").select(columns) {
                filter {
                    eq("id_challenge", challengeId)
                }
            }.decodeSingle<Challenge>()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Получение активных пользовательских челленджей
    suspend fun fetchAllUserAcceptedChallenges(userId: Int): Result<List<UserChallenge>> {
        return try {
            val columns = Columns.raw("""
                id_user_challenge,
                users (
                    id_user,
                    uid,
                    name,
                    phone,
                    date_of_birth,
                    password,
                    role
                ),
                challenge (
                    id_challenge,
                    category (
                        id_category,
                        name
                    ),
                    name,
                    description,
                    tasks,
                    reward,
                    challenge_start_date,
                    challenge_end_date
                ),
                user_start_date,
                step,
                progress
            """.trimIndent())
            val response: List<UserChallenge> = supabase.from("user_challenge").select(columns) {
                filter {
                    eq("id_user", userId)
                }
            }.decodeList<UserChallenge>()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Получение деталей активного пользовательского челленджа
    suspend fun fetchUserAcceptedChallenge(challengeId: Int, userId: Int): Result<UserChallenge> {
        return try {
            val columns = Columns.raw("""
                id_user_challenge,
                users (
                    id_user,
                    uid,
                    name,
                    phone,
                    date_of_birth,
                    password,
                    role
                ),
                challenge (
                    id_challenge,
                    category (
                        id_category,
                        name
                    ),
                    name,
                    description,
                    tasks,
                    reward,
                    challenge_start_date,
                    challenge_end_date
                ),
                user_start_date,
                step,
                progress
            """.trimIndent())
            val response = supabase.from("user_challenge").select(columns) {
                filter {
                    eq("id_user", userId)
                    eq("id_challenge", challengeId)
                }
            }.decodeSingle<UserChallenge>()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Принятие челленджа
    suspend fun insertUserChallenge(userId: Int, challengeId: Int, step: String, progress: Int): Result<Boolean> {
        return try {
            val data = UserChallenge(
                id_user = userId,
                id_challenge = challengeId,
                step = step,
                progress = progress,
            )
            supabase.from("user_challenge").insert(data)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Загрузка аватарки пользователя на сервер
    suspend fun uploadUserAvatar(imageUri: Uri, uid: String): Result<Boolean> {
        return try {
            val bucket = supabase.storage.from("UsersPhoto")
            bucket.update("${uid}.jpg", imageUri)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Загрузка аватарки пользователя с сервера
    suspend fun downloadUserAvatar(uid: String): Result<ByteArray> {
        return try {
            val bucket = supabase.storage.from("UsersPhoto")
            val bytes = bucket.downloadAuthenticated("${uid}.jpg")
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Обновление данных пользователя
    suspend fun updateUser(userId: Int, name: String, phone: String, date: String, userPassword: String? = null): Result<Boolean> {
        return try {
            supabase.from("users").update({
                set("name", name)
                set("phone", phone)
                set("date_of_birth", date)
            }) {
                filter {
                    eq("id_user", userId)
                }
            }
            if (!userPassword.isNullOrEmpty()) {
                supabase.auth.updateUser {
                    password = userPassword
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Получение кол-ва пользователей, которые приняли челлендж
    suspend fun getChallengeUsers(challengeId: Int) : Result<Long> {
        return try {
            val count = supabase.from("user_challenge")
                .select {
                    count(Count.EXACT)
                    filter {
                        eq("id_challenge", challengeId)
                    }
                }.countOrNull()!!
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    fun fetchRating(challengeId: Int) {
        viewModelScope.launch {
            try {
                val columns = Columns.raw("""
                id,
                id_challenge,
                score,
                users(
                    id,
                    uid,
                    name,
                    phone,
                    date_of_birth
                )
            """.trimIndent())
                val response = supabase.from("Rating").select(columns) {
                    order("score", Order.DESCENDING)
                    filter {
                        eq("id_challenge", challengeId)
                    }
                }.decodeList<RatingWithDetails>()

                // Преобразуем в список RatingItem
                _ratingItems.value = response.map { rating ->
                    RatingItem(
                        name = rating.user.name,
                        score = rating.score
                    )
                }
            } catch (e: Exception) {
                Log.e("123", e.message.toString())
            }
        }
    }

    suspend fun getTotalScore(userId: Int?) : Int {
        return try {
            val data = Columns.raw("""
                id_challenge,
                score,
                id_user
            """.trimIndent())
            val response = supabase.from("Rating")
                .select(data) {
                    filter { eq("id_user", userId.toString()) }
                }
                .decodeList<Rating>()

            // Суммируем все очки
            response.sumOf { it.score }
        } catch (e: Exception) {
            Log.e("123", e.message.toString())
            0
        }
    }

    suspend fun fetchQuestionsByTestId(testId: Int): List<QuestionWithAnswers> {
        try {
            val questions = supabase.from("Questions")
                .select {
                    filter { eq("id_test", testId) }
                }
                .decodeList<Question>()

            val questionWithAnswers = questions.map { question ->
                val answers = supabase.from("Answers")
                    .select {
                        filter { eq("id_questions", question.id) }
                    }
                    .decodeList<Answer>()
                QuestionWithAnswers(question.id, question.text, answers)
            }

            return questionWithAnswers
        } catch (e : Exception) {
            Log.e("123", e.message.toString())
        }
        return emptyList()
    }

    suspend fun saveTestResult(userId: Int, challengeId: Int, score: Int) {
        try {
            val data = Rating(id_challenge =  challengeId, score = score, id_user = userId)
            supabase.from("Rating").insert(data)
        } catch (e: Exception) {
            Log.e("SupabaseClient", "Ошибка сохранения результата: ${e.message}")
            throw e
        }
    }

    suspend fun getUserResults(userId: Int, challengeId: Int) : Int{
        return try {
            val response = supabase.from("Rating")
                .select {
                    filter {
                        eq("id_user", userId)
                        eq("id_challenge", challengeId)
                    }
                }
                .decodeSingle<Rating>()
            response.score
        } catch (e: Exception) {
            Log.e("123", e.message.toString())
            0
        }
    }

    suspend fun updateProgress(progress: Int, challengeId: Int, userId: Int, date: String) {
        try {
            supabase.from("Progress").update({
                set("progress", progress)
                set("date", date)
            }) {
                filter {
                    eq("id_challenge", challengeId)
                    eq("id_user", userId)
                }
            }
        } catch (e: Exception) {
            Log.e("123", e.message.toString())
        }
    }
}
