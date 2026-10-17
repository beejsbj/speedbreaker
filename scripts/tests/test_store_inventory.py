"""Per-channel final-manifest policy: only the GitHub build may reach the network or install updates."""
import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location('store_inventory', Path(__file__).parents[1] / 'store_inventory.py')
inventory = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(inventory)

OFFLINE = sorted(inventory.OFFLINE_PERMISSIONS)
INTERNET = 'android.permission.INTERNET'
INSTALL = 'android.permission.REQUEST_INSTALL_PACKAGES'


class ChannelPermissionPolicyTest(unittest.TestCase):
    def violations(self, channel, *extra, without=()):
        return inventory.permission_violations(channel, [p for p in OFFLINE if p not in without] + list(extra))

    def test_store_builds_accept_exactly_the_offline_set(self):
        for channel in ('community', 'play'):
            with self.subTest(channel=channel):
                self.assertEqual(self.violations(channel), [])

    def test_store_builds_reject_network_and_self_install(self):
        for channel in ('community', 'play'):
            with self.subTest(channel=channel):
                self.assertIn('INTERNET permission present', self.violations(channel, INTERNET))
                self.assertIn('Install-packages permission present in a store build', self.violations(channel, INSTALL))
                self.assertIn('Network-state permission present',
                              self.violations(channel, 'android.permission.ACCESS_NETWORK_STATE'))

    def test_github_build_allows_exactly_the_updater_permissions(self):
        self.assertEqual(self.violations('github', INTERNET, INSTALL), [])

    def test_github_build_allows_nothing_else_new(self):
        for extra in ('android.permission.ACCESS_NETWORK_STATE', 'com.android.vending.BILLING',
                      'android.permission.ACCESS_WIFI_STATE', 'android.permission.READ_CONTACTS'):
            with self.subTest(extra=extra):
                self.assertTrue(self.violations('github', INTERNET, INSTALL, extra))

    def test_github_build_must_carry_its_updater(self):
        self.assertEqual(self.violations('github', INTERNET),
                         [f'expected permission missing: {INSTALL}'])
        self.assertEqual(self.violations('github'), [f'expected permission missing: {INTERNET}',
                                                     f'expected permission missing: {INSTALL}'])

    def test_unknown_permissions_are_rejected_everywhere(self):
        for channel in inventory.CHANNEL_PERMISSIONS:
            with self.subTest(channel=channel):
                self.assertIn('unexpected permission android.permission.CAMERA',
                              self.violations(channel, 'android.permission.CAMERA'))

    def test_dropping_an_offline_permission_is_not_a_violation(self):
        self.assertEqual(self.violations('community', without=('android.permission.RECEIVE_BOOT_COMPLETED',)), [])


if __name__ == '__main__':
    unittest.main()
