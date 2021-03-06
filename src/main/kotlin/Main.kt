package se.zensum.advisorScheduler
import kotlin.js.Math
import kotlin.js.Date
external fun require(module:String):dynamic


//wrapper that initializes the slots in a way that allows for the singleton-ish (anti)pattern
//that is used to remove at least some of all the redundant calculations
@JsName("assignApplications")
fun jsAssignApplications(
        slots: Array<String>,
        applications: Array<JsApplication>,
        advisors: Array<JsAdvisor>
    ):Array<JsAdvisor> {

    Slot.setSlots(slots)

    val kotlinApplications = applications.map {
            Application(it)
        }

    val kotlinAdvisors = advisors.map {
            Advisor(it)
        }

    kotlinAdvisors.forEach {
        it.applications.addAll(kotlinApplications.filter {app -> it.id == app.advisor_id })
    }

    return assignApplications(
        kotlinApplications.filter {app -> app.advisor_id.isNullOrEmpty() || !kotlinAdvisors.any { it -> it.id == app.advisor_id } },
        kotlinAdvisors
    ) .map {
        it.toJsAdvisor()
    } .toTypedArray()
}

data class JsAdvisor (
    val id: String,
    val firstName: String = "",
    val lastName: String = "",
    var applications: Array<JsApplication> = emptyArray(),
    val slots: Array<String> = emptyArray()
)

data class JsApplication (
    val id: String,
    val advisor_id: String = "",
    val return_at: String = "",
    val desiredLoan: Int = 0
)

fun assignApplications(
    applicationsToAssign: Collection<Application>,
    advisors: Collection<Advisor>
): Collection<Advisor> {
    val unassigned = applicationsToAssign.toMutableList()
    Slot.all().forEach {
        it.desiredApplicationsPerAdvisor =
            calcDesiredApplicationsPerAdvisor(it, advisors, unassigned)
    }

    for (app in applicationsToAssign) {
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
    }
    return advisors
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

class Advisor constructor (
    val id: String,
    val firstName: String = "",
    val lastName: String = "",
    val applications: MutableList<Application> = mutableListOf<Application>(),
    val slots: List<Slot> = emptyList()
) {
    constructor(advisor: JsAdvisor): this(
        advisor.id,
        advisor.firstName,
        advisor.lastName,
        if (advisor.applications == null) mutableListOf<Application>()
        else advisor.applications.map { Application(it) }.toMutableList(),
        if (advisor.slots == null) emptyList() else  advisor.slots.map { Slot.get(it) }
    )
    fun toJsAdvisor() = JsAdvisor(
            id,
            firstName,
            lastName,
            applications.map { it.toJsApplication() }.toTypedArray(),
            slots.map { it.time }.toTypedArray()
        )
}
class Application constructor(
    val id: String = "",
    var advisor_id: String = "",
    val slot: Slot,
    val desiredLoan: Int = 0
) {
    constructor(app: JsApplication): this(app.id, app.advisor_id, Slot.get(app.return_at), app.desiredLoan)

    fun toJsApplication() = JsApplication(
        id,
        advisor_id,
        slot.time,
        desiredLoan
    )
}

class Slot private constructor(val time: String, val durationInMinutes: Int) {
    var desiredApplicationsPerAdvisor: Float = 0f
    companion object Factory {
        fun get(time: String): Slot {
            var slot = slots.find {
                time == it.time ||
                moment(time).add(1, "ms").isBetween(
                    moment(it.time, "YYYY-MM-DD hh:mm"),
                    moment(it.time, "YYYY-MM-DD hh:mm").add(it.durationInMinutes, "minutes")
                )
            }
            if(slot == null) {
                return nullSlot
            } else {
                return slot
            }

        }

        fun all():List<Slot> {
            return slots.toList()
        }

        fun setSlots(slots: Array<String>) {
            Slot.slots = slots.map {Slot(it, 15)}.toList()
        }
        private var nullSlot: Slot = Slot("not found", 0)
        private var slots: List<Slot> = emptyList()
        private val moment = require("moment")
    }
}


