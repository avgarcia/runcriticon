package com.runcriticon.clubtaxonomia.domain.group

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GroupDetailTest :
    FunSpec({
        val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val group = Group.create(club, "Maratón Valencia avanzado").shouldBeRight()

        fun member(name: String) = GroupMember(PersonId.of(UuidCreator.getTimeOrderedEpoch()), name)

        test("total cuenta los miembros y no las exclusiones") {
            val detail =
                GroupDetail(
                    group = group,
                    members =
                        listOf(
                            GroupMembership(member("Pedro Cordero"), GroupMemberOrigin.FILTER, hasOverride = false),
                            GroupMembership(member("Ana Vila"), GroupMemberOrigin.MANUAL_INCLUSION, hasOverride = true),
                        ),
                    exclusions = listOf(GroupExclusion(member("Marta Lois"), matchesFilter = true)),
                )

            detail.total shouldBe 2
        }

        test("un grupo del que se ha sacado a todo el mundo a mano queda con total cero") {
            val detail =
                GroupDetail(
                    group = group,
                    members = emptyList(),
                    exclusions = listOf(GroupExclusion(member("Marta Lois"), matchesFilter = true)),
                )

            detail.total shouldBe 0
        }
    })
