package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.ReponseDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReponseDocumentJpaRepository extends JpaRepository<ReponseDocumentEntity, Long> {
    List<ReponseDocumentEntity> findByReponseId(Long reponseId);
    void deleteByReponseId(Long reponseId);
}