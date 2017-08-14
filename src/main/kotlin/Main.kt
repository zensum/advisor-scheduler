package se.zensum.advisorScheduler
import kotlin.js.Math

@JsName("assignApplications")
fun assignApplications(slots: Array<Slot>, applications: MutableList<Application>, advisors: Array<Advisor>) {
    reassignApplications(applications, advisors)
    val unassigned = applications.toMutableList()
    for (app in applications) {
        app.slot.desiredApplicationsPerAdvisor =
            calcDesiredApplicationsPerAdvisor(app.slot, advisors, unassigned)

        val advisor = getAdvisorsHavingSlot(app.slot, advisors).minBy {
            calcSlotScore(app.slot, it, advisors, unassigned)
        }

        advisor.applications.add(app)
        unassigned.remove(app)
    }
}

fun reassignApplications(applications: MutableList<Application>, advisors: Array<Advisor>) {
    advisors.forEach { advisor ->
        var apps = applications.filter { app -> app.advisor_id == advisor.id }
        advisor.applications.addAll(apps)
        applications.removeAll(apps)
    }
}



fun hasSlot(advisor: Advisor, slot: Slot) = advisor.slots.any {it.time == slot.time}
fun getAdvisorsHavingSlot(slot: Slot, advisors: Collection<Advisor>) = advisors.filter {hasSlot(it)}

fun getAppsInSlot(slot: Slot, advisor: Advisor) = getAppsInSlot(slot, advisor.applications)
fun getAppsInSlot(slot: Slot, advisors: Collection<Advisor>) = advisors.flatMap {getAppsInSlot(slot, it.applications)}
fun getAppsInSlot(slot: Slot, applications: Collection<Application>) = applications.filter {it.slot == slot}

fun countAppsInSlot(slot: Slot, advisor: Advisor) = countAppsInSlot(slot, advisor.applications)
fun countAppsInSlot(slot: Slot, advisors: Collection<Advisor>) = advisors.map {countAppsInSlot(slot, it.applications)}.sum()
fun countAppsInSlot(slot: Slot, applications: Collection<Application>) = applications.count {it.slot == slot}


fun calcDesiredApplicationsPerAdvisor(
    slot: Slot,
    advisors: Collection<Advisor>,
    unassignedApps: Collection<Application>
):Float {
    val assignableAdvisors = getAdvisorsToAssignTo(slot, advisors,  unassignedApps)
    return (
        countAppsInSlot(slot, unassignedApps) + countAppsInSlot(slot, assignableAdvisors)
    ).toFloat() / assignableAdvisors.size
}

fun getAdvisorsToAssignTo(
    slot: Slot,
    advisors: Collection<Advisor>,
    unassignedApps: Collection<Application>
):Collection<Advisor> {
    val unassigned = getAppsInSlot(slot, unassignedApps)
    if(unassigned.size == 0) {
        return emptyList()
    }
    val assignableAdvisors = advisors.filter {hasSlot(it, slot)}.toMutableList()
    val assigned = getAppsInSlot(slot, assignableAdvisors)
    var countedAppsInSlot = assigned.size + unassigned.size

    do {
        var advisorsToRemove = assignableAdvisors.filter {
            countAppsInSlot(slot, it) > countedAppsInSlot / assignableAdvisors.size
        }

        assignableAdvisors.removeAll(advisorsToRemove)
        countedAppsInSlot -= countAppsInSlot(slot, advisorsToRemove)
    } while (advisorsToRemove.size > 0)
    return assignableAdvisors
}

fun calcSlotScore(slot: Slot, advisor: Advisor, advisors: Collection<Advisor>, unassignedApps: Collection<Application>) {
    return countAppsInSlot(slot, advisor) * 1000 + predictAvgBooks(advisor, advisors, unassignedApps)
}

fun predictAvgBooks(
    advisor: Advisor,
    advisors: Collection<Advisor>,
    unassignedApps: Collection<Application>
):Float {
    return 1 / advisor.slots.size * (
        advisor.slots.map {slot ->
            Math.max(
                slot.desiredApplicationsPerAdvisor,
                countAppsInSlot(slot, advisor).toFloat()
            )
        } .sum()
    )
}

data class Advisor(
    val id: String,
    val applications: MutableList<Application> = mutableListOf<Application>(),
    val slots: MutableList<Slot> = mutableListOf<Slot>()
)
data class Application(val id: String = "", var advisor_id: String = "", val slot: Slot)
class Slot {
    private constructor(time: String) {
        this.time = time
    }

    val time: String
    var desiredApplicationsPerAdvisor: Float = 0
    companion object Factory {
        fun get(time: String): Slot {
            var slot = slots.find { time == it.time }
            if(slot == null) {
                slot = Slot(time)
                slots.add(slot)
            }
            return slot
        }

        private val slots: MutableList<Slot> = mutableListOf<Slot>()
    }
}
