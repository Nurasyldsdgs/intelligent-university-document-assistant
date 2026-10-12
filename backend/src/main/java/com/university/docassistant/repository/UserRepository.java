package com.university.docassistant.repository;

import com.university.docassistant.entity.User;
import com.university.docassistant.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /** Pending teacher role requests: still students, flag set. */
    Page<User> findByTeacherRequestedTrueAndRole(Role role, Pageable pageable);

    boolean existsByEmail(String email);

    Page<User> findByRole(Role role, Pageable pageable);

    /** Email or name contains the pattern (see LikePatterns.contains: lower-case, % and _ escaped). */
    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.email) LIKE :pattern ESCAPE '\\' OR LOWER(COALESCE(u.fullName, '')) LIKE :pattern ESCAPE '\\'
            """)
    Page<User> search(@Param("pattern") String pattern, Pageable pageable);

    @Query("""
            SELECT u FROM User u
            WHERE u.role = :role
              AND (LOWER(u.email) LIKE :pattern ESCAPE '\\' OR LOWER(COALESCE(u.fullName, '')) LIKE :pattern ESCAPE '\\')
            """)
    Page<User> searchByRole(@Param("role") Role role, @Param("pattern") String pattern, Pageable pageable);

    Optional<User> findByResetOtpCode(String otp);
}
