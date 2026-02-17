package com.moa.service

import com.moa.common.exception.NotFoundException
import com.moa.repository.MemberRepository
import com.moa.service.dto.MemberResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MemberService(
    private val memberRepository: MemberRepository,
) {

    @Transactional(readOnly = true)
    fun getMember(memberId: Long): MemberResponse {
        val member = memberRepository.findById(memberId)
            .orElseThrow { NotFoundException() }

        return MemberResponse(
            id = member.id,
            provider = member.provider,
        )
    }
}
