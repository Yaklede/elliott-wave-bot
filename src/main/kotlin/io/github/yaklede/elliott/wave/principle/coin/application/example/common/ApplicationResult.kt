package bz.bix.agency.approval.system.application.common.response

import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.HttpStatus.OK
import org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE
import org.springframework.http.HttpStatus.UNAUTHORIZED

data class ApplicationResult(
    val code: Int,
    val message: String?,
    val detailMessage: String? = null,
) {
    companion object {
        fun ok(
            message: String?,
            detailMessage: String? = null
        ): ApplicationResult {
            val resolvedMessage = message ?: "success"
            return ApplicationResult(
                code = OK.value(),
                message = resolvedMessage,
                detailMessage = detailMessage ?: resolvedMessage
            )
        }

        fun unauthorized(message: String): ApplicationResult =
            ApplicationResult(
                code = UNAUTHORIZED.value(),
                message = message,
                detailMessage = message,
            )

        fun forbidden(message: String): ApplicationResult =
            ApplicationResult(
                code = FORBIDDEN.value(),
                message = message,
                detailMessage = message,
            )

        fun badRequest(message: String?): ApplicationResult =
            ApplicationResult(
                code = BAD_REQUEST.value(),
                message = message,
                detailMessage = message,
            )

        fun server(message: String?): ApplicationResult =
            ApplicationResult(
                code = INTERNAL_SERVER_ERROR.value(),
                message = message,
                detailMessage = message,
            )

        fun notFound(message: String?): ApplicationResult =
            ApplicationResult(
                code = NOT_FOUND.value(),
                message = message,
                detailMessage = message,
            )

        fun noContent(message: String?): ApplicationResult =
            ApplicationResult(
                code = NO_CONTENT.value(),
                message = message,
                detailMessage = message,
            )

        fun conflict(message: String?): ApplicationResult =
            ApplicationResult(
                code = CONFLICT.value(),
                message = message,
                detailMessage = message,
            )

        fun tooLarge(message: String?): ApplicationResult =
            ApplicationResult(
                code = PAYLOAD_TOO_LARGE.value(),
                message = message,
                detailMessage = message,
            )
    }
}
