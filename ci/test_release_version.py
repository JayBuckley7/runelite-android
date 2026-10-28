import unittest

from verify_release_version import verify


class ReleaseVersionTest(unittest.TestCase):
    metadata = {"tag": "1.0.56_1.13.4", "versionCode": 1000056}
    badging = "package: name='net.runelite.mp' versionCode='1000056' versionName='1.0.56_1.13.4'\n"
    build_config = 'public static final String RELEASE_TAG = "1.0.56_1.13.4";'

    def test_matching_release(self):
        verify(self.metadata, self.badging, self.build_config)

    def test_rejects_runelite_only_version_name(self):
        with self.assertRaisesRegex(ValueError, "APK versionName"):
            verify(self.metadata, self.badging.replace("1.0.56_1.13.4", "1.13.4"), self.build_config)

    def test_rejects_stale_version_code(self):
        with self.assertRaisesRegex(ValueError, "APK versionCode"):
            verify(self.metadata, self.badging.replace("1000056", "1000055"), self.build_config)

    def test_rejects_wrong_package(self):
        with self.assertRaisesRegex(ValueError, "APK name"):
            verify(self.metadata, self.badging.replace("net.runelite.mp", "other.app"), self.build_config)

    def test_rejects_stale_updater_tag(self):
        with self.assertRaisesRegex(ValueError, "RELEASE_TAG"):
            verify(self.metadata, self.badging, self.build_config.replace("1.0.56", "1.0.55"))

    def test_rejects_missing_metadata(self):
        with self.assertRaisesRegex(ValueError, "metadata is missing"):
            verify(self.metadata, "", self.build_config)
        with self.assertRaisesRegex(ValueError, "RELEASE_TAG"):
            verify(self.metadata, self.badging, "")


if __name__ == "__main__":
    unittest.main()
