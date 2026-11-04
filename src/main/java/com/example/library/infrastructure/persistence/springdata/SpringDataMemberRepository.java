package com.example.library.infrastructure.persistence.springdata;

import com.example.library.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SpringDataMemberRepository extends JpaRepository<Member, UUID> {}
