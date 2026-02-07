package com.moa.entity

import jakarta.persistence.*

@Entity
class Profile(
    @Column(nullable = false, unique = true)
    val memberId: Long,

    @Column(nullable = false)
    var nickname: String,

    @Column(nullable = false)
    var workplace: String,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
