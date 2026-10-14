package dev.refactorlab.searchlab.repository;

import dev.refactorlab.searchlab.model.ProductRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProductSearchRepository {
    private final JdbcTemplate jdbcTemplate;

    public ProductSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProductRecord> search(String query, int limit) {
        String pattern = "%" + query.toLowerCase() + "%";
        // The database and sample data are initialized automatically at startup.
        return jdbcTemplate.query(
                "SELECT id, name, description, category, base_score " +
                        "FROM products " +
                        "WHERE LOWER(name) LIKE ? OR LOWER(description) LIKE ? " +
                        "ORDER BY base_score DESC LIMIT ?",
                (rs, rowNum) -> new ProductRecord(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("category"),
                        rs.getDouble("base_score")
                ),
                pattern, pattern, limit
        );
    }
}
