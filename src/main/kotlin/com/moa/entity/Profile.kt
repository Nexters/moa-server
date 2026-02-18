package com.moa.entity

import jakarta.persistence.*

@Entity
class Profile(
    @Column(nullable = false, unique = true)
    val memberId: Long,

    @Column(nullable = false)
    var nickname: String,

    @Column(nullable = true)
    var workplace: String? = null,

    @Column(nullable = false)
    var paydayDay: Int = 25,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
