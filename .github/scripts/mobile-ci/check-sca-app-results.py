#!/usr/bin/env python3
"""Reject empty, skipped or incomplete unattended payment acceptance runs."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root, expected = Path(sys.argv[1]), int(sys.argv[2])
cases = [case for path in root.rglob('*.xml') for case in ET.parse(path).iter('testcase')
         if 'ScaPaymentAppE2ETest' in case.get('classname', '')]
assert len(cases) == expected, f'Expected {expected} SCA app tests, got {len(cases)}'
assert not any(any(case.find(tag) is not None for tag in ('skipped', 'failure', 'error')) for case in cases), \
    'SCA app tests must all execute and pass; missing backend deployment is a failed precondition'
print(f'{len(cases)} unattended SCA app tests passed; authentication was simulated')
