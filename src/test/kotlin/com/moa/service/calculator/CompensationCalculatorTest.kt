package com.moa.service.calculator

import com.moa.entity.DailyWorkScheduleType
import com.moa.entity.SalaryInputType
import com.moa.entity.WorkPolicyVersion
import com.moa.entity.Workday
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class CompensationCalculatorTest {
    private val compensationCalculator = CompensationCalculator()

    companion object {
        private val DATE = LocalDate.of(2025, 6, 9)
    }

    private fun createPolicy(
        clockIn: LocalTime = LocalTime.of(9, 0),
        clockOut: LocalTime = LocalTime.of(18, 0),
        workdays: MutableSet<Workday> = mutableSetOf(Workday.MON, Workday.TUE, Workday.WED, Workday.THU, Workday.FRI),
    ) = WorkPolicyVersion(
        memberId = 1L,
        effectiveFrom = DATE.minusDays(30),
        clockInTime = clockIn,
        clockOutTime = clockOut,
        workdays = workdays,
    )

    @Test
    fun `기준 근무 시간은 정책의 일일 근무시간과 월 근무일 수를 곱해 계산한다`() {
        val result = compensationCalculator.calculateStandardMinutes(
            policy = createPolicy(),
            start = LocalDate.of(2025, 6, 1),
            endInclusive = LocalDate.of(2025, 6, 30),
        )

        assertThat(result).isEqualTo(11_340)
    }

    @Test
    fun `9시에서 18시까지는 540분을 반환한다`() {
        val result = compensationCalculator.calculateWorkMinutes(
            clockIn = LocalTime.of(9, 0),
            clockOut = LocalTime.of(18, 0),
        )

        assertThat(result).isEqualTo(540)
    }

    @Test
    fun `자정을 넘겨 22시에서 2시까지 근무하면 240분을 반환한다`() {
        val result = compensationCalculator.calculateWorkMinutes(
            clockIn = LocalTime.of(22, 0),
            clockOut = LocalTime.of(2, 0),
        )

        assertThat(result).isEqualTo(240)
    }

    @Test
    fun `시작시간과 종료시간이 같으면 0분을 반환한다`() {
        val result = compensationCalculator.calculateWorkMinutes(
            clockIn = LocalTime.of(9, 0),
            clockOut = LocalTime.of(9, 0),
        )

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `연봉 3,600,000이면 기준 월급 300,000을 반환한다`() {
        val result = compensationCalculator.calculateStandardSalary(SalaryInputType.ANNUAL, 3_600_000)

        assertThat(result).isEqualByComparingTo(BigDecimal("300000"))
    }

    @Test
    fun `월급 3,000,000이면 기준 월급으로 그대로 3,000,000을 반환한다`() {
        val result = compensationCalculator.calculateStandardSalary(SalaryInputType.MONTHLY, 3_000_000)

        assertThat(result).isEqualByComparingTo(BigDecimal("3000000"))
    }

    @Test
    fun `휴무이면 저장된 시간 기반으로 급여를 계산한다`() {
        val result = compensationCalculator.calculateDailyEarnings(
            date = DATE,
            salaryType = SalaryInputType.MONTHLY,
            salaryAmount = 3_000_000,
            policy = createPolicy(),
            type = DailyWorkScheduleType.VACATION,
            clockInTime = LocalTime.of(9, 0),
            clockOutTime = LocalTime.of(18, 0),
        )

        assertThat(result.toLong()).isEqualTo(142857L)
    }

    @Test
    fun `근무 일정이 없으면 ZERO를 반환한다`() {
        val result = compensationCalculator.calculateDailyEarnings(
            date = DATE,
            salaryType = SalaryInputType.MONTHLY,
            salaryAmount = 3_000_000,
            policy = createPolicy(),
            type = DailyWorkScheduleType.NONE,
            clockInTime = null,
            clockOutTime = null,
        )

        assertThat(result).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `정상 근무시 일급을 반환한다`() {
        val result = compensationCalculator.calculateDailyEarnings(
            date = DATE,
            salaryType = SalaryInputType.MONTHLY,
            salaryAmount = 3_000_000,
            policy = createPolicy(),
            type = DailyWorkScheduleType.WORK,
            clockInTime = LocalTime.of(9, 0),
            clockOutTime = LocalTime.of(18, 0),
        )

        assertThat(result.toLong()).isEqualTo(142857L)
    }

    @Test
    fun `초과 근무시 일급보다 높은 금액을 반환한다`() {
        val result = compensationCalculator.calculateDailyEarnings(
            date = DATE,
            salaryType = SalaryInputType.MONTHLY,
            salaryAmount = 3_000_000,
            policy = createPolicy(),
            type = DailyWorkScheduleType.WORK,
            clockInTime = LocalTime.of(9, 0),
            clockOutTime = LocalTime.of(19, 0),
        )

        val dailyRate = 3_000_000L / 21
        assertThat(result.toLong()).isGreaterThan(dailyRate)
    }

    @Test
    fun `조기 퇴근시 일급보다 낮은 금액을 반환한다`() {
        val result = compensationCalculator.calculateDailyEarnings(
            date = DATE,
            salaryType = SalaryInputType.MONTHLY,
            salaryAmount = 3_000_000,
            policy = createPolicy(),
            type = DailyWorkScheduleType.WORK,
            clockInTime = LocalTime.of(9, 0),
            clockOutTime = LocalTime.of(17, 0),
        )

        val dailyRate = 3_000_000L / 21
        assertThat(result.toLong()).isLessThan(dailyRate)
    }

    @Test
    fun `출퇴근 시간이 같으면 0원을 반환한다`() {
        val result = compensationCalculator.calculateDailyEarnings(
            date = DATE,
            salaryType = SalaryInputType.MONTHLY,
            salaryAmount = 3_000_000,
            policy = createPolicy(),
            type = DailyWorkScheduleType.WORK,
            clockInTime = LocalTime.of(9, 0),
            clockOutTime = LocalTime.of(9, 0),
        )

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `출퇴근 시간이 null이면 기본 일급을 반환한다`() {
        val result = compensationCalculator.calculateDailyEarnings(
            date = DATE,
            salaryType = SalaryInputType.MONTHLY,
            salaryAmount = 3_000_000,
            policy = createPolicy(),
            type = DailyWorkScheduleType.WORK,
            clockInTime = null,
            clockOutTime = null,
        )

        assertThat(result.toLong()).isEqualTo(142857L)
    }

    @Test
    fun `연봉은 월급으로 환산한 뒤 일급을 계산한다`() {
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
    fun `근무일이 없으면 일급으로 0을 반환한다`() {
        val result = compensationCalculator.calculateDailyRate(
            targetDate = LocalDate.of(2025, 6, 1),
            salaryType = SalaryInputType.MONTHLY,
            salaryAmount = 3_000_000,
            workDays = emptySet(),
        )

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `주3일 근무면 해당 근무일 수로 나누어 일급을 계산한다`() {
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
    fun `일급 계산 결과의 소수점은 반올림한다`() {
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
    fun `월마다 근무일 수가 다르면 일급 계산 결과도 달라진다`() {
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
    fun `실제 근무시간이 정책과 같으면 일급과 동일한 금액을 반환한다`() {
        val dailyRate = BigDecimal("100000")

        val result = compensationCalculator.calculateEarnings(dailyRate, 540, 540)

        assertThat(result).isEqualByComparingTo(BigDecimal("100000"))
    }

    @Test
    fun `초과 근무시 분급 기준으로 증가한 금액을 반환한다`() {
        val dailyRate = BigDecimal("100000")

        val result = compensationCalculator.calculateEarnings(dailyRate, 540, 600)

        assertThat(result).isEqualByComparingTo(BigDecimal("111111"))
    }

    @Test
    fun `조기 퇴근시 분급 기준으로 감소한 금액을 반환한다`() {
        val dailyRate = BigDecimal("100000")

        val result = compensationCalculator.calculateEarnings(dailyRate, 540, 480)

        assertThat(result).isEqualByComparingTo(BigDecimal("88889"))
    }

    @Test
    fun `시작일 포함 종료일 제외 기준으로 근무일 수를 계산한다`() {
        val result = compensationCalculator.getWorkDaysInPeriod(
            start = LocalDate.of(2025, 6, 1),
            end = LocalDate.of(2025, 7, 1),
            workDays = setOf(
                Workday.MON.dayOfWeek,
                Workday.TUE.dayOfWeek,
                Workday.WED.dayOfWeek,
                Workday.THU.dayOfWeek,
                Workday.FRI.dayOfWeek,
            ),
        )

        assertThat(result).isEqualTo(21)
    }

    @Test
    fun `종료일이 근무일이어도 근무일 수 계산에서는 제외한다`() {
        val result = compensationCalculator.getWorkDaysInPeriod(
            start = LocalDate.of(2025, 6, 2),
            end = LocalDate.of(2025, 6, 9),
            workDays = setOf(DayOfWeek.MONDAY),
        )

        assertThat(result).isEqualTo(1)
    }
}
