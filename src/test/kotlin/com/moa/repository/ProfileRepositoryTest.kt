package com.moa.repository

import com.moa.entity.PaydayDay
import com.moa.entity.Profile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest

@DataJpaTest
class ProfileRepositoryTest @Autowired constructor(
    private val profileRepository: ProfileRepository,
) {

    @Test
    fun `paydayDay value 목록에 포함되는 프로필만 조회한다`() {
        val included = profileRepository.save(
            Profile(
                memberId = 1L,
                nickname = "included",
                paydayDay = PaydayDay(25),
            ),
        )
        profileRepository.save(
            Profile(
                memberId = 2L,
                nickname = "excluded",
                paydayDay = PaydayDay(10),
            ),
        )

        val result = profileRepository.findAllByPaydayDay_ValueIn(listOf(25, 31))

        assertThat(result.map { it.id }).containsExactly(included.id)
    }

    @Test
    fun `여러 paydayDay value를 전달하면 해당 값들을 가진 프로필을 모두 조회한다`() {
        val first = profileRepository.save(
            Profile(
                memberId = 3L,
                nickname = "first",
                paydayDay = PaydayDay(25),
            ),
        )
        val second = profileRepository.save(
            Profile(
                memberId = 4L,
                nickname = "second",
                paydayDay = PaydayDay(31),
            ),
        )
        profileRepository.save(
            Profile(
                memberId = 5L,
                nickname = "third",
                paydayDay = PaydayDay(12),
            ),
        )

        val result = profileRepository.findAllByPaydayDay_ValueIn(listOf(25, 31))

        assertThat(result.map { it.id }).containsExactlyInAnyOrder(first.id, second.id)
    }
}
