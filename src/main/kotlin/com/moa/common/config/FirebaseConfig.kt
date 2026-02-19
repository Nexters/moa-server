package com.moa.common.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource

@Configuration
class FirebaseConfig {

    @PostConstruct
    fun initFirebase() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                val resource =
                    ClassPathResource("moa-secret/firebase/moamoa-cc333-firebase-adminsdk-fbsvc-0d84dba1f8.json")

                resource.inputStream.use { stream ->
                    val options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(stream))
                        .build()

                    FirebaseApp.initializeApp(options)
                }
            }
        } catch (ex: Exception) {
            throw RuntimeException("Firebase 초기화 중 오류가 발생했습니다.", ex)
        }
    }
}
