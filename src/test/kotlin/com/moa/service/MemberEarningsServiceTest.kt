package com.moa.service

import com.moa.entity.*
import com.moa.repository.PayrollVersionRepository
import com.moa.service.calculator.CompensationCalculator
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

@ExtendWith(MockKExtension::class)
class MemberEarningsServiceTest {

    private val payrollVersionRepository: PayrollVersionRepository = mockk()
    private val compensationCalculator = CompensationCalculator()
    private val sut = MemberEarningsService(payrollVersionRepository, compensationCalculator)

    companion object {
        private const val MEMBER_ID = 1L
        private val DATE = LocalDate.of(2025, 6, 9) // Monday
        private val LAST_DAY_OF_MONTH = LocalDate.of(2025, 6, 30)
    }

    private fun createPolicy(
        clockIn: LocalTime = LocalTime.of(9, 0),
        clockOut: LocalTime = LocalTime.of(18, 0),
        workdays: MutableSet<Workday> = mutableSetOf(Workday.MON, Workday.TUE, Workday.WED, Workday.THU, Workday.FRI),
    ) = WorkPolicyVersion(
        memberId = MEMBER_ID,
        effectiveFrom = DATE.minusDays(30),
        clockInTime = clockIn,
        clockOutTime = clockOut,
        workdays = workdays,
    )

