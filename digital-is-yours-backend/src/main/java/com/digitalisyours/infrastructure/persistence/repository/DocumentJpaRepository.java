package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.List;

@Repository
public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, Long> {
    @Query("SELECT d FROM DocumentEntity d WHERE d.cours.id = :coursId ORDER BY d.dateAjout DESC")
    List<DocumentEntity> findByCoursId(@Param("coursId") Long coursId);

    @Query("SELECT COUNT(d) FROM DocumentEntity d WHERE d.cours.id = :coursId")
    long countByCoursId(@Param("coursId") Long coursId);

    @Query("SELECT COALESCE(SUM(d.taille), 0) FROM DocumentEntity d WHERE d.cours.id = :coursId")
    long sumTailleByCoursId(@Param("coursId") Long coursId);


}
