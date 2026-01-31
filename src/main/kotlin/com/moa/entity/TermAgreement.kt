package com.moa.entity

import jakarta.persistence.*

@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["memberId", "termCode"])
    ]
)
class TermAgreement(
    @Column(nullable = false)
    val memberId: Long,

    @Column(nullable = false)
    val termCode: String,

    @Column(nullable = false)
    var agreed: Boolean,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
