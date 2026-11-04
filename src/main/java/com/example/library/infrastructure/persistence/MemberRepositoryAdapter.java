package com.example.library.infrastructure.persistence;

import com.example.library.application.port.MemberRepository;
import com.example.library.domain.Member;
import com.example.library.infrastructure.persistence.springdata.SpringDataMemberRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MemberRepositoryAdapter implements MemberRepository {
    private final SpringDataMemberRepository repository;
    public MemberRepositoryAdapter(SpringDataMemberRepository repository) { this.repository = repository; }
    @Override public Member save(Member member) { return repository.save(member); }
    @Override public Optional<Member> findById(UUID id) { return repository.findById(id); }
}
