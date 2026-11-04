package com.example.library.application.port;

import com.example.library.domain.Member;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository {
    Member save(Member member);
    Optional<Member> findById(UUID id);
}
