package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.Categorie;

import java.util.List;
import java.util.Optional;

public interface CategorieUseCase {
    List<Categorie> getAllCategories();
    Optional<Categorie> getCategorieById(Long id);
    Categorie createCategorie(Categorie categorie);
    Categorie updateCategorie(Long id, Categorie categorie);
    void deleteCategorie(Long id);
}
