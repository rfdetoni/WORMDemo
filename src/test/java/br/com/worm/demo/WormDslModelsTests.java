package br.com.worm.demo;

import br.com.liviacare.worm.query.FilterBuilder;
import br.com.worm.demo.dto.BookProjection_;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class WormDslModelsTests {

    @Test
    void generatedMetamodelIsAvailableAtCompileTime() {
        assertNotNull(Book_.id);
        assertNotNull(Book_.status);
        assertNotNull(Book_.active);
        assertNotNull(Author_.id);
        assertNotNull(Author_.email);
        assertNotNull(BookProjection_.id);
        assertNotNull(BookProjection_.status);
        assertNotNull(BookProjection_.active);

        assertEquals("id", Book_.id.columnName());
        assertEquals("status", Book_.status.columnName());
        assertEquals("active", Book_.active.columnName());
        assertEquals("status", BookProjection_.status.columnName());
    }

    @Test
    void typedFilterBuilderUsesGeneratedMetamodel() {
        FilterBuilder typed = FilterBuilder.create()
                .eq(Book_.status, "AVAILABLE")
                .eq(Book_.active, true)
                .orderBy(Book_.title, true);

        assertEquals("status = ? AND active = ?", typed.getWhereClause());
        assertEquals(" ORDER BY title ASC", typed.buildOrderBy());
        assertEquals(2, typed.getParameters().size());
        assertEquals("AVAILABLE", typed.getParameters().get(0));
        assertEquals(true, typed.getParameters().get(1));
    }
}
