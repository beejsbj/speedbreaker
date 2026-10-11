import Foundation
import FamilyControls
import ManagedSettings
import DeviceActivity

// Provisional identifiers: replace together after the owner registers real IDs.
enum PrototypeIDs {
    static let group = "group.dev.burooj.speedbreaker.ios"
    static let activity = DeviceActivityName("speedbreaker.daily-prototype")
    static let store = ManagedSettingsStore.Name("speedbreaker.prototype")
}

struct PrototypePolicy: Codable {
    enum Mode: String, Codable { case off, immediate, daily }
    var selection = FamilyActivitySelection()
    var mode: Mode = .off
    var startHour = 9
    var endHour = 17

    var hasSelection: Bool {
        !selection.applicationTokens.isEmpty || !selection.categoryTokens.isEmpty ||
        !selection.webDomainTokens.isEmpty
    }

    // Deliberately limited to a same-day window of whole hours (at least one hour).
    func contains(_ date: Date) -> Bool {
        let hour = Calendar.current.component(.hour, from: date)
        return hour >= startHour && hour < endHour
    }
}

enum PrototypeStorage {
    enum Failure: LocalizedError {
        case unavailable
        var errorDescription: String? { "Shared App Group storage unavailable. Check signed entitlements." }
    }
    static func defaults() throws -> UserDefaults {
        guard FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: PrototypeIDs.group) != nil,
              let defaults = UserDefaults(suiteName: PrototypeIDs.group) else { throw Failure.unavailable }
        return defaults
    }
    static func load() throws -> PrototypePolicy {
        guard let data = try defaults().data(forKey: "policy") else { return PrototypePolicy() }
        return try JSONDecoder().decode(PrototypePolicy.self, from: data)
    }
    static func save(_ policy: PrototypePolicy) throws {
        try defaults().set(JSONEncoder().encode(policy), forKey: "policy")
    }
    static func recordCallback(_ value: String) {
        // One overwritten diagnostic, no activity history or selected identity.
        try? defaults().set("\(value) · \(Date().formatted())", forKey: "lastCallback")
    }
    static func lastCallback() -> String {
        (try? defaults().string(forKey: "lastCallback")) ?? "No callback recorded"
    }
}

enum PrototypeShields {
    static var store: ManagedSettingsStore { ManagedSettingsStore(named: PrototypeIDs.store) }
    static func clear() { store.clearAllSettings() }
    static func apply(_ selection: FamilyActivitySelection) {
        let store = store
        store.shield.applications = selection.applicationTokens.isEmpty ? nil : selection.applicationTokens
        store.shield.webDomains = selection.webDomainTokens.isEmpty ? nil : selection.webDomainTokens
        store.shield.applicationCategories = selection.categoryTokens.isEmpty ? nil : .specific(selection.categoryTokens)
        store.shield.webDomainCategories = selection.categoryTokens.isEmpty ? nil : .specific(selection.categoryTokens)
    }
}
