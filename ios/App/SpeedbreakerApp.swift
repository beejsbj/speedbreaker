import SwiftUI
import FamilyControls
import DeviceActivity

@main
struct SpeedbreakerApp: App {
    var body: some Scene { WindowGroup { PrototypeView() } }
}

@MainActor
final class PrototypeModel: ObservableObject {
    @Published var policy = PrototypePolicy()
    @Published var status = "No operation requested"
    @Published var callback = "No callback recorded"
    @Published var authorization = AuthorizationCenter.shared.authorizationStatus
    @Published var registered = false
    @Published var busy = false

    init() { refresh() }

    func refresh() {
        authorization = AuthorizationCenter.shared.authorizationStatus
        registered = DeviceActivityCenter().activities.contains(PrototypeIDs.activity)
        callback = PrototypeStorage.lastCallback()
        do { policy = try PrototypeStorage.load() }
        catch { status = error.localizedDescription }
        if authorization != .approved { stop() }
    }

    func authorize() async {
        busy = true
        defer { busy = false }
        do {
            try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
            status = "Authorization request completed"
        } catch { status = "Authorization failed: \(error.localizedDescription)" }
        refresh()
    }

    func select(_ selection: FamilyActivitySelection) {
        // Changing targets stops the previous experiment before persisting new tokens.
        stop()
        policy.selection = selection
        do { try PrototypeStorage.save(policy); status = "Selection saved locally; shielding off" }
        catch { status = error.localizedDescription }
    }

    func start(_ mode: PrototypePolicy.Mode) {
        guard AuthorizationCenter.shared.authorizationStatus == .approved, policy.hasSelection else {
            status = "Approve Screen Time access and choose targets first"
            return
        }
        guard policy.startHour < policy.endHour else {
            status = "Daily window must end after it starts"
            return
        }
        stop()
        policy.mode = mode
        do {
            try PrototypeStorage.save(policy)
            if mode == .daily {
                try DeviceActivityCenter().startMonitoring(PrototypeIDs.activity, during: DeviceActivitySchedule(
                    intervalStart: DateComponents(hour: policy.startHour, minute: 0),
                    intervalEnd: DateComponents(hour: policy.endHour, minute: 0), repeats: true))
            }
            if mode == .immediate || policy.contains(Date()) { PrototypeShields.apply(policy.selection) }
            registered = DeviceActivityCenter().activities.contains(PrototypeIDs.activity)
            status = "\(mode.rawValue.capitalized) policy requested. Check the selected target on this iPhone."
        } catch {
            let failure = error.localizedDescription
            stop()
            status = "Setup failed; shielding cleared: \(failure)"
        }
    }

    func stop() {
        policy.mode = .off
        // Publish off before stopping monitoring so late callbacks normally see off.
        do { try PrototypeStorage.save(policy) }
        catch { status = "Cleared shields; could not save off state: \(error.localizedDescription)" }
        DeviceActivityCenter().stopMonitoring([PrototypeIDs.activity])
        PrototypeShields.clear()
        registered = false
    }

    func reset() {
        stop()
        policy = PrototypePolicy()
        do {
            try PrototypeStorage.save(policy)
            try PrototypeStorage.defaults().removeObject(forKey: "lastCallback")
            callback = "No callback recorded"
            status = "Selection and diagnostic erased; shields cleared"
        } catch { status = error.localizedDescription }
    }
}

struct PrototypeView: View {
    @StateObject private var model = PrototypeModel()
    @Environment(\.scenePhase) private var phase
    @State private var picker = false
    @State private var selection = FamilyActivitySelection()

    private var authorizationLabel: String {
        switch model.authorization {
        case .approved: return "Approved"
        case .denied: return "Denied — shielding inactive"
        case .notDetermined: return "Not requested — shielding inactive"
        @unknown default: return "Unknown — shielding inactive"
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Feasibility prototype") {
                    Text("System shields for chosen apps, categories and websites. This is not the Android breathing intervention.")
                    Text("No opening detection, countdown lock, continuous-use timer or redirects.").foregroundStyle(.secondary)
                }
                Section("1 · Screen Time access") {
                    Text(authorizationLabel)
                    Button("Request individual authorization") { Task { await model.authorize() } }
                        .disabled(model.busy)
                    Text("You can revoke access in iOS Settings. Individual access does not prevent deleting this app.")
                }
                Section("2 · Opaque selection") {
                    Button("Choose with Apple's picker") { selection = model.policy.selection; picker = true }
                        .disabled(model.authorization != .approved)
                    Text("\(model.policy.selection.applicationTokens.count) apps · \(model.policy.selection.categoryTokens.count) categories · \(model.policy.selection.webDomainTokens.count) websites")
                    Text("Categories can shield many apps. Choose a disposable test target; avoid anything you need for safety.")
                }
                Section("3 · Shield experiment") {
                    Stepper("Daily start: \(model.policy.startHour):00", value: $model.policy.startHour, in: 0...22)
                    Stepper("Daily end: \(model.policy.endHour):00", value: $model.policy.endHour, in: 1...23)
                    Button("Shield selection now") { model.start(.immediate) }
                        .disabled(model.authorization != .approved || !model.policy.hasSelection)
                    Button("Register daily window") { model.start(.daily) }
                        .disabled(model.authorization != .approved || !model.policy.hasSelection)
                    Text("Register again to apply edited hours. Scheduled callbacks are system driven; timing is not a deadline guarantee.")
                    Button("Stop monitoring and clear shields", role: .destructive) {
                        model.stop()
                    }
                    Text("Requested mode: \(model.policy.mode.rawValue)")
                    Text("Monitor registration: \(model.registered ? "present" : "absent")")
                }
                Section("Diagnostics") {
                    Text(model.status)
                    Text(model.callback)
                    Button("Refresh authorization and diagnostic") { model.refresh() }
                    Text("Registration and requested state do not prove that a target is shielded.")
                    Button("Erase local selection and diagnostic", role: .destructive) { model.reset() }
                }
            }
            .navigationTitle("Speedbreaker · iOS")
            .sheet(isPresented: $picker, onDismiss: { model.select(selection) }) {
                NavigationStack {
                    FamilyActivityPicker(selection: $selection)
                        .navigationTitle("Test targets")
                        .toolbar { Button("Done") { picker = false } }
                }
            }
            .onChange(of: phase) { newPhase in if newPhase == .active { model.refresh() } }
        }
    }
}
