package com.moa.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
class Term(
    @Id
    val code: String,

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val required: Boolean,

    @Column(nullable = false)
    val contentUrl: String,
)
