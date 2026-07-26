package com.runcriticon.clubtaxonomia.infrastructure.persistence

import com.runcriticon.clubtaxonomia.domain.tag.Distance
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Test unitario (sin Spring, sin BD) del converter que persiste [TagValueMetadata] como JSONB. Cubre el round-trip
 * de las dos variantes y la degradación a [TagValueMetadata.Empty] cuando el JSON no deserializa, en vez de
 * propagar la excepción y tumbar la lectura de la fila.
 */
class TagValueMetadataJsonbConverterTest {
    private val converter = TagValueMetadataJsonbConverter()

    @Test
    fun `Empty hace round-trip`() {
        val json = converter.convertToDatabaseColumn(TagValueMetadata.Empty)

        converter.convertToEntityAttribute(json) shouldBe TagValueMetadata.Empty
    }

    @Test
    fun `Race hace round-trip persistiendo la distancia por su code`() {
        val race = TagValueMetadata.Race(date = LocalDate.of(2026, 11, 15), distance = Distance.K42)

        val json = converter.convertToDatabaseColumn(race)

        json shouldContain """"tipo":"Race""""
        json shouldContain """"date":"2026-11-15""""
        json shouldContain """"distance":"42K""""
        converter.convertToEntityAttribute(json) shouldBe race
    }

    @Test
    fun `null se persiste y se lee como Empty`() {
        converter.convertToDatabaseColumn(null) shouldContain """"tipo":"Empty""""
        converter.convertToEntityAttribute(null) shouldBe TagValueMetadata.Empty
    }

    @Test
    fun `JSON corrupto degrada a Empty en vez de propagar la excepcion`() {
        converter.convertToEntityAttribute("{esto no es json") shouldBe TagValueMetadata.Empty
    }

    @Test
    fun `tipo desconocido degrada a Empty en vez de propagar la excepcion`() {
        converter.convertToEntityAttribute("""{"tipo":"NoExiste"}""") shouldBe TagValueMetadata.Empty
    }

    @Test
    fun `distancia desconocida degrada a Empty en vez de propagar la excepcion`() {
        converter.convertToEntityAttribute(
            """{"tipo":"Race","date":"2026-11-15","distance":"100K"}""",
        ) shouldBe TagValueMetadata.Empty
    }
}
