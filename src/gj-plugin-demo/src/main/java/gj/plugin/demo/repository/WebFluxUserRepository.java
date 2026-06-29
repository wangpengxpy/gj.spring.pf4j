package gj.plugin.demo.repository;

import gj.plugin.demo.entity.WebFluxUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebFluxUserRepository extends JpaRepository<WebFluxUserEntity, Long> {

    List<WebFluxUserEntity> findByNameContainingOrEmailContaining(String nameKeyword, String emailKeyword);
}
