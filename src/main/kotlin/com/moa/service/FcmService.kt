package com.moa.service

import com.google.firebase.messaging.*
import com.moa.repository.FcmTokenRepository
import com.moa.service.dto.FcmRequest
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FcmService(
    private val fcmTokenRepository: FcmTokenRepository,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val sendSuccess: Counter = Counter.builder(METRIC_SEND)
        .description("FCM 메시지 단위 결과")
        .tag("result", "success")
        .register(meterRegistry)
    private val sendFailure: Counter = Counter.builder(METRIC_SEND)
        .description("FCM 메시지 단위 결과")
        .tag("result", "failure")
        .register(meterRegistry)

    private fun tokenInvalidated(errorCode: String): Counter = Counter.builder(METRIC_TOKEN_INVALIDATED)
        .description("FCM 에러로 자동 삭제된 토큰 수")
        .tag("error_code", errorCode)
        .register(meterRegistry)

    fun sendEach(requests: List<FcmRequest>): List<Boolean> {
        if (requests.isEmpty()) return emptyList()
        val results = ArrayList<Boolean>(requests.size)
        requests.chunked(MAX_BATCH_SIZE).forEach { batch ->
            try {
                val messages = batch.map { buildMessage(it.token, it.data) }
                val response = FirebaseMessaging.getInstance().sendEach(messages)
                response.responses.forEachIndexed { i, sendResponse ->
                    if (sendResponse.isSuccessful) {
                        sendSuccess.increment()
                    } else {
                        sendFailure.increment()
                        handleFcmException(sendResponse.exception, batch[i].token)
                    }
                    results.add(sendResponse.isSuccessful)
                }
            } catch (ex: Exception) {
                log.error("FCM batch send failed for {} messages", batch.size, ex)
                sendFailure.increment(batch.size.toDouble())
                repeat(batch.size) { results.add(false) }
            }
        }
        return results
    }

    private fun buildMessage(token: String, data: Map<String, String>): Message =
        Message.builder()
            .setToken(token)
            .setNotification(
                Notification.builder()
                    .setTitle(data["title"])
                    .setBody(data["body"])
                    .build()
            )
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .build()
            )
            .putAllData(data)
            .build()

    private fun handleFcmException(ex: Exception?, token: String) {
        val fcmEx = ex as? FirebaseMessagingException ?: run {
            log.error("FCM 전송 중 예상치 못한 예외: {}", token, ex)
            return
        }
        when (fcmEx.messagingErrorCode) {
            MessagingErrorCode.UNREGISTERED -> {
                log.warn("유효하지 않은 FCM 토큰 삭제: {}", token)
                fcmTokenRepository.deleteByToken(token)
                tokenInvalidated("unregistered").increment()
            }
            MessagingErrorCode.INVALID_ARGUMENT -> {
                log.warn("유효하지 않은 FCM 토큰 삭제: {}", token)
                fcmTokenRepository.deleteByToken(token)
                tokenInvalidated("invalid_argument").increment()
            }
            else -> log.error("FCM 전송 실패: {}", token, fcmEx)
        }
    }

    companion object {
        private const val MAX_BATCH_SIZE = 500
        private const val METRIC_SEND = "moa.fcm.send"
        private const val METRIC_TOKEN_INVALIDATED = "moa.fcm.token_invalidated"
    }
}
