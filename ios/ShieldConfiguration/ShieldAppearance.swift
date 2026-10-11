import ManagedSettings
import ManagedSettingsUI
import UIKit

final class ShieldAppearance: ShieldConfigurationDataSource {
    private var appearance: ShieldConfiguration {
        ShieldConfiguration(
            backgroundBlurStyle: .systemMaterial,
            backgroundColor: .systemBackground,
            icon: UIImage(systemName: "leaf"),
            title: .init(text: "What are you here for?", color: .label),
            subtitle: .init(text: "This target is shielded by the Speedbreaker prototype. To clear shields, return to Speedbreaker yourself.", color: .secondaryLabel),
            primaryButtonLabel: .init(text: "Close target", color: .white),
            primaryButtonBackgroundColor: .systemIndigo)
    }
    override func configuration(shielding application: Application) -> ShieldConfiguration { appearance }
    override func configuration(shielding application: Application, in category: ActivityCategory) -> ShieldConfiguration { appearance }
    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration { appearance }
    override func configuration(shielding webDomain: WebDomain, in category: ActivityCategory) -> ShieldConfiguration { appearance }
}
