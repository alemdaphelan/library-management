package com.huit.library.modules.catalog.repository;

import com.huit.library.modules.catalog.entity.BookDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookElasticRepository extends ElasticsearchRepository<BookDocument, String> {
}
