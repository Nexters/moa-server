package com.moa.common.response

import com.moa.common.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ApiResponseTest {

    @Nested
    @DisplayName("success() 메서드")
    inner class SuccessNoArg {
        @Test
        @DisplayName("code는 SUCCESS, message는 기본 메시지, content는 null을 반환한다")
        fun returnsSuccessWithNullContent() {
            val response = ApiResponse.success()

            assertThat(response.code).isEqualTo("SUCCESS")
            assertThat(response.message).isEqualTo("요청이 성공적으로 처리되었습니다")
            assertThat(response.content).isNull()
        }
    }

    @Nested
    @DisplayName("success(content) 메서드")
    inner class SuccessWithContent {
        @Test
        @DisplayName("전달한 content를 정확히 반환한다")
        fun returnsSuccessWithContent() {
            val content = "test content"

            val response = ApiResponse.success(content)

            assertThat(response.code).isEqualTo("SUCCESS")
            assertThat(response.message).isEqualTo("요청이 성공적으로 처리되었습니다")
            assertThat(response.content).isEqualTo(content)
        }

        @Test
        @DisplayName("객체 타입의 content도 정확히 반환한다")
        fun returnsSuccessWithObjectContent() {
            data class TestData(val id: Long, val name: String)
            val content = TestData(1L, "test")

            val response = ApiResponse.success(content)

            assertThat(response.content).isEqualTo(content)
            assertThat(response.content?.id).isEqualTo(1L)
            assertThat(response.content?.name).isEqualTo("test")
        }
    }

    @Nested
    @DisplayName("success(content, message) 메서드")
    inner class SuccessWithContentAndMessage {
        @Test
        @DisplayName("커스텀 메시지가 정확히 반영된다")
        fun returnsSuccessWithCustomMessage() {
            val content = "test content"
            val customMessage = "커스텀 성공 메시지"

            val response = ApiResponse.success(content, customMessage)

            assertThat(response.code).isEqualTo("SUCCESS")
            assertThat(response.message).isEqualTo(customMessage)
            assertThat(response.content).isEqualTo(content)
        }
    }

    @Nested
    @DisplayName("error(code, message) 메서드")
    inner class ErrorWithCodeAndMessage {
        @Test
        @DisplayName("전달한 code와 message를 반환하고 content는 null이다")
        fun returnsErrorWithCodeAndMessage() {
            val errorCode = "E001"
            val errorMessage = "에러 메시지"

            val response = ApiResponse.error(errorCode, errorMessage)

            assertThat(response.code).isEqualTo(errorCode)
            assertThat(response.message).isEqualTo(errorMessage)
            assertThat(response.content).isNull()
        }
    }

    @Nested
    @DisplayName("error(ErrorCode) 메서드")
    inner class ErrorWithErrorCode {
        @Test
        @DisplayName("ErrorCode의 code와 message를 사용한다")
        fun returnsErrorFromErrorCode() {
            val errorCode = ErrorCode.RESOURCE_NOT_FOUND

            val response = ApiResponse.error(errorCode)

            assertThat(response.code).isEqualTo(errorCode.code)
            assertThat(response.message).isEqualTo(errorCode.message)
            assertThat(response.content).isNull()
        }

        @Test
        @DisplayName("다양한 ErrorCode가 정확히 반영된다")
        fun returnsErrorFromVariousErrorCodes() {
            val errorCodes = listOf(
                ErrorCode.INVALID_INPUT,
                ErrorCode.INTERNAL_SERVER_ERROR,
                ErrorCode.ENTITY_NOT_FOUND,
            )

            errorCodes.forEach { errorCode ->
                val response = ApiResponse.error(errorCode)

                assertThat(response.code).isEqualTo(errorCode.code)
                assertThat(response.message).isEqualTo(errorCode.message)
            }
        }
    }

    @Nested
    @DisplayName("validationError(errors) 메서드")
    inner class ValidationError {
        @Test
        @DisplayName("VALIDATION_ERROR 코드를 사용하고 errors가 content에 포함된다")
        fun returnsValidationError() {
            val errors = listOf(
                FieldError("email", "이메일 형식이 올바르지 않습니다"),
                FieldError("password", "비밀번호는 8자 이상이어야 합니다"),
            )

            val response = ApiResponse.validationError(errors)

            assertThat(response.code).isEqualTo(ErrorCode.VALIDATION_ERROR.code)
            assertThat(response.message).isEqualTo(ErrorCode.VALIDATION_ERROR.message)
            assertThat(response.content).hasSize(2)
            assertThat(response.content).containsExactlyElementsOf(errors)
        }

        @Test
        @DisplayName("빈 errors 리스트도 처리할 수 있다")
        fun returnsValidationErrorWithEmptyList() {
            val errors = emptyList<FieldError>()

            val response = ApiResponse.validationError(errors)

            assertThat(response.code).isEqualTo(ErrorCode.VALIDATION_ERROR.code)
            assertThat(response.content).isEmpty()
        }
    }
}
