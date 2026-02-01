package com.moa.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalTime

@Entity
class WorkPolicyVersion(
    @Column(nullable = false)
    val memberId: Long,

    @Column(nullable = false)
    var effectiveFrom: LocalDate,

    @Column(nullable = false)
    var clockInTime: LocalTime,

    @Column(nullable = false)
    var clockOutTime: LocalTime,

    @Column(nullable = false)
    var breakStartTime: LocalTime,

    @Column(nullable = false)
    var breakEndTime: LocalTime,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "work_policy_version_workdays",
        joinColumns = [JoinColumn(name = "workPolicyVersionId")]
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "workday", nullable = false)
    var workdays: MutableSet<Workday> = mutableSetOf(),
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
