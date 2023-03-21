package api

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import data.EmailCodeRepository
import data.User
import data.UserRepository
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jakarta.mail.internet.AddressException
import jakarta.mail.internet.InternetAddress
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import util.Email
import util.PBKDF2
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@Serializable
@Resource("/auth")
private class Auth {
    @Serializable
    @Resource("/sign-in")
    class SignIn(val parent: Auth = Auth())

    @Serializable
    @Resource("/sign-up")
    class SignUp(val parent: Auth = Auth())

    @Serializable
    @Resource("/verify-email")
    class VerifyEmail(
        val parent: Auth = Auth(),
        val email: String,
    )

    @Serializable
    @Resource("/reset-password")
    class ResetPassword(val parent: Auth = Auth())
}

fun Route.routeAuth() {
    val service by inject<AuthService>()

    post<Auth.SignIn> {
        val body = call.receive<AuthService.SignInBody>()
        val result = service.signIn(body)
        call.respondResult(result)
    }

    post<Auth.SignUp> {
        val body = call.receive<AuthService.SignUpBody>()
        val result = service.signUp(body)
        call.respondResult(result)
    }

    post<Auth.VerifyEmail> { loc ->
        val result = service.verifyEmail(loc.email)
        call.respondResult(result)
    }

    post<Auth.ResetPassword> {
        val result = service.resetPassword()
        call.respondResult(result)
    }
}

class AuthService(
    private val secret: String,
    private val userRepository: UserRepository,
    private val emailCodeRepository: EmailCodeRepository,
) {
    private fun generateToken(username: String): Pair<String, Long> {
        val expiresAt = LocalDateTime.now()
            .plusMonths(6)
            .atZone(ZoneId.systemDefault())
        return Pair(
            JWT.create()
                .withClaim("username", username)
                .withExpiresAt(Date.from(expiresAt.toInstant()))
                .sign(Algorithm.HMAC256(secret)),
            expiresAt.toEpochSecond(),
        )
    }

    @Serializable
    data class SignInBody(
        val emailOrUsername: String,
        val password: String,
    )

    @Serializable
    data class SignInDto(
        val email: String,
        val username: String,
        val token: String,
        val expiresAt: Long,
    )

    suspend fun signIn(body: SignInBody): Result<SignInDto> {
        val user = userRepository.getByEmail(body.emailOrUsername)
            ?: userRepository.getByUsername(body.emailOrUsername)
            ?: return httpNotFound("用户不存在")

        if (user.password != PBKDF2.hash(body.password, user.salt))
            return httpUnauthorized("密码错误")

        val (token, expiresAt) = generateToken(user.username)
        return Result.success(
            SignInDto(
                email = user.email,
                username = user.username,
                token = token,
                expiresAt = expiresAt,
            )
        )

    }

    @Serializable
    data class SignUpBody(
        val email: String,
        val emailCode: String,
        val username: String,
        val password: String,
    )

    suspend fun signUp(body: SignUpBody): Result<SignInDto> {
        if (body.username.length < 3) {
            return httpBadRequest("用户名至少为3个字符")
        }
        if (body.username.length > 15) {
            return httpBadRequest("用户名至多为15个字符")
        }
        if (body.password.length < 8) {
            return httpBadRequest("密码至少为8个字符")
        }
        userRepository.getByEmail(body.email)?.let {
            return httpConflict("邮箱已经被使用")
        }
        userRepository.getByUsername(body.username)?.let {
            return httpConflict("用户名已经被使用")
        }

        if (!emailCodeRepository.exist(body.email, body.emailCode))
            return httpBadRequest("邮箱验证码错误")

        val salt = PBKDF2.randomSalt()
        val password = PBKDF2.hash(body.password, salt)
        userRepository.add(
            User(
                email = body.email,
                username = body.username,
                salt = salt,
                password = password,
                role = User.Role.Normal,
                createdAt = LocalDateTime.now(),
            )
        )

        val (token, expiresAt) = generateToken(body.username)
        return Result.success(
            SignInDto(
                email = body.email,
                username = body.username,
                token = token,
                expiresAt = expiresAt,
            )
        )
    }

    suspend fun verifyEmail(email: String): Result<String> {
        userRepository.getByEmail(email)?.let {
            return httpConflict("邮箱已经被使用")
        }

        try {
            InternetAddress(email).apply { validate() }
        } catch (e: AddressException) {
            return httpBadRequest("邮箱不合法")
        }

        val emailCode = String.format("%06d", Random().nextInt(999999))

        try {
            Email.send(
                to = email,
                subject = "$emailCode 日本网文机翻机器人 注册激活码",
                text = "您的注册激活码为 $emailCode\n" +
                        "激活码将会在五分钟后失效,请尽快完成注册\n" +
                        "这是系统邮件，请勿回复"
            )
        } catch (e: AddressException) {
            return httpInternalServerError("邮件发送失败")
        }

        emailCodeRepository.add(email, emailCode)
        return Result.success("邮件已发送")
    }

    suspend fun resetPassword(): Result<String> {
        // TODO
        return httpInternalServerError("未实现")
    }
}
