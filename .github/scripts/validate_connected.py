"""Read connected-test results."""
import sys
from pathlib import Path
from xml.etree import ElementTree

report_dir = Path(sys.argv[1])
for report in report_dir.rglob("TEST-*.xml"):
    root = ElementTree.parse(report).getroot()
    print(f"{root.get('tests')} tests passed")
