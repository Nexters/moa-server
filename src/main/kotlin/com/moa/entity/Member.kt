package com.moa.entity

import com.moa.service.auth.oidc.ProviderType
import jakarta.persistence.*

@Entity
class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    val provider: ProviderType,

    val providerSubject: String,

    @OneToOne(fetch = FetchType.LAZY)
    val profile: Profile?,

    ) : BaseEntity() {
}
