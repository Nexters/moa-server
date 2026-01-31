package com.moa.repository

import com.moa.entity.Profile
import org.springframework.data.jpa.repository.JpaRepository

interface ProfileRepository : JpaRepository<Profile, Long>
