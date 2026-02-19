package com.moa.entity

import jakarta.persistence.*

@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["token"])])
class FcmToken(
    @Column(nullable = false)
    var memberId: Long,

    @Column(nullable = false)
    val token: String,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
