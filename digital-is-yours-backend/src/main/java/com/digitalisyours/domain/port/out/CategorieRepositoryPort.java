package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Categorie;

import java.util.List;
import java.util.Optional;

public interface CategorieRepositoryPort {
    List<Categorie> findAllOrderByOrdre();
    Optional<Categorie> findById(Long id);
    boolean existsById(Long id);
    boolean existsByNom(String nom);
    Categorie save(Categorie categorie);
    void deleteById(Long id);
}
