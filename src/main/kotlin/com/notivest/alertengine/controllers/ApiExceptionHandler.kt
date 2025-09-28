package com.notivest.alertengine.web

import com.notivest.alertengine.exception.ForbiddenOperationException
import com.notivest.alertengine.exception.InvalidParamsException
import com.notivest.alertengine.exception.ResourceNotFoundException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.MDC
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.HandlerMapping
import java.time.Instant

@RestControllerAdvice
class ApiExceptionHandler {

    data class ErrorBody(
        val timestamp: String = Instant.now().toString(),
        val status: Int,
        val error: String,              // Razón humana (e.g. "Bad Request")
        val errorCode: String?,         // Código machine-friendly (e.g. "validation_error")
        val message: String?,
        val path: String,
        val method: String?,
        val pathPattern: String?,
        val requestId: String?,
        val traceId: String?,           // si usás micrometer tracing, se pobla en MDC
        val fields: Map<String, String>? = null,
        val violations: List<Violation>? = null,
        val exception: String? = null,
        val cause: String? = null,
    ) {
        data class Violation(val property: String, val message: String)
    }

    // 404
    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(ex: ResourceNotFoundException, req: HttpServletRequest) =
        status(HttpStatus.NOT_FOUND, "not_found", ex.message, req)

    // 403
    @ExceptionHandler(ForbiddenOperationException::class)
    fun handleForbidden(ex: ForbiddenOperationException, req: HttpServletRequest) =
        status(HttpStatus.FORBIDDEN, "forbidden", ex.message, req)

    // 400 (params inválidos por JSON Schema)
    @ExceptionHandler(InvalidParamsException::class)
    fun handleInvalidParams(ex: InvalidParamsException, req: HttpServletRequest) =
        status(HttpStatus.BAD_REQUEST, "invalid_request", ex.message, req, exception = ex)

    // 400 (Bean Validation @Valid)
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBeanValidation(ex: MethodArgumentNotValidException, req: HttpServletRequest): ResponseEntity<ErrorBody> {
        val fields = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
        return status(HttpStatus.BAD_REQUEST, "validation_error", "Bean validation failed", req, fields = fields, exception = ex)
    }

    // 400 (Constraint violations en params/path/query)
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolations(ex: ConstraintViolationException, req: HttpServletRequest): ResponseEntity<ErrorBody> {
        val violations = ex.constraintViolations.map {
            ErrorBody.Violation(it.propertyPath.toString(), it.message)
        }
        return status(HttpStatus.BAD_REQUEST, "constraint_violation", "Constraint violations", req, violations = violations, exception = ex)
    }

    // 409 (conflictos de unicidad/estado)
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleConflict(ex: DataIntegrityViolationException, req: HttpServletRequest) =
        status(
            HttpStatus.CONFLICT,
            "conflict",
            "Operation conflicts with current state",
            req,
            exception = ex,
            cause = ex.mostSpecificCause.message
        )

    // 400 (JSON malformado)
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleBadJson(ex: HttpMessageNotReadableException, req: HttpServletRequest) =
        status(
            HttpStatus.BAD_REQUEST,
            "bad_json",
            "Malformed JSON request body",
            req,
            exception = ex,
            cause = ex.mostSpecificCause.message
        )

    // 400 (mismatch de tipos en path/query)
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException, req: HttpServletRequest) =
        status(
            HttpStatus.BAD_REQUEST,
            "type_mismatch",
            "Invalid value for parameter '${ex.name}'",
            req,
            exception = ex,
            cause = ex.mostSpecificCause.message
        )

    // 400 (falta parámetro requerido)
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(ex: MissingServletRequestParameterException, req: HttpServletRequest) =
        status(HttpStatus.BAD_REQUEST, "missing_parameter", "Missing required parameter '${ex.parameterName}'", req, exception = ex)

    // 405
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(ex: HttpRequestMethodNotSupportedException, req: HttpServletRequest) =
        status(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed", ex.message, req, exception = ex)

    // 500 (fallback)
    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception, req: HttpServletRequest) =
        status(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal_error",
            "Unexpected error",
            req,
            exception = ex,
            cause = ex.cause?.message
        )

    // -------- helpers --------

    private fun status(
        http: HttpStatus,
        code: String,
        message: String?,
        req: HttpServletRequest,
        fields: Map<String, String>? = null,
        violations: List<ErrorBody.Violation>? = null,
        exception: Throwable? = null,
        cause: String? = null
    ): ResponseEntity<ErrorBody> =
        ResponseEntity.status(http).body(errorBody(http, code, message, req, fields, violations, exception, cause))

    private fun errorBody(
        status: HttpStatus,
        code: String,
        message: String?,
        req: HttpServletRequest,
        fields: Map<String, String>?,
        violations: List<ErrorBody.Violation>?,
        exception: Throwable?,
        cause: String?
    ): ErrorBody {
        val pathPattern = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String
        val requestId = req.getHeader("X-Request-ID")?.takeIf { it.isNotBlank() }
        val traceId = MDC.get("traceId") ?: req.getHeader("X-B3-TraceId")

        return ErrorBody(
            status = status.value(),
            error = status.reasonPhrase,   // humano
            errorCode = code,              // machine-friendly
            message = message,
            path = req.requestURI,
            method = req.method,
            pathPattern = pathPattern,
            requestId = requestId,
            traceId = traceId,
            fields = fields,
            violations = violations,
            exception = exception?.javaClass?.simpleName,
            cause = cause
        )
    }
}
