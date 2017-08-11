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
        var advisors = arrayOf(adv1, adv2)

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
}
