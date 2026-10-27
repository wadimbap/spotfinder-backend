package com.spotfinder.spot.repository;

import com.spotfinder.spot.entity.SpotEntity;
import com.spotfinder.spot.entity.SpotFeature;
import com.spotfinder.spot.entity.SpotType;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

public final class SpotSpecifications {

    private SpotSpecifications() {
    }

    public static Specification<SpotEntity> approvedMatching(
            String query,
            Collection<SpotType> types,
            Collection<SpotFeature> features
    ) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isTrue(root.get("approved")));

            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern));
            }

            if (types != null && !types.isEmpty()) {
                predicates.add(root.get("type").in(types));
            }

            if (features != null && !features.isEmpty()) {
                Expression<Set<SpotFeature>> spotFeatures = root.<Set<SpotFeature>>get("features");
                features.forEach(feature -> predicates.add(criteriaBuilder.isMember(feature, spotFeatures)));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
