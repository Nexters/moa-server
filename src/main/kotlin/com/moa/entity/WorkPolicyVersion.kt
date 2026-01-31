package com.moa.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
class WorkPolicyVersion(
    @Column(nullable = false)
    val memberId: Long,

    @Column(nullable = false)
    var effectiveFrom: LocalDate,

    ) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
