package com.moa.repository

import com.moa.entity.Term
import org.springframework.data.jpa.repository.JpaRepository

interface TermRepository : JpaRepository<Term, String>
