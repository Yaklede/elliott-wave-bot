package bz.bix.agency.approval.system.application.common.response

import bz.bix.agency.approval.system.application.common.response.ApplicationResponse.Companion.badRequest
import bz.bix.agency.approval.system.application.common.response.ApplicationResponse.Companion.conflict
import bz.bix.agency.approval.system.application.common.response.ApplicationResponse.Companion.forbidden
import bz.bix.agency.approval.system.application.common.response.ApplicationResponse.Companion.noContent
import bz.bix.agency.approval.system.application.common.response.ApplicationResponse.Companion.notFound
import bz.bix.agency.approval.system.application.common.response.ApplicationResponse.Companion.ok
import bz.bix.agency.approval.system.application.common.response.ApplicationResponse.Companion.server
import bz.bix.agency.approval.system.application.common.response.ApplicationResponse.Companion.tooLarge
import bz.bix.agency.approval.system.application.common.response.ApplicationResponse.Companion.unauthorized
import com.fasterxml.jackson.annotation.JsonInclude

data class ApplicationResponse<T>(
    val result: ApplicationResult,
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val payload: T? = null,
) {
    companion object {
        fun <T> ok(
            message: String? = null,
            payload: T? = null,
            detailMessage: String? = null,
        ): ApplicationResponse<T> =
            ApplicationResponse(
                result = ApplicationResult.Companion.ok(message, detailMessage),
                payload = payload,
            )

        fun <T> unauthorized(
            message: String,
            payload: T? = null,
        ): ApplicationResponse<T> =
            ApplicationResponse(
                result = ApplicationResult.Companion.unauthorized(message),
                payload = payload,
            )

        fun <T> forbidden(
            message: String,
            payload: T? = null,
        ): ApplicationResponse<T> =
            ApplicationResponse(
                result = ApplicationResult.Companion.forbidden(message),
                payload = payload,
            )

        fun <T> badRequest(
            message: String,
            payload: T? = null,
        ): ApplicationResponse<T> =
            ApplicationResponse(
                result = ApplicationResult.Companion.badRequest(message),
                payload = payload,
            )

        fun <T> server(
            message: String?,
            payload: T? = null,
        ): ApplicationResponse<T> =
            ApplicationResponse(
                result = ApplicationResult.Companion.server(message),
                payload = payload,
            )

        fun <T> notFound(
            message: String,
            payload: T? = null,
        ): ApplicationResponse<T> =
            ApplicationResponse(
                result = ApplicationResult.Companion.notFound(message),
                payload = payload,
            )

        fun <T> noContent(
            message: String?,
            payload: T? = null,
        ): ApplicationResponse<T> =
            ApplicationResponse(
                result = ApplicationResult.Companion.noContent(message),
                payload = payload,
            )

        fun <T> conflict(
            message: String? = null,
            payload: T? = null,
        ): ApplicationResponse<T> =
            ApplicationResponse(
                result = ApplicationResult.Companion.conflict(message),
                payload = payload,
            )

        fun <T> tooLarge(
            message: String,
            payload: T? = null,
        ): ApplicationResponse<T> =
            ApplicationResponse(
                result = ApplicationResult.Companion.tooLarge(message),
                payload = payload,
            )
    }
}

// 일반 객체용 toApplicationResponse
fun <T> T.toApplicationResponse(
    type: ReturnType = ReturnType.OK,
    message: String? = null,
    detailMessage: String? = null,
): ApplicationResponse<T> =
    when (type) {
        ReturnType.OK -> ok(payload = this, detailMessage = detailMessage)
        ReturnType.UNAUTHORIZED -> unauthorized(
            message = message ?: "unauthorized",
            payload = this
        )
        ReturnType.FORBIDDEN -> forbidden(
            message = message ?: "forbidden",
            payload = this
        )
        ReturnType.BAD_REQUEST -> badRequest(
            message = message ?: "bad request",
            payload = this
        )
        ReturnType.SERVER -> server(
            message = message,
            payload = this
        )
        ReturnType.NOT_FOUND -> notFound(
            message = message ?: "not found",
            payload = this
        )
        ReturnType.NO_CONTENT -> noContent(
            message = message,
            payload = this
        )
        ReturnType.CONFLICT -> conflict(
            message = message,
            payload = this
        )
        ReturnType.TOO_LARGE -> tooLarge(
            message = message ?: "too large",
            payload = this
        )
    }

enum class ReturnType {
    OK,
    UNAUTHORIZED,
    FORBIDDEN,
    BAD_REQUEST,
    SERVER,
    NOT_FOUND,
    NO_CONTENT,
    CONFLICT,
    TOO_LARGE,
}
