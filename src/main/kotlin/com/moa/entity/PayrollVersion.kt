package com.moa.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
class PayrollVersion(
    @Column(nullable = false)
    val memberId: Long,

    @Column(nullable = false)
    var effectiveFrom: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var salaryInputType: SalaryInputType,

    @Column(nullable = false)
    var salaryAmount: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}

enum class SalaryInputType {
    ANNUAL, MONTHLY
}
