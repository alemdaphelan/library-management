package com.huit.library.modules.interactive.repository;

import com.huit.library.modules.interactive.entity.UserEventDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventLogMongoRepository extends MongoRepository<UserEventDocument, String> {
}
