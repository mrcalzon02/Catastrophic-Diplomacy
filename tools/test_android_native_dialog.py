#!/usr/bin/env python3
"""Host-only regression for signed APK startup-dialog recognition."""
import unittest
from android_native_dialog import dismiss_point


class UpdaterDialogTest(unittest.TestCase):
    def test_real_native_updater_dialog(self):
        xml = '''<hierarchy><node text="Available GitHub APK releases" />
        <node text="LATER" class="android.widget.Button"
          resource-id="android:id/button2" bounds="[1120,560][1250,620]" />
        </hierarchy>'''
        self.assertEqual((1185, 590), dismiss_point(xml))

    def test_normal_game_surface_never_receives_tap(self):
        self.assertIsNone(dismiss_point('<hierarchy><node text="" class="android.view.SurfaceView" /></hierarchy>'))

    def test_other_permission_dialog_never_receives_tap(self):
        xml = '<hierarchy><node text="Allow installation?"/><node text="LATER" class="android.widget.Button" bounds="[1,2][3,4]" /></hierarchy>'
        self.assertIsNone(dismiss_point(xml))

    def test_missing_button_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "Later button"):
            dismiss_point('<hierarchy><node text="Available GitHub APK releases" /></hierarchy>')

    def test_ambiguous_button_fails_closed(self):
        xml = '<hierarchy><node text="Available GitHub APK releases" />' + '<node text="LATER" class="android.widget.Button" bounds="[1,2][3,4]" />' * 2 + '</hierarchy>'
        with self.assertRaisesRegex(ValueError, "not unique"):
            dismiss_point(xml)

    def test_invalid_bounds_fails_closed(self):
        xml = '<hierarchy><node text="Available GitHub APK releases" /><node text="LATER" class="android.widget.Button" bounds="[2,2][1,4]" /></hierarchy>'
        with self.assertRaisesRegex(ValueError, "empty"):
            dismiss_point(xml)


if __name__ == '__main__':
    unittest.main()
