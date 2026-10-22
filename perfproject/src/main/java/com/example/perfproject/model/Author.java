package com.example.perfproject.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class Author {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;

    // @BatchSize: nếu vẫn lazy-load books thì Hibernate gom 50 author/1 query (IN) thay vì 1 query/author
    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @ToString.Exclude
    private List<Book> books = new ArrayList<>();

    // equals/hashCode chỉ dựa trên id, KHÔNG chạm vào collection books
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Author other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Author(id=" + id + ", name=" + name + ")";
    }
}
