package com.moa.service

import com.moa.common.exception.NotFoundException
import com.moa.entity.WithdrawalRecord
import com.moa.repository.FcmTokenRepository
import com.moa.repository.MemberRepository
import com.moa.repository.WithdrawalRecordRepository
import com.moa.service.dto.MemberResponse
import com.moa.service.dto.WithdrawalRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val fcmTokenRepository: FcmTokenRepository,
    private val withdrawalRecordRepository: WithdrawalRecordRepository,
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

    @Transactional
    fun deleteMember(memberId: Long, req: WithdrawalRequest) {
        val member = memberRepository.findById(memberId)
            .orElseThrow { NotFoundException() }

        // TODO 필요 시 다른 정보들도 삭제
        fcmTokenRepository.findAllByMemberId(memberId)

        req.reason.forEach {
            withdrawalRecordRepository.save(
                WithdrawalRecord(
                    reason = it,
                )
            )
        }

        memberRepository.delete(member)
    }
}
