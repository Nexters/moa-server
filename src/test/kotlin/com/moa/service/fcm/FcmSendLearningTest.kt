package com.moa.service.fcm

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

@Tag("learning")
class FcmSendLearningTest {

    companion object {
        // 실제 디바이스의 FCM 토큰을 여기에 붙여넣어 테스트
        private const val TEST_FCM_TOKEN =
            ""

        @JvmStatic
        @BeforeAll
        fun initFirebase() {
            if (FirebaseApp.getApps().isEmpty()) {
                val resource = ClassPathResource(
                    "moa-secret/firebase/moamoa-cc333-firebase-adminsdk-fbsvc-0d84dba1f8.json"
                )
                resource.inputStream.use { stream ->
                    val options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(stream))
                        .build()
                    FirebaseApp.initializeApp(options)
                }
            }
        }
    }

    @Test
    fun `FCM 메시지 전송 테스트`() {
        val message = Message.builder()
            .setToken(TEST_FCM_TOKEN)
            .setNotification(
                Notification.builder()
                    .setTitle("테스트 알림")
                    .setBody("FCM 전송 학습 테스트입니다.")
                    .build()
            )
            .build()

        val messageId = FirebaseMessaging.getInstance().send(message)

        println("전송 성공 — messageId: $messageId")
        assertThat(messageId).isNotBlank()
    }
}
