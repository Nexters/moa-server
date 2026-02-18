package com.moa.entity

import jakarta.persistence.*

@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["osType"])])
class AppVersion(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val osType: OsType,

    @Column(nullable = false)
    var latestVersion: String,

    @Column(nullable = false)
    var minimumVersion: String,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
