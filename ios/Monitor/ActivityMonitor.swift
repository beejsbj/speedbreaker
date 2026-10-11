import DeviceActivity
import FamilyControls

final class ActivityMonitor: DeviceActivityMonitor {
    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        guard activity == PrototypeIDs.activity else { return }
        do {
            let policy = try PrototypeStorage.load()
            guard policy.mode == .daily else { return }
            guard AuthorizationCenter.shared.authorizationStatus == .approved,
                  policy.contains(Date()) else { PrototypeShields.clear(); return }
            PrototypeShields.apply(policy.selection)
            PrototypeStorage.recordCallback("Daily interval start")
        } catch {
            PrototypeShields.clear()
            PrototypeStorage.recordCallback("Start failed; shields cleared")
        }
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        guard activity == PrototypeIDs.activity else { return }
        do {
            guard try PrototypeStorage.load().mode == .daily else { return }
            PrototypeShields.clear()
            PrototypeStorage.recordCallback("Daily interval end")
        } catch { PrototypeShields.clear() }
    }
}
