package com.moa.entity

import jakarta.persistence.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["workPolicyVersionId", "workday"])
    ]
)
class WorkPolicyDayPolicy(
    @Column(nullable = false)
    val workPolicyVersionId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val workday: Workday,

    @Column(nullable = false)
    val clockInTime: LocalTime,

    @Column(nullable = false)
    val clockOutTime: LocalTime,

    @Column(nullable = false)
    val breakStartTime: LocalTime,

    @Column(nullable = false)
    val breakEndTime: LocalTime,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}

enum class Workday(
    val dayOfWeek: DayOfWeek,
) {
    MON(DayOfWeek.MONDAY),
    TUE(DayOfWeek.TUESDAY),
    WED(DayOfWeek.WEDNESDAY),
    THU(DayOfWeek.THURSDAY),
    FRI(DayOfWeek.FRIDAY),
    SAT(DayOfWeek.SATURDAY),
    SUN(DayOfWeek.SUNDAY);

    companion object {

        fun from(date: LocalDate): Workday =
            from(date.dayOfWeek)

        fun from(dayOfWeek: DayOfWeek): Workday =
            entries.first { it.dayOfWeek == dayOfWeek }

        val WEEKDAYS = setOf(MON, TUE, WED, THU, FRI)

        val WEEKENDS = setOf(SAT, SUN)
    }
}
