import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

MODULE = Path(__file__).resolve().parent / 'prepare_android_update_manifest.py'
spec = importlib.util.spec_from_file_location('cd_manifest_tool', MODULE)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ManifestTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.apk = Path(self.tmp.name) / 'Catastrophic-Diplomacy-0.1.2-Android.apk'
        self.apk.write_bytes(b'PK\x03\x04' + b'x' * 10000)
    @patch.object(module.shutil,'which',return_value='/mock/sdk')
    @patch.object(module.subprocess,'run')
    def test_signed_release(self, run, which):
        run.return_value = subprocess.CompletedProcess([],0,"package: name='games.catastrophicdiplomacy' versionCode='3' versionName='0.1.2'",'')
        candidate = module.describe(self.apk)
        self.assertEqual(candidate['applicationId'],module.PACKAGE)
        self.assertEqual(candidate['versionCode'],3)
        self.assertEqual(candidate['apk']['bytes'],len(self.apk.read_bytes()))
        self.assertEqual(len(candidate['apk']['sha256']),64)
        self.assertEqual(run.call_count,2)
        self.assertEqual(run.call_args[0][0][:2],['/mock/sdk','verify'])
    @patch.object(module.shutil,'which',return_value='/mock/sdk')
    @patch.object(module.subprocess,'run')
    def test_package_mismatch(self, run, which):
        run.return_value=subprocess.CompletedProcess([],0,"package: name='other.package' versionCode='3' versionName='0.1.2'",'')
        with self.assertRaisesRegex(ValueError,'Wrong package'): module.describe(self.apk)
    @patch.object(module.shutil,'which',return_value='/mock/sdk')
    @patch.object(module.subprocess,'run')
    def test_unsigned_rejected(self, run, which):
        ok=subprocess.CompletedProcess([],0,"package: name='games.catastrophicdiplomacy' versionCode='3' versionName='0.1.2'",'')
        run.side_effect=[ok,subprocess.CalledProcessError(1,'apksigner')]
        with self.assertRaises(subprocess.CalledProcessError):module.describe(self.apk)
    @patch.object(module.shutil,'which',return_value='/mock/sdk')
    @patch.object(module.subprocess,'run')
    def test_prerelease_arbitrary_filename(self, run, which):
        apk = Path(self.tmp.name) / 'nightly-test_build.42.apk'
        apk.write_bytes(self.apk.read_bytes())
        run.return_value = subprocess.CompletedProcess([],0,
            "package: name='games.catastrophicdiplomacy' versionCode='47' versionName='0.1.1-beta.8'",'')
        candidate = module.describe(apk)
        self.assertEqual(candidate['versionCode'],47)
        self.assertEqual(candidate['version'],'0.1.1-beta.8')
        self.assertEqual(candidate['apk']['name'],'nightly-test_build.42.apk')
    def test_bad_filename_rejected(self):
        with patch.object(module.shutil,'which',return_value='/mock/sdk'), \
             patch.object(module.subprocess,'run') as run:
            run.return_value = subprocess.CompletedProcess([],0,
                "package: name='games.catastrophicdiplomacy' versionCode='47' versionName='0.1.1-beta.8'",'')
            apk = Path(self.tmp.name) / 'not-an-apk.zip'
            apk.write_bytes(self.apk.read_bytes())
            with self.assertRaisesRegex(ValueError,'APK filename'):
                module.describe(apk)

    def test_missing_sdk_rejected(self):
        with patch.object(module.shutil,'which',return_value=None):
            with self.assertRaisesRegex(ValueError,'must be installed'):module.describe(self.apk)

if __name__=='__main__':unittest.main()