    private fun stubPayroll(salaryAmount: Long = 3_000_000) {
        every {
            payrollVersionRepository.findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                MEMBER_ID, LAST_DAY_OF_MONTH,
            )
        } returns PayrollVersion(
            memberId = MEMBER_ID,
            effectiveFrom = DATE.minusDays(30),
            salaryInputType = SalaryInputType.MONTHLY,
            salaryAmount = salaryAmount,
        )
    }

    @Test
    fun `VACATION이면 저장된 시간 기반으로 급여를 계산한다`() {
        stubPayroll()
        val policy = createPolicy()

        val result = sut.calculateDailyEarnings(
            memberId = MEMBER_ID,
            date = DATE,
            policy = policy,
            type = DailyWorkScheduleType.VACATION,
            clockInTime = LocalTime.of(9, 0),
            clockOutTime = LocalTime.of(18, 0),
        )

        Assertions.assertThat(result.toLong()).isEqualTo(142857L)
    }

    @Test
    fun `NONE이면 ZERO를 반환한다`() {
        val result = sut.calculateDailyEarnings(
            memberId = MEMBER_ID,
            date = DATE,
            policy = createPolicy(),
            type = DailyWorkScheduleType.NONE,
            clockInTime = null,
            clockOutTime = null,
        )

        Assertions.assertThat(result).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `PayrollVersion이 없으면 0원을 반환한다`() {
        every {
            payrollVersionRepository.findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                MEMBER_ID, LAST_DAY_OF_MONTH,
            )
        } returns null

        val result = sut.calculateDailyEarnings(
            memberId = MEMBER_ID,
            date = DATE,
            policy = createPolicy(),
            type = DailyWorkScheduleType.WORK,
            clockInTime = null,
            clockOutTime = null,
        )

        Assertions.assertThat(result).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `정상 근무시 일급을 반환한다`() {
        stubPayroll()
        val policy = createPolicy()

        val result = sut.calculateDailyEarnings(
            memberId = MEMBER_ID,
            date = DATE,
            policy = policy,
            type = DailyWorkScheduleType.WORK,
            clockInTime = LocalTime.of(9, 0),
            clockOutTime = LocalTime.of(18, 0),
        )

        Assertions.assertThat(result.toLong()).isEqualTo(142857L)
    }

    @Test
    fun `초과 근무시 일급보다 높은 금액을 반환한다`() {
        stubPayroll()
        val policy = createPolicy()

        val result = sut.calculateDailyEarnings(
            memberId = MEMBER_ID,
            date = DATE,
            policy = policy,
            type = DailyWorkScheduleType.WORK,
            clockInTime = LocalTime.of(9, 0),
            clockOutTime = LocalTime.of(19, 0),
        )

        val dailyRate = 3_000_000L / 21
        Assertions.assertThat(result.toLong()).isGreaterThan(dailyRate)
    }

    @Test
    fun `조기 퇴근시 일급보다 낮은 금액을 반환한다`() {
        stubPayroll()
        val policy = createPolicy()

        val result = sut.calculateDailyEarnings(
            memberId = MEMBER_ID,
            date = DATE,
            policy = policy,
            type = DailyWorkScheduleType.WORK,
            clockInTime = LocalTime.of(9, 0),
            clockOutTime = LocalTime.of(17, 0),
        )

        val dailyRate = 3_000_000L / 21
        Assertions.assertThat(result.toLong()).isLessThan(dailyRate)
    }

    @Test
    fun `출퇴근 시간이 같으면 0원을 반환한다`() {
        stubPayroll()
        val policy = createPolicy()

        val result = sut.calculateDailyEarnings(
            memberId = MEMBER_ID,
            date = DATE,
            policy = policy,
            type = DailyWorkScheduleType.WORK,
            clockInTime = LocalTime.of(9, 0),
            clockOutTime = LocalTime.of(9, 0),
        )

        Assertions.assertThat(result).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `ANNUAL 3,600,000이면 월급 300,000을 반환한다`() {
        every {
            payrollVersionRepository.findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                MEMBER_ID, LAST_DAY_OF_MONTH,
            )
        } returns PayrollVersion(
            memberId = MEMBER_ID,
            effectiveFrom = DATE.minusDays(30),
            salaryInputType = SalaryInputType.ANNUAL,
            salaryAmount = 3_600_000,
        )

        val result = sut.calculateStandardSalary(MEMBER_ID, DATE)

        Assertions.assertThat(result).isEqualByComparingTo(BigDecimal("300000"))
    }

    @Test
    fun `MONTHLY 3,000,000이면 그대로 3,000,000을 반환한다`() {
        stubPayroll(3_000_000)

        val result = sut.calculateStandardSalary(MEMBER_ID, DATE)

        Assertions.assertThat(result).isEqualByComparingTo(BigDecimal("3000000"))
    }

    @Test
    fun `PayrollVersion이 없으면 calculateStandardSalary는 0을 반환한다`() {
        every {
            payrollVersionRepository.findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                MEMBER_ID, LAST_DAY_OF_MONTH,
            )
        } returns null

        val result = sut.calculateStandardSalary(MEMBER_ID, DATE)

        Assertions.assertThat(result).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `calculateStandardMinutes는 정책의 일일 근무시간과 월 근무일 수를 곱한다`() {
        val result = sut.calculateStandardMinutes(
            policy = createPolicy(),
            start = LocalDate.of(2025, 6, 1),
            endInclusive = LocalDate.of(2025, 6, 30),
        )

        // 2025-06 주5일 근무일 21일 * 540분
        Assertions.assertThat(result).isEqualTo(11_340)
    }

    @Test
    fun `calculateWorkedMinutes는 실제 출퇴근 시각을 분 단위로 환산한다`() {
        val result = sut.calculateWorkedMinutes(
            clockInTime = LocalTime.of(22, 0),
            clockOutTime = LocalTime.of(2, 0),
        )

        Assertions.assertThat(result).isEqualTo(240)
    }

    @Test
    fun `출퇴근 시간이 null이면 기본 일급을 반환한다`() {
        stubPayroll()
        val policy = createPolicy()

        val result = sut.calculateDailyEarnings(
            memberId = MEMBER_ID,
            date = DATE,
            policy = policy,
            type = DailyWorkScheduleType.WORK,
            clockInTime = null,
            clockOutTime = null,
        )

        Assertions.assertThat(result.toLong()).isEqualTo(142857L)
    }
}
