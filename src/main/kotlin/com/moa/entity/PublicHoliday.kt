package com.moa.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "public_holiday")
class PublicHoliday(
    @Column(nullable = false)
    val date: LocalDate,

    @Column(nullable = false, length = 50)
    val name: String,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
