package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.Categorie;
import com.digitalisyours.domain.port.out.CategorieRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.CategorieEntity;
import com.digitalisyours.infrastructure.persistence.repository.CategorieJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CategorieRepositoryAdapter implements CategorieRepositoryPort {
    private final CategorieJpaRepository jpaRepository;

    @Override
    public List<Categorie> findAllOrderByOrdre() {
        return jpaRepository.findAllByOrderByOrdreAffichageAsc()
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<Categorie> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public boolean existsByNom(String nom) {
        return jpaRepository.existsByNom(nom);
    }

    @Override
    public Categorie save(Categorie categorie) {
        CategorieEntity entity = toEntity(categorie);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    // ── Mapping ──────────────────────────────────────────────

    private Categorie toDomain(CategorieEntity e) {
        return Categorie.builder()
                .id(e.getId())
                .nom(e.getNom())
                .description(e.getDescription())
                .couleur(e.getCouleur())
                .imageCouverture(e.getImageCouverture())
                .metaDescription(e.getMetaDescription())
                .ordreAffichage(e.getOrdreAffichage())
                .visibleCatalogue(e.getVisibleCatalogue())
                .dateCreation(e.getDateCreation())
                .build();
    }

    private CategorieEntity toEntity(Categorie d) {
        return CategorieEntity.builder()
                .id(d.getId())
                .nom(d.getNom())
                .description(d.getDescription())
                .couleur(d.getCouleur())
                .imageCouverture(d.getImageCouverture())
                .metaDescription(d.getMetaDescription())
                .ordreAffichage(d.getOrdreAffichage())
                .visibleCatalogue(d.getVisibleCatalogue())
                .dateCreation(d.getDateCreation())
                .build();
    }
}
