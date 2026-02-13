package com.moa.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalTime

@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["memberId", "date"])])
class DailyWorkSchedule(
    @Column(nullable = false)
    val memberId: Long,

    @Column(nullable = false)
    val date: LocalDate,

    @Column(nullable = false)
    var clockInTime: LocalTime,

    @Column(nullable = false)
    var clockOutTime: LocalTime,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
