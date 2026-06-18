package org.openjtrace.example.mybatis;

import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "user_info")
public class User {
    private String id;
    private String name;
}
