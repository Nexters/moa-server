package com.moa.common.response

import com.moa.common.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiResponseTest {

    @Test
    fun `성공 응답시 내용이 없으면 content는 null을 반환한다`() {

        val response = ApiResponse.success()


        assertThat(response.code).isEqualTo("SUCCESS")
        assertThat(response.message).isEqualTo("요청이 성공적으로 처리되었습니다")
        assertThat(response.content).isNull()
    }

    @Test
    fun `성공 응답시 content를 반환한다`() {

        val content = "test content"

        val response = ApiResponse.success(content)

        assertThat(response.code).isEqualTo("SUCCESS")
        assertThat(response.message).isEqualTo("요청이 성공적으로 처리되었습니다")
        assertThat(response.content).isEqualTo(content)
    }

    @Test
    fun `성공 응답시 객체 타입의 content도 반환한다`() {

        data class TestData(val id: Long, val name: String)

        val content = TestData(1L, "test")

        val response = ApiResponse.success(content)

        assertThat(response.content).isEqualTo(content)
        assertThat(response.content?.id).isEqualTo(1L)
        assertThat(response.content?.name).isEqualTo("test")
    }

    @Test
    fun `성공 응답시 커스텀 메시지를 반영한다`() {

        val content = "test content"
        val customMessage = "커스텀 성공 메시지"

        val response = ApiResponse.success(content, customMessage)

        assertThat(response.code).isEqualTo("SUCCESS")
        assertThat(response.message).isEqualTo(customMessage)
        assertThat(response.content).isEqualTo(content)
    }

    @Test
    fun `에러 응답시 ErrorCode의 코드와 메시지를 사용한다`() {

        val errorCode = ErrorCode.RESOURCE_NOT_FOUND

        val response = ApiResponse.error(errorCode)

        assertThat(response.code).isEqualTo(errorCode.code)
        assertThat(response.message).isEqualTo(errorCode.message)
        assertThat(response.content).isNull()
    }

    @Test
    fun `다양한 ErrorCode가 반영된다`() {

        val errorCodes = listOf(
            ErrorCode.BAD_REQUEST,
            ErrorCode.RESOURCE_NOT_FOUND,
            ErrorCode.INTERNAL_SERVER_ERROR,
        )

        errorCodes.forEach { errorCode ->
            val response = ApiResponse.error(errorCode)

            assertThat(response.code).isEqualTo(errorCode.code)
            assertThat(response.message).isEqualTo(errorCode.message)
        }
    }

    @Test
    fun `유효성 검증 에러시 VALIDATION_ERROR 코드를 사용하고 에러를 content에 포함한다`() {

        val errors = listOf(
            FieldError("email", "이메일 형식이 올바르지 않습니다"),
            FieldError("password", "비밀번호는 8자 이상이어야 합니다"),
        )

        val response = ApiResponse.validationError(errors)

        assertThat(response.code).isEqualTo(ErrorCode.BAD_REQUEST.code)
        assertThat(response.message).isEqualTo(ErrorCode.BAD_REQUEST.message)
        assertThat(response.content).hasSize(2)
        assertThat(response.content).containsExactlyElementsOf(errors)
    }

    @Test
    fun `유효성 검증 에러시 빈 에러 리스트도 처리할 수 있다`() {

        val errors = emptyList<FieldError>()

        val response = ApiResponse.validationError(errors)

        assertThat(response.code).isEqualTo(ErrorCode.BAD_REQUEST.code)
        assertThat(response.content).isEmpty()
    }
}
