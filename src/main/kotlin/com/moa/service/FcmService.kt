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

    fun send(token: String, title: String, body: String): Boolean {
        val message = Message.builder()
            .setToken(token)
            .setNotification(
                Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            )
            .build()

        return try {
            FirebaseMessaging.getInstance().send(message)
            true
        } catch (ex: FirebaseMessagingException) {
            handleFcmException(ex, token)
            false
        }
    }

    private fun handleFcmException(ex: FirebaseMessagingException, token: String) {
        when (ex.messagingErrorCode) {
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.INVALID_ARGUMENT -> {
                log.warn("유효하지 않은 FCM 토큰 삭제: {}", token)
                fcmTokenRepository.deleteByToken(token)
            }

            else -> log.error("FCM 전송 실패", ex)
        }
    }
}
