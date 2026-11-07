import subprocess
import unittest
from unittest.mock import patch
import publish_release_with_retry as publisher

class PublishRetryTest(unittest.TestCase):
    @patch.object(publisher.time, 'sleep')
    @patch.object(publisher.subprocess, 'run')
    def test_ambiguous_error_with_public_readback_is_success(self, run, sleep):
        run.side_effect = [subprocess.CompletedProcess([], 1),
                           subprocess.CompletedProcess([], 0, '{"tagName":"test","isDraft":false}')]
        publisher.publish('test', 'owner/repo')
        self.assertEqual(run.call_count, 2)
        sleep.assert_not_called()

    @patch.object(publisher.time, 'sleep')
    @patch.object(publisher.subprocess, 'run')
    def test_persistent_error_is_bounded_and_preserves_existing_release(self, run, sleep):
        run.side_effect = [subprocess.CompletedProcess([], 1),
                           subprocess.CompletedProcess([], 0, '{"tagName":"test","isDraft":true}')] * 4
        with self.assertRaises(SystemExit):
            publisher.publish('test', 'owner/repo')
        self.assertEqual(run.call_count, 8)
        self.assertEqual(sleep.call_count, 3)
        self.assertTrue(all(call.args[0][1:3] in (['release', 'edit'], ['release', 'view'])
                            for call in run.call_args_list))

if __name__ == '__main__':
    unittest.main()
