package com.moa.service

import com.google.firebase.messaging.*
import com.moa.repository.FcmTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FcmService(
    private val fcmTokenRepository: FcmTokenRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendEach(requests: List<Pair<String, Map<String, String>>>): List<Boolean> {
        if (requests.isEmpty()) return emptyList()
        val results = ArrayList<Boolean>(requests.size)
        requests.chunked(MAX_BATCH_SIZE).forEach { batch ->
            try {
                val messages = batch.map { (token, data) -> buildMessage(token, data) }
                val response = FirebaseMessaging.getInstance().sendEach(messages)
                response.responses.forEachIndexed { i, sendResponse ->
                    if (!sendResponse.isSuccessful) {
                        handleFcmException(sendResponse.exception, batch[i].first)
                    }
                    results.add(sendResponse.isSuccessful)
                }
            } catch (ex: Exception) {
                log.error("FCM batch send failed for {} messages", batch.size, ex)
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
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.INVALID_ARGUMENT -> {
                log.warn("유효하지 않은 FCM 토큰 삭제: {}", token)
                fcmTokenRepository.deleteByToken(token)
            }

            else -> log.error("FCM 전송 실패: {}", token, fcmEx)
        }
    }

    companion object {
        private const val MAX_BATCH_SIZE = 500
    }
}
