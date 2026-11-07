#!/usr/bin/env python3
"""Publish an already-uploaded draft, tolerating ambiguous transient GitHub errors.
Never creates a release, moves a tag, rebuilds, or replaces uploaded assets.
"""
import json
import subprocess
import sys
import time

def publish(tag, repo):
    for delay in (0, 2, 5, 10):
        if delay:
            time.sleep(delay)
        result = subprocess.run(['gh', 'release', 'edit', tag, '-R', repo, '--draft=false'])
        readback = subprocess.run(['gh', 'release', 'view', tag, '-R', repo,
                                   '--json', 'tagName,isDraft'], capture_output=True, text=True)
        if readback.returncode == 0:
            state = json.loads(readback.stdout)
            if state.get('tagName') == tag and state.get('isDraft') is False:
                print('PASS: public release read back after publication')
                return
        print(f'Publication not confirmed (edit status {result.returncode}); retrying boundedly.', flush=True)
    raise SystemExit('BLOCKED: publication could not be confirmed; existing release and tag retained')

if __name__ == '__main__':
    publish(sys.argv[1], sys.argv[2])
