package com.planbana.backend.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class UserAdminSearchRepositoryImpl implements UserAdminSearchRepository {

    @Autowired
    private MongoTemplate mongo;

    @Override
    public Page<User> searchAdmin(String search, Pageable pageable, String role, User.VerificationStatus status) {
        Query q = new Query();
        List<Criteria> list = new ArrayList<>();

        if (search != null && !search.isBlank()) {
            String regex = ".*" + search + ".*";
            list.add(new Criteria().orOperator(
                    Criteria.where("phone").regex(regex, "i"),
                    Criteria.where("displayName").regex(regex, "i")));
        }

        if (role != null)
            list.add(Criteria.where("roles").in(role));
        if (status != null)
            list.add(Criteria.where("govIdVerificationStatus").is(status));

        if (!list.isEmpty())
            q.addCriteria(new Criteria().andOperator(list.toArray(new Criteria[0])));
        long total = mongo.count(q, User.class);

        q.with(pageable);
        return new PageImpl<>(mongo.find(q, User.class), pageable, total);
    }

    @Override
    public Page<User> findAllAdmin(Pageable pageable, String role, User.VerificationStatus status) {
        Query q = new Query();
        List<Criteria> list = new ArrayList<>();

        if (role != null)
            list.add(Criteria.where("roles").in(role));
        if (status != null)
            list.add(Criteria.where("govIdVerificationStatus").is(status));

        if (!list.isEmpty())
            q.addCriteria(new Criteria().andOperator(list.toArray(new Criteria[0])));
        long total = mongo.count(q, User.class);

        q.with(pageable);
        return new PageImpl<>(mongo.find(q, User.class), pageable, total);
    }
}
