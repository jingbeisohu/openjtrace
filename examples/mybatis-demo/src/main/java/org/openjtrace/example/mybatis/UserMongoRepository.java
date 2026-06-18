package org.openjtrace.example.mybatis;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface UserMongoRepository extends MongoRepository<User, String> {
    List<User> findByName(String name);
}
