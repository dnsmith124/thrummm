#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Trims the bundled Inter fonts to the characters Thrummm needs, cutting ~80% of their size.
# Missing characters (e.g. a Cyrillic app name) fall back to the system font.
#
# Usage: tools/subset-fonts.sh <dir with full Inter .ttf files>
# Full fonts: https://github.com/rsms/inter/releases (Inter-Light/Regular/SemiBold .ttf),
# saved as inter_light.ttf, inter_regular.ttf, inter_semibold.ttf. Needs fonttools
# (`pip install fonttools`).
set -euo pipefail
src="${1:?pass the directory holding the full Inter .ttf files}"
out="$(dirname "$0")/../app/src/main/res/font"

# Latin, Latin-1 and Latin Extended-A (European app names), general punctuation
# (— … “ ” › etc.), and the ✓ used on selected app rows.
unicodes="U+0020-007E,U+00A0-017F,U+2000-206F,U+20AC,U+2122,U+2190-2193,U+2713"

for f in inter_light inter_regular inter_semibold; do
    pyftsubset "$src/$f.ttf" --unicodes="$unicodes" --layout-features='*' \
        --no-hinting --desubroutinize --output-file="$out/$f.ttf"
    echo "$f: $(wc -c < "$src/$f.ttf") -> $(wc -c < "$out/$f.ttf") bytes"
done
