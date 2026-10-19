package com.empresa.sistema.repository;

import com.empresa.sistema.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  java.util.Optional<User> findByEmail(String email);
}
