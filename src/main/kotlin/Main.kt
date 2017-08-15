package se.zensum.advisorScheduler
import kotlin.js.Math

@JsName("assignApplications")
fun assignApplications(applications: MutableList<Application>, advisors: Collection<Advisor>) {
    reassignApplications(applications, advisors)
    val unassigned = applications.toMutableList()
    Slot.all().forEach {
        it.desiredApplicationsPerAdvisor =
            calcDesiredApplicationsPerAdvisor(it, advisors, unassigned)
    }

    for (app in applications) {
        app.slot.desiredApplicationsPerAdvisor =
            calcDesiredApplicationsPerAdvisor(app.slot, advisors, unassigned)
        val advisor = getAdvisorsHavingSlot(app.slot, advisors).minBy {
            calcSlotScore(app.slot, it)
        }
        if(advisor != null) {
            advisor.applications.add(app)
            unassigned.remove(app)
        }
        app.slot.desiredApplicationsPerAdvisor =
            calcDesiredApplicationsPerAdvisor(app.slot, advisors, unassigned)
    }//TODO: maybe return something if unassigned is not empty
}

fun reassignApplications(applications: MutableList<Application>, advisors: Collection<Advisor>) {
    advisors.forEach { advisor ->
        var apps = applications.filter { app -> app.advisor_id == advisor.id }
        advisor.applications.addAll(apps)
        applications.removeAll(apps)
    }
}



fun hasSlot(advisor: Advisor, slot: Slot) = advisor.slots.any {it.time == slot.time}
fun getAdvisorsHavingSlot(slot: Slot, advisors: Collection<Advisor>) = advisors.filter {hasSlot(it, slot)}

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
    val openApps = countAppsInSlot(slot, unassignedApps)
    if(openApps == 0) return -1f
    val assignableAdvisors = getAdvisorsToAssignTo(slot, advisors,  unassignedApps)
    return (
        openApps + countAppsInSlot(slot, assignableAdvisors)
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

fun calcSlotScore(slot: Slot, advisor: Advisor): Float {
    return countAppsInSlot(slot, advisor) * 1000 + predictAvgBooks(advisor)
}

fun predictAvgBooks(
    advisor: Advisor
):Float {
    return 1 / advisor.slots.size.toFloat() * (
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
    val slots: List<Slot> = listOf<Slot>()
)
data class Application(val id: String = "", var advisor_id: String = "", val slot: Slot)
class Slot private constructor(val time: String) {


    var desiredApplicationsPerAdvisor: Float = 0f
    companion object Factory {
        fun get(time: String): Slot {
            var slot = slots.find { time == it.time }
            if(slot == null) {
                slot = Slot(time)
                slots.add(slot)
            }
            return slot
        }

        fun all():List<Slot> {
            return slots.toList()
        }

        private val slots: MutableList<Slot> = mutableListOf<Slot>()
    }
}
