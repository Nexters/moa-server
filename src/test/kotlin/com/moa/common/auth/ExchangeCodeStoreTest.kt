package com.moa.common.auth

import com.github.benmanes.caffeine.cache.Ticker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExchangeCodeStoreTest {
    private val sut = ExchangeCodeStore(ttlSeconds = 120, maxSize = 10_000)

    @Test
    fun `issue 후 consume 하면 subject 를 돌려준다`() {
        val code = sut.issue(subject = "apple-sub-42")
        assertThat(sut.consume(code)).isEqualTo("apple-sub-42")
    }

    @Test
    fun `consume 은 1회용 - 두 번째는 null`() {
        val code = sut.issue("sub-7")
        sut.consume(code)
        assertThat(sut.consume(code)).isNull()
    }

    @Test
    fun `존재하지 않는 코드는 null`() {
        assertThat(sut.consume("nope")).isNull()
    }

    @Test
    fun `issue 는 매번 다른 코드를 만든다`() {
        assertThat(sut.issue("sub-1")).isNotEqualTo(sut.issue("sub-1"))
    }

    @Test
    fun `ttl 이 지나면 만료되어 null`() {
        val ticker = FakeTicker()
        val store = ExchangeCodeStore(ttlSeconds = 120, maxSize = 10, ticker = ticker)
        val code = store.issue("sub-1")

        ticker.advanceSeconds(121)

        assertThat(store.consume(code)).isNull()
    }

    @Test
    fun `ttl 이전에는 아직 유효하다`() {
        val ticker = FakeTicker()
        val store = ExchangeCodeStore(ttlSeconds = 120, maxSize = 10, ticker = ticker)
        val code = store.issue("sub-1")

        ticker.advanceSeconds(119)

        assertThat(store.consume(code)).isEqualTo("sub-1")
    }

    /** 시간을 수동으로 흘리는 가짜 시계 — 실시간 sleep 없이 만료를 결정적으로 검증한다. */
    private class FakeTicker : Ticker {
        private var nanos = 0L
        fun advanceSeconds(seconds: Long) {
            nanos += seconds * 1_000_000_000L
        }
        override fun read(): Long = nanos
    }
}
