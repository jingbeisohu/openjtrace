package org.openjtrace.example.mybatis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserMongoService {

    @Autowired
    private UserMongoRepository userMongoRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    public List<User> queryUser(String name) {
        return userMongoRepository.findByName(name);
    }

    public void removeUser(String id) {
        mongoTemplate.remove(id, User.class);
    }
}
