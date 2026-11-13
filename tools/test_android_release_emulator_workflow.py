"""Guard against reactivecircus/android-emulator-runner running a split command.

The action invokes script lines individually via sh -c. A YAML backslash
continuation does not carry the updater APK arguments to the first command.
"""

import pathlib
import shlex
import unittest


class ReleaseEmulatorInvocationTest(unittest.TestCase):
    def test_real_updater_receives_all_four_arguments_on_one_line(self):
        workflow = pathlib.Path(".github/workflows/continuous-functional-release.yml").read_text()
        emulator_job = workflow.split("\n  android-runtime:", 1)[1].split(
            "\n  android-campaign-runtime:", 1
        )[0]
        calls = [
            line.strip() for line in emulator_job.splitlines()
            if line.lstrip().startswith("bash tools/android_verify_real_update.sh")
        ]
        self.assertEqual(len(calls), 1)
        self.assertEqual(
            shlex.split(calls[0]),
            [
                "bash", "tools/android_verify_real_update.sh",
                "test-output/android", "package/updater-fixture-old.apk",
                "package/updater-verifier-current.apk",
                "package/Catastrophic-Diplomacy-Android.apk",
            ],
        )


if __name__ == "__main__":
    unittest.main()
