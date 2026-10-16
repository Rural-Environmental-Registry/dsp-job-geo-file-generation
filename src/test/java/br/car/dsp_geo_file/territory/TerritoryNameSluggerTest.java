package br.car.dsp_geo_file.territory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TerritoryNameSluggerTest {

    @ParameterizedTest
    @CsvSource({
            "São Paulo,sao-paulo",
            "Rio de Janeiro,rio-de-janeiro",
            "  Bom Jesus  ,bom-jesus",
            "Açaí/Guaçu,acai-guacu",
            "300m,300m",
            "MAIÚSCULA,maiuscula"
    })
    void slugify_NormalizesAccentsCaseAndSeparators(String name, String expected) {
        assertEquals(expected, TerritoryNameSlugger.slugify(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "---", "///"})
    void slugify_ReturnsNullWithoutUsableCharacters(String name) {
        assertNull(TerritoryNameSlugger.slugify(name));
    }

    @Test
    void slugify_ReturnsNullForNull() {
        assertNull(TerritoryNameSlugger.slugify(null));
    }
}
