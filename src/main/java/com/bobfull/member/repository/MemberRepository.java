package com.bobfull.member.repository;

import com.bobfull.member.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByBusinessNumber(String businessNumber);

    Optional<Member> findByEmail(String email);
}
