package com.moa.service

import com.moa.common.exception.BadRequestException
import com.moa.common.exception.ErrorCode
import com.moa.entity.Term
import com.moa.entity.TermAgreement
import com.moa.repository.TermAgreementRepository
import com.moa.repository.TermRepository
import com.moa.service.dto.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TermsService(
    private val termRepository: TermRepository,
    private val termAgreementRepository: TermAgreementRepository,
) {

    @Transactional(readOnly = true)
    fun getTerms(): TermsResponse {
        val terms = termRepository.findAll()
            .sortedWith(compareByDescending<Term> { it.required }.thenBy { it.code })
            .map {
                TermDto(
                    code = it.code,
                    title = it.title,
                    required = it.required,
                    contentUrl = it.contentUrl,
                )
            }

        return TermsResponse(terms = terms)
    }

    @Transactional
    fun getAgreements(memberId: Long): TermsAgreementsResponse {
        val terms = termRepository.findAll()
        val requiredCodes = terms.filter { it.required }.map { it.code }.toSet()

        val existingAgreements = termAgreementRepository.findAllByMemberId(memberId)
            .associateBy { it.termCode }

        val responseAgreements = terms
            .sortedWith(compareByDescending<Term> { it.required }.thenBy { it.code })
            .map { term ->
                TermAgreementDto(
                    code = term.code,
                    agreed = existingAgreements[term.code]?.agreed == true,
                )
            }

        val hasRequiredTermsAgreed = requiredCodes.all { existingAgreements[it]?.agreed == true }

        return TermsAgreementsResponse(
            agreements = responseAgreements,
            hasRequiredTermsAgreed = hasRequiredTermsAgreed,
        )
    }

    @Transactional
    fun upsertAgreements(memberId: Long, req: TermsAgreementRequest): TermsAgreementsResponse {
        val terms = termRepository.findAll()
        val termByCode = terms.associateBy { it.code }
        val requiredCodes = terms.filter { it.required }.map { it.code }.toSet()

        val requestMap = req.agreements.associateBy { it.code }

        req.agreements.forEach { a ->
            if (!termByCode.containsKey(a.code)) {
                throw BadRequestException(ErrorCode.BAD_REQUEST)
            }
        }

        val missingRequired = requiredCodes.any { !requestMap.containsKey(it) }
        val hasRequiredFalse = requiredCodes.any { requestMap[it]?.agreed == false }

        if (missingRequired || hasRequiredFalse) {
            throw BadRequestException(ErrorCode.REQUIRED_TERMS_MUST_BE_AGREED)
        }

        req.agreements.forEach { a ->
            val existing = termAgreementRepository.findByMemberIdAndTermCode(memberId, a.code)
            if (existing != null) {
                existing.agreed = a.agreed
            } else {
                termAgreementRepository.save(
                    TermAgreement(
                        memberId = memberId,
                        termCode = a.code,
                        agreed = a.agreed,
                    )
                )
            }
        }

        return getAgreements(memberId)
    }
}
