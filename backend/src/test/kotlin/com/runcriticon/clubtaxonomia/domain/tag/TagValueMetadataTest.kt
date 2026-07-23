package com.runcriticon.clubtaxonomia.domain.tag

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class TagValueMetadataTest :
    FunSpec({
        test("Race conserva date y distance") {
            val race = TagValueMetadata.Race(LocalDate.of(2026, 12, 6), Distance.K42)
            race.date shouldBe LocalDate.of(2026, 12, 6)
            race.distance shouldBe Distance.K42
        }

        test("Empty es un singleton") {
            (TagValueMetadata.Empty === TagValueMetadata.Empty) shouldBe true
        }

        test("un when sobre la sealed class es exhaustivo sin else") {
            fun describe(metadata: TagValueMetadata): String =
                when (metadata) {
                    is TagValueMetadata.Empty -> "empty"
                    is TagValueMetadata.Race -> "race"
                }
            describe(TagValueMetadata.Empty) shouldBe "empty"
            describe(TagValueMetadata.Race(LocalDate.of(2026, 1, 1), Distance.K5)) shouldBe "race"
        }
    })
