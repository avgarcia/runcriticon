package com.runcriticon.clubtaxonomia.testing

import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.TestClubs
import io.kotest.assertions.arrow.core.shouldBeRight

/**
 * Builder de [Group] (`testing-de-modulos.md` §8): valores por defecto válidos, nombres de método de negocio,
 * nunca un objeto a medio construir.
 */
class GroupBuilder {
    private var clubId: ClubId = TestClubs.newClub()
    private var name: String = "Grupo de prueba"
    private var requiredTagValueIds: Set<TagValueId> = emptySet()
    private var id: GroupId = GroupId.new()

    fun inClub(clubId: ClubId) = apply { this.clubId = clubId }

    fun named(name: String) = apply { this.name = name }

    fun requiring(tagValueIds: Set<TagValueId>) = apply { this.requiredTagValueIds = tagValueIds }

    fun withId(id: GroupId) = apply { this.id = id }

    fun build(): Group = Group.create(clubId, name, requiredTagValueIds, id).shouldBeRight()
}
