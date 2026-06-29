package com.moa.common.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RefreshTokenHasherTest {

    private val sut = RefreshTokenHasher()

    @Test
    fun `generate 는 매번 다른 평문을 만든다`() {
        val a = sut.generate()
        val b = sut.generate()
        assertThat(a).isNotEqualTo(b)
        assertThat(a).isNotBlank()
        assertThat(a).matches("[A-Za-z0-9_-]+")
        assertThat(a).hasSize(43)
    }

    @Test
    fun `같은 평문은 같은 해시로, 다른 평문은 다른 해시로 매핑된다`() {
        val plain = sut.generate()
        assertThat(sut.hash(plain)).isEqualTo(sut.hash(plain))
        assertThat(sut.hash(plain)).isNotEqualTo(sut.hash(sut.generate()))
    }

    @Test
    fun `해시는 평문을 그대로 노출하지 않는다`() {
        val plain = sut.generate()
        assertThat(sut.hash(plain)).doesNotContain(plain)
        assertThat(sut.hash(plain)).matches("[0-9a-f]{64}")
    }
}
