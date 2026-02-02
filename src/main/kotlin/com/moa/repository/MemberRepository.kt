package com.moa.repository

import com.moa.entity.Member
import com.moa.service.auth.oidc.ProviderType
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long> {
    fun findByProviderAndProviderSubject(provider: ProviderType, providerSubject: String): Member?
}
