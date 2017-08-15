import se.zensum.advisorScheduler.assignApplications
import se.zensum.advisorScheduler.reassignApplications
import se.zensum.advisorScheduler.Advisor
import se.zensum.advisorScheduler.Application
import se.zensum.advisorScheduler.Slot
import se.zensum.advisorScheduler.hasSlot
import se.zensum.advisorScheduler.calcDesiredApplicationsPerAdvisor
import kotlin.test.*

class AdvisorSchedulerTest {
    @Test fun testGettingSlotWithSameTimeReturnsSameObject() {
        var slot1 = Slot.get("10:00")
        var slot2 = Slot.get("10:00")

        assertEquals(slot1, slot2)
    }

    @Test fun testReassignApplications() {
        var app1 = Application("1","4",Slot.get("10:00"))
        var app2 = Application("1", "", Slot.get("10:00"))
        var app3 = Application("1","3",Slot.get("10:00"))
        var app4 = Application("1","4",Slot.get("10:00"))
        var apps = mutableListOf(app1, app2, app3, app4)
        var adv1 = Advisor("3")
        var adv2 = Advisor("4")
        var advisors = listOf(adv1, adv2)

        reassignApplications(apps, advisors)

        assertEquals(apps.size, 1, "apps should have size 1")
        assertTrue(apps.contains(app2), "apps should contain the unassigned app")
        assertEquals(adv1.applications.size, 1, "adv1.applications should have size 1")
        assertTrue(adv1.applications.contains(app3), "adv1.applications should contain app3")
        assertEquals(adv2.applications.size, 2, "adv2.applications should have size 2")
        assertTrue(adv2.applications.containsAll(listOf(app1, app4)), "adv2.applications should contain app1 and app4")
    }


    @Test fun testHasSlot() {
        var slot1 = Slot.get("10:00")
        var slot2 = Slot.get("11:00")
        var slot3 = Slot.get("10:00")

        var advisor = Advisor(id = "1", slots = mutableListOf(slot1))

        assertTrue(hasSlot(advisor, slot1))
        assertTrue(hasSlot(advisor, slot3))
        assertFalse(hasSlot(advisor, slot2))
    }

    @Test fun testCalcDesiredAppsPerAdvisor() {
        var slot = Slot.get("10:00")
        var apps = listOf(
            Application("1", slot=slot),
            Application("2", slot=slot),
            Application("3", slot=slot),
            Application("4", slot=slot),
            Application("5", slot=slot),
            Application("6", slot=slot)
        )
        var slots = mutableListOf(slot)
        var advisors = listOf(
                Advisor("1", mutableListOf(Application("7", slot=slot), Application("8", slot=slot)), slots),
                Advisor("2", mutableListOf(Application("10", slot=slot), Application("11", slot=slot), Application("12", slot=slot), Application("13", slot=slot)), slots),
                Advisor("3", mutableListOf(Application("9", slot=slot)), slots),
                Advisor("4", slots=slots),
                Advisor("5", slots=slots)
            )

        assertEquals(calcDesiredApplicationsPerAdvisor(slot, advisors, apps), (9.0/4.0).toFloat(), "CalcDesiredApps should calculate correctly")
    }

    @Test fun testAssignApplicationsDistributesApplicationsEvenlyOverAdvisors() {
        var slots = listOf(
            Slot.get("10:00"),
            Slot.get("11:00"),
            Slot.get("12:00")
        )

        var advisors = listOf(
            Advisor("1", slots=slots.slice(listOf(0))),
            Advisor("2", slots=slots.slice(listOf(0,1,2))),
            Advisor("3", slots=slots.slice(listOf(1,2))),
            Advisor("4", slots=slots.slice(listOf(2))),
            Advisor("5", slots=slots.slice(listOf(0,1)))
        )

        var apps = mutableListOf(
            Application("1", slot=slots[0]),
            Application("2", slot=slots[0]),
            Application("3", advisor_id="1", slot=slots[0]),
            Application("4", slot=slots[0]),
            Application("5", slot=slots[0]),
            Application("6", advisor_id="5", slot=slots[0]),
            Application("7", advisor_id="5", slot=slots[0]),
            Application("8", slot=slots[0]),
            Application("9", slot=slots[1]),
            Application("10", advisor_id="5", slot=slots[1]),
            Application("11", slot=slots[1]),
            Application("12", slot=slots[1]),
            Application("13", advisor_id="5", slot=slots[1]),
            Application("14", slot=slots[1]),
            Application("15", slot=slots[2]),
            Application("15", slot=slots[2])
        )

        assignApplications(apps, advisors)

        assertEquals(advisors[0].applications.size, 2, "advisor 1 gets 2 apps")
        assertEquals(advisors[1].applications.size, 6, "advisor 2 gets 5 apps")
        assertEquals(advisors[2].applications.size, 3, "advisor 3 gets 3 apps")
        assertEquals(advisors[3].applications.size, 1, "advisor 4 gets 1 app")
        assertEquals(advisors[4].applications.size, 5, "advisor 5 gets 5 app")
    }
}