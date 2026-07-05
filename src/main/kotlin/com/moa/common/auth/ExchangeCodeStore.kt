package com.moa.common.auth

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * OIDC 데스크톱 핸드오프용 1회용 교환 코드 저장소
 *
 * 단일 인스턴스 배포를 전제로 한 인메모리(Caffeine) 구현이다
 * — 다중 인스턴스/롤링 배포에서는 외부 저장소로 교체해야 한다.
 */
@Component
class ExchangeCodeStore(
    @Value("\${auth.exchange-code.ttl-seconds:120}") ttlSeconds: Long = 120,
    @Value("\${auth.exchange-code.max-size:10000}") maxSize: Long = 10_000,
) {
    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
        .maximumSize(maxSize)
        .build<String, String>()

    /** 검증된 OIDC subject 를 1회용 코드로 봉인한다. 회원 생성은 consume 이후로 미룬다. */
    fun issue(subject: String): String {
        val code = OpaqueTokenGenerator.generate()
        cache.put(code, subject)
        return code
    }

    /** 원자적 1회 소비. 없거나 만료면 null. */
    fun consume(code: String): String? = cache.asMap().remove(code)
}
