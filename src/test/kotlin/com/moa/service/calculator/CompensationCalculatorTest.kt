package com.moa.service.calculator

import com.moa.entity.SalaryInputType
import com.moa.entity.Workday
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class CompensationCalculatorTest {
    private val compensationCalculator = CompensationCalculator()

    @Test
    fun `calculateDailyRate - 월급 300만원과 주5일 근무면 6월 기준 일급을 계산한다`() {
        val result = compensationCalculator.calculateDailyRate(
            targetDate = LocalDate.of(2025, 6, 1),
            salaryType = SalaryInputType.MONTHLY,
            salaryAmount = 3_000_000,
            workDays = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
        )

        assertThat(result).isEqualByComparingTo(BigDecimal("142857"))
    }

    @Test
    fun `calculateDailyRate - 연봉은 월급으로 환산 후 일급을 계산한다`() {
        val result = compensationCalculator.calculateDailyRate(
            targetDate = LocalDate.of(2025, 6, 1),
            salaryType = SalaryInputType.ANNUAL,
            salaryAmount = 36_000_000,
            workDays = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
        )

        assertThat(result).isEqualByComparingTo(BigDecimal("142857"))
    }

    @Test
    fun `calculateDailyRate - 근무일이 없으면 0을 반환한다`() {
        val result = compensationCalculator.calculateDailyRate(
            targetDate = LocalDate.of(2025, 6, 1),
            salaryType = SalaryInputType.MONTHLY,
            salaryAmount = 3_000_000,
            workDays = emptySet(),
        )

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `calculateDailyRate - 주3일 근무면 해당 근무일 수로 나눈다`() {
        val result = compensationCalculator.calculateDailyRate(
            targetDate = LocalDate.of(2025, 6, 1),
            salaryType = SalaryInputType.MONTHLY,
            salaryAmount = 3_000_000,
            workDays = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.FRIDAY,
            ),
        )

        assertThat(result).isEqualByComparingTo(BigDecimal("230769"))
    }

    @Test
    fun `calculateDailyRate - 단일 근무일만 있어도 계산한다`() {
        val result = compensationCalculator.calculateDailyRate(
            targetDate = LocalDate.of(2025, 6, 1),
            salaryType = SalaryInputType.MONTHLY,
            salaryAmount = 1_000_000,
            workDays = setOf(DayOfWeek.MONDAY),
        )

        assertThat(result).isEqualByComparingTo(BigDecimal("200000"))
    }

    @Test
    fun `calculateDailyRate - 소수점은 반올림한다`() {
        val result = compensationCalculator.calculateDailyRate(
            targetDate = LocalDate.of(2025, 6, 1),
            salaryType = SalaryInputType.MONTHLY,
            salaryAmount = 1_000_000,
            workDays = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
            ),
        )

        assertThat(result.scale()).isEqualTo(0)
    }

    @Test
    fun `calculateDailyRate - Workday enum 기준 주5일도 같은 결과를 낸다`() {
        val result = compensationCalculator.calculateDailyRate(
            targetDate = LocalDate.of(2025, 6, 1),
            salaryType = SalaryInputType.MONTHLY,
            salaryAmount = 3_000_000,
            workDays = setOf(
                Workday.MON.dayOfWeek,
                Workday.TUE.dayOfWeek,
                Workday.WED.dayOfWeek,
                Workday.THU.dayOfWeek,
                Workday.FRI.dayOfWeek,
            ),
        )

        assertThat(result).isEqualByComparingTo(BigDecimal("142857"))
    }

    @Test
    fun `calculateDailyRate - 월마다 근무일 수가 다르면 결과도 달라진다`() {
        val febResult = compensationCalculator.calculateDailyRate(
            targetDate = LocalDate.of(2025, 2, 1),
            salaryType = SalaryInputType.MONTHLY,
            salaryAmount = 3_000_000,
            workDays = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
        )
        val marResult = compensationCalculator.calculateDailyRate(
            targetDate = LocalDate.of(2025, 3, 1),
            salaryType = SalaryInputType.MONTHLY,
            salaryAmount = 3_000_000,
            workDays = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
        )

        assertThat(febResult).isNotEqualByComparingTo(marResult)
    }

    @Test
    fun `calculateWorkMinutes - 9시에서 18시는 540분을 반환한다`() {
        val result = compensationCalculator.calculateWorkMinutes(
            clockIn = LocalTime.of(9, 0),
            clockOut = LocalTime.of(18, 0),
        )

        assertThat(result).isEqualTo(540)
    }

    @Test
    fun `calculateWorkMinutes - 자정넘김 22시에서 2시는 240분을 반환한다`() {
        val result = compensationCalculator.calculateWorkMinutes(
            clockIn = LocalTime.of(22, 0),
            clockOut = LocalTime.of(2, 0),
        )

        assertThat(result).isEqualTo(240)
    }

    @Test
    fun `calculateWorkMinutes - 시작시간과 종료시간이 같으면 0분을 반환한다`() {
        val result = compensationCalculator.calculateWorkMinutes(
            clockIn = LocalTime.of(9, 0),
            clockOut = LocalTime.of(9, 0),
        )

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `calculateEarnings - 실제 근무시간이 정책과 같으면 일급과 동일한 금액을 반환한다`() {
        val dailyRate = BigDecimal("100000")

        val result = compensationCalculator.calculateEarnings(dailyRate, 540, 540)

        assertThat(result).isEqualByComparingTo(BigDecimal("100000"))
    }

    @Test
    fun `calculateEarnings - 초과 근무시 분급 기준으로 증가된 금액을 반환한다`() {
        val dailyRate = BigDecimal("100000")

        val result = compensationCalculator.calculateEarnings(dailyRate, 540, 600)

        assertThat(result).isEqualByComparingTo(BigDecimal("111111"))
    }

    @Test
    fun `calculateEarnings - 조기 퇴근시 분급 기준으로 감소된 금액을 반환한다`() {
        val dailyRate = BigDecimal("100000")

        val result = compensationCalculator.calculateEarnings(dailyRate, 540, 480)

        assertThat(result).isEqualByComparingTo(BigDecimal("88889"))
    }
}
