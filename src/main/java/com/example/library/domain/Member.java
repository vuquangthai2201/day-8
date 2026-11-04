package com.example.library.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "members")
public class Member {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String fullName;
    @Column(nullable = false, unique = true)
    private String email;

    protected Member() {}

    public Member(String fullName, String email) {
        this.id = UUID.randomUUID();
        this.fullName = fullName;
        this.email = email;
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
}
