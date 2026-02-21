package com.moa.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class SalaryCalculatorTest {

    companion object {
        private val WEEKDAYS = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
    }

    // --- 월급 기반 일급 계산 ---

    @Test
    fun `MONTHLY - 월급을 해당 월의 근무일수로 나눈 일급을 반환한다`() {
        // 2025년 2월: 평일 20일
        val targetDate = LocalDate.of(2025, 2, 3)
        val monthlySalary = 3_000_000L

        val result = SalaryCalculator.calculateDailyRate(
            targetDate = targetDate,
            salaryType = SalaryType.MONTHLY,
            salaryAmount = monthlySalary,
            workDays = WEEKDAYS,
        )

        val expected = BigDecimal(3_000_000).divide(BigDecimal(20), 0, RoundingMode.HALF_UP)
        assertThat(result).isEqualByComparingTo(expected)
    }

    // --- 연봉 기반 일급 계산 ---

    @Test
    fun `YEARLY - 연봉을 12로 나눈 월급 기준으로 일급을 계산한다`() {
        // 2025년 2월: 평일 20일
        val targetDate = LocalDate.of(2025, 2, 3)
        val yearlySalary = 36_000_000L

        val result = SalaryCalculator.calculateDailyRate(
            targetDate = targetDate,
            salaryType = SalaryType.YEARLY,
            salaryAmount = yearlySalary,
            workDays = WEEKDAYS,
        )

        val expected = BigDecimal(36_000_000).divide(BigDecimal(12), 0, RoundingMode.HALF_UP)
            .divide(BigDecimal(20), 0, RoundingMode.HALF_UP)
        assertThat(result).isEqualByComparingTo(expected)
    }

    // --- 월별 근무일수 차이 ---

    @Test
    fun `3월은 평일 21일 기준으로 일급을 계산한다`() {
        // 2025년 3월: 평일 21일
        val targetDate = LocalDate.of(2025, 3, 10)

        val result = SalaryCalculator.calculateDailyRate(
            targetDate = targetDate,
            salaryType = SalaryType.MONTHLY,
            salaryAmount = 2_100_000L,
            workDays = WEEKDAYS,
        )

        val expected = BigDecimal(2_100_000).divide(BigDecimal(21), 0, RoundingMode.HALF_UP)
        assertThat(result).isEqualByComparingTo(expected)
    }

    @Test
    fun `1월은 평일 23일 기준으로 일급을 계산한다`() {
        // 2025년 1월: 평일 23일
        val targetDate = LocalDate.of(2025, 1, 15)

        val result = SalaryCalculator.calculateDailyRate(
            targetDate = targetDate,
            salaryType = SalaryType.MONTHLY,
            salaryAmount = 2_300_000L,
            workDays = WEEKDAYS,
        )

        val expected = BigDecimal(2_300_000).divide(BigDecimal(23), 0, RoundingMode.HALF_UP)
        assertThat(result).isEqualByComparingTo(expected)
    }

    // --- 2월 처리 ---

    @Test
    fun `2월은 28일 기준으로 근무일수를 계산한다`() {
        // 2025년 2월 (비윤년): 평일 20일
        val targetDate = LocalDate.of(2025, 2, 10)

        val result = SalaryCalculator.calculateDailyRate(
            targetDate = targetDate,
            salaryType = SalaryType.MONTHLY,
            salaryAmount = 2_000_000L,
            workDays = WEEKDAYS,
        )

        val expected = BigDecimal(2_000_000).divide(BigDecimal(20), 0, RoundingMode.HALF_UP)
        assertThat(result).isEqualByComparingTo(expected)
    }

    @Test
    fun `윤년 2월은 29일 기준으로 근무일수를 계산한다`() {
        // 2024년 2월 (윤년): 평일 21일
        val targetDate = LocalDate.of(2024, 2, 10)

        val result = SalaryCalculator.calculateDailyRate(
            targetDate = targetDate,
            salaryType = SalaryType.MONTHLY,
            salaryAmount = 2_100_000L,
            workDays = WEEKDAYS,
        )

        val expected = BigDecimal(2_100_000).divide(BigDecimal(21), 0, RoundingMode.HALF_UP)
        assertThat(result).isEqualByComparingTo(expected)
    }

    // --- 근무요일 설정 ---

    @Test
    fun `주6일 근무자는 토요일도 근무일수에 포함된다`() {
        val sixDayWork = WEEKDAYS + DayOfWeek.SATURDAY
        // 2025년 2월: 월~토 = 24일
        val targetDate = LocalDate.of(2025, 2, 3)

        val result = SalaryCalculator.calculateDailyRate(
            targetDate = targetDate,
            salaryType = SalaryType.MONTHLY,
            salaryAmount = 2_400_000L,
            workDays = sixDayWork,
        )

        val expected = BigDecimal(2_400_000).divide(BigDecimal(24), 0, RoundingMode.HALF_UP)
        assertThat(result).isEqualByComparingTo(expected)
    }

    // --- 엣지 케이스 ---

    @Test
    fun `근무요일이 없으면 일급은 0을 반환한다`() {
        val result = SalaryCalculator.calculateDailyRate(
            targetDate = LocalDate.of(2025, 2, 3),
            salaryType = SalaryType.MONTHLY,
            salaryAmount = 3_000_000L,
            workDays = emptySet(),
        )

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO)
    }

    // --- 근무 시간(분) 계산 ---

    @Test
    fun `calculateWorkMinutes - 9시에서 18시는 540분을 반환한다`() {
        val result = SalaryCalculator.calculateWorkMinutes(
            LocalTime.of(9, 0),
            LocalTime.of(18, 0),
        )
        assertThat(result).isEqualTo(540L)
    }

    @Test
    fun `calculateWorkMinutes - 자정넘김 22시에서 2시는 240분을 반환한다`() {
        val result = SalaryCalculator.calculateWorkMinutes(
            LocalTime.of(22, 0),
            LocalTime.of(2, 0),
        )
        assertThat(result).isEqualTo(240L)
    }

    // --- 실제 수입 계산 ---

    @Test
    fun `calculateEarnings - 실제 근무시간이 정책과 같으면 일급과 동일한 금액을 반환한다`() {
        val dailyRate = BigDecimal(150_000)
        val result = SalaryCalculator.calculateEarnings(dailyRate, 540, 540)
        assertThat(result).isEqualByComparingTo(dailyRate)
    }

    @Test
    fun `calculateEarnings - 초과 근무시 분급 기준으로 증가된 금액을 반환한다`() {
        val dailyRate = BigDecimal(150_000)
        // 540분 정책, 600분 실제 (1시간 초과)
        val result = SalaryCalculator.calculateEarnings(dailyRate, 540, 600)
        assertThat(result).isGreaterThan(dailyRate)
    }

    @Test
    fun `calculateEarnings - 조기 퇴근시 분급 기준으로 감소된 금액을 반환한다`() {
        val dailyRate = BigDecimal(150_000)
        // 540분 정책, 480분 실제 (1시간 조기 퇴근)
        val result = SalaryCalculator.calculateEarnings(dailyRate, 540, 480)
        assertThat(result).isLessThan(dailyRate)
    }

    @Test
    fun `같은 월급이라도 월마다 근무일수에 따라 일급이 달라진다`() {
        val salary = 3_000_000L

        val febResult = SalaryCalculator.calculateDailyRate(
            targetDate = LocalDate.of(2025, 2, 1),
            salaryType = SalaryType.MONTHLY,
            salaryAmount = salary,
            workDays = WEEKDAYS,
        )

        val marResult = SalaryCalculator.calculateDailyRate(
            targetDate = LocalDate.of(2025, 3, 1),
            salaryType = SalaryType.MONTHLY,
            salaryAmount = salary,
            workDays = WEEKDAYS,
        )

        // 2월(20일) > 3월(21일) → 2월 일급이 더 높아야 함
        assertThat(febResult).isGreaterThan(marResult)
    }
}
