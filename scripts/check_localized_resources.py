#!/usr/bin/env python3
"""Check that supported locales contain every default Android resource key."""

from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"
DEFAULT = ROOT / "values" / "strings.xml"
LOCALES = {
    "Arabic": [ROOT / "values-ar" / "strings.xml"],
    "German": [ROOT / "values-de" / "strings.xml"],
    "Greek": [ROOT / "values-el" / "strings.xml"],
    "Spanish": [ROOT / "values-es" / "strings.xml"],
    "French": [ROOT / "values-fr" / "strings.xml"],
    "Hungarian": [ROOT / "values-hu" / "strings.xml"],
    "Italian": [ROOT / "values-it" / "strings.xml"],
    "Hebrew": [ROOT / "values-iw" / "strings.xml"],
    "Japanese": [ROOT / "values-ja" / "strings.xml"],
    "Korean": [ROOT / "values-ko" / "strings.xml"],
    "Lithuanian": [ROOT / "values-lt" / "strings.xml"],
    "Dutch": [ROOT / "values-nl" / "strings.xml"],
    "Polish": [ROOT / "values-pl" / "strings.xml"],
    "Portuguese": [ROOT / "values-pt" / "strings.xml"],
    "Brazilian Portuguese": [
        ROOT / "values-pt" / "strings.xml",
        ROOT / "values-pt-rBR" / "strings.xml",
    ],
    "Russian": [ROOT / "values-ru" / "strings.xml"],
    "Serbian": [ROOT / "values-sr" / "strings.xml"],
    "Turkish": [ROOT / "values-tr" / "strings.xml"],
    "Simplified Chinese": [ROOT / "values-zh-rCN" / "strings.xml"],
    "Traditional Chinese": [ROOT / "values-zh-rTW" / "strings.xml"],
}


def resources(path: Path):
    root = ET.parse(path).getroot()
    result = {}
    for element in root:
        name = element.attrib.get("name")
        if name:
            kind = element.tag.rsplit("}", 1)[-1]
            result[(kind, name)] = element
    return result


def placeholders(element):
    return re.findall(r"%\d+\$[sd]", "".join(element.itertext()))


default = resources(DEFAULT)
failed = False
for locale, paths in LOCALES.items():
    translated = {}
    for path in paths:
        translated.update(resources(path))
    missing = sorted(set(default) - set(translated))
    wrong_types = sorted(
        key for key in set(default) & set(translated)
        if default[key].tag.rsplit("}", 1)[-1] != translated[key].tag.rsplit("}", 1)[-1]
    )
    placeholder_errors = sorted(
        key for key in set(default) & set(translated)
        if placeholders(default[key]) != placeholders(translated[key])
    )
    if missing or wrong_types or placeholder_errors:
        failed = True
        print(f"{locale} resource validation failed:")
        if missing:
            print("  missing:", ", ".join(f"{kind}:{name}" for kind, name in missing))
        if wrong_types:
            print("  wrong resource type:", ", ".join(f"{kind}:{name}" for kind, name in wrong_types))
        if placeholder_errors:
            print("  placeholder mismatch:", ", ".join(f"{kind}:{name}" for kind, name in placeholder_errors))
    else:
        print(f"{locale}: {len(default)} resources validated")

sys.exit(1 if failed else 0)
