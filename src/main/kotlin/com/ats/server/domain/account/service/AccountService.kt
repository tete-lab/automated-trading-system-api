package com.ats.server.domain.account.service

import com.ats.server.domain.account.dto.AccountCreateReq
import com.ats.server.domain.account.dto.AccountRes
import com.ats.server.domain.account.entity.MemberAccount
import com.ats.server.domain.account.repository.MemberAccountRepository
import com.ats.server.domain.member.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AccountService(
    private val accountRepository: MemberAccountRepository,
    private val memberRepository: MemberRepository
) {
    // 계좌 생성
    @Transactional
    fun createAccount(req: AccountCreateReq): Long {
        val member = memberRepository.findById(req.memberId)
            .orElseThrow { IllegalArgumentException("존재하지 않는 회원입니다.") }

        val account = MemberAccount(
            member = member,
            accountNum = req.accountNum,
            accountName = req.accountName,
            apiKey = req.apiKey,
            secretKey = req.secretKey
        )
        return accountRepository.save(account).memberAccountId!!
    }

    // 내 계좌 목록 조회
    fun getMyAccounts(memberId: Long): List<AccountRes> {
        return accountRepository.findAllByMemberMemberId(memberId).map {
            AccountRes(
                accountId = it.memberAccountId!!,
                accountNum = it.accountNum,
                accountName = it.accountName,
                isVirtual = it.isVirtual,
                isActive = it.isActive,
                buyRsi = it.buyRsi,
                sellRsi = it.sellRsi
            )
        }
    }
}