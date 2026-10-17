"""Report real visible-pixel duration without changing the locked shutter gates."""
import csv
import json
from pathlib import Path
import sys


def analyze(rows):
    normal = [row for row in rows if 350 <= float(row['start_ms']) <= 500]
    report = {'origin': 'first_visible_interior_pixel_frame',
              'product_origin': 'native_packet_deadline_minus_200ms',
              'first_visible_product_ms': None, 'visible_clear_ms': None,
              'visible_result': 'FAIL', 'acceptance': 'original_gates_still_required'}
    if len(normal) < 3:
        return report | {'reason': 'No stable post-flash reference'}, []
    reference = [sum(float(row[c]) for row in normal) / len(normal) for c in ('r', 'g', 'b')]
    denominator = sum((255 - c) ** 2 for c in reference)
    if denominator <= 1000:
        return report | {'reason': 'Reference scene too white'}, []
    values = []
    for row in rows:
        alpha = sum((float(row[c]) - reference[i]) * (255 - reference[i])
                    for i, c in enumerate(('r', 'g', 'b'))) / denominator
        values.append({**row, 'estimated_alpha': alpha})
    visible = [row for row in values
               if 0 <= float(row['start_ms']) <= 200 and row['estimated_alpha'] >= .08]
    if not visible:
        return report | {'reason': 'No real visible flash; no timing origin assigned'}, values
    origin = float(visible[0]['start_ms'])
    for row in values:
        row['visible_start_ms'] = float(row['start_ms']) - origin
        row['visible_end_ms'] = float(row['end_ms']) - origin
    report['first_visible_product_ms'] = origin
    strong = any(row['estimated_alpha'] >= .25 for row in visible)
    clear = [row for row in values if row['visible_start_ms'] >= 0
             and abs(row['estimated_alpha']) < .05]
    gaps = [float(b['start_ms']) - float(a['start_ms']) for a, b in zip(values, values[1:])]
    report['max_capture_gap_ms'] = max(gaps) if gaps else None
    if clear:
        report['visible_clear_ms'] = clear[0]['visible_start_ms']
        report['visible_clear_product_ms'] = float(clear[0]['start_ms'])
    good = (strong and len(visible) >= 2 and clear and gaps and min(gaps) >= 0
            and max(gaps) <= 50 and 0 < report['visible_clear_ms'] <= 250
            and report['visible_clear_product_ms'] <= 250)
    report['visible_result'] = 'OBSERVED' if good else 'FAIL'
    report['reason'] = ('Visible frames observed; original 200ms/250ms gates remain authoritative'
                        if good else 'Visible-pixel duration or sampling requirements not met')
    return report, values


def main():
    directory = Path(sys.argv[1])
    with (directory / 'flash-x11-times.csv').open() as stream:
        report, values = analyze(list(csv.DictReader(stream)))
    (directory / 'flash-visible-timing.json').write_text(json.dumps(report, indent=2) + '\n')
    if values:
        with (directory / 'flash-visible-times.csv').open('w') as stream:
            writer = csv.DictWriter(stream, fieldnames=list(values[0]))
            writer.writeheader()
            writer.writerows(values)
    print(json.dumps(report), flush=True)
    return 0 if report['visible_result'] == 'OBSERVED' else 1


if __name__ == '__main__':
    raise SystemExit(main())
