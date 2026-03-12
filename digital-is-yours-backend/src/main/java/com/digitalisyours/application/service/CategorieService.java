package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.Categorie;
import com.digitalisyours.domain.port.in.CategorieUseCase;
import com.digitalisyours.domain.port.out.CategorieRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CategorieService implements CategorieUseCase {
    private final CategorieRepositoryPort categorieRepository;

    @Override
    public List<Categorie> getAllCategories() {
        return categorieRepository.findAllOrderByOrdre();
    }

    @Override
    public Optional<Categorie> getCategorieById(Long id) {
        return categorieRepository.findById(id);
    }

    @Override
    public Categorie createCategorie(Categorie categorie) {
        if (categorieRepository.existsByNom(categorie.getNom())) {
            throw new RuntimeException("Une catégorie avec ce nom existe déjà");
        }
        return categorieRepository.save(categorie);
    }

    @Override
    public Categorie updateCategorie(Long id, Categorie categorie) {
        Categorie existing = categorieRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Catégorie non trouvée"));

        if (!existing.getNom().equals(categorie.getNom())
                && categorieRepository.existsByNom(categorie.getNom())) {
            throw new RuntimeException("Une catégorie avec ce nom existe déjà");
        }

        categorie.setId(id);
        categorie.setDateCreation(existing.getDateCreation());
        return categorieRepository.save(categorie);
    }

    @Override
    public void deleteCategorie(Long id) {
        if (!categorieRepository.existsById(id)) {
            throw new RuntimeException("Catégorie non trouvée");
        }
        categorieRepository.deleteById(id);
    }
}